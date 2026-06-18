package com.example.paddleocrapp.ocr

import android.graphics.Bitmap
import android.util.Log

/**
 * DB（Differentiable Binarization）文本检测器
 *
 * 实现 PaddleOCR 的 DB 文本检测算法，包括：
 * 1. 预处理：图像缩放（长边限制 + 32倍数对齐）、归一化、NHWC->NCHW 转换
 * 2. 推理：调用 Paddle Lite 预测器执行 DB 模型推理
 * 3. 后处理：二值化、轮廓查找、最小外接矩形、BoxScoreFast、Unclip（Vatti clipping）、
 *    坐标映射回原图、过滤
 *
 * DB 算法核心思想：
 * - 通过可微分二值化操作学习文本区域的边界
 * - 使用 Vatti clipping 算法扩展文本框以获得更完整的文本区域
 */
class Detector(
    private val predictor: PaddleLitePredictor,
    private val maxSideLen: Int = 960,
    private val detDbThresh: Float = 0.3f,
    private val detDbBoxThresh: Float = 0.5f,
    private val detDbUnclipRatio: Float = 1.6f,
    private val detDbUseDilate: Boolean = false
) {
    companion object {
        private const val TAG = "Detector"

        /** 检测模型归一化均值（ImageNet 均值） */
        private val DET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)

        /** 检测模型归一化标准差（ImageNet 标准差） */
        private val DET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        /** 尺寸对齐因子，宽高需要对齐到此值的倍数 */
        private const val LIMIT_MAX_SIDE_LEN = 960
        private const val LIMIT_MIN_SIDE_LEN = 32
    }

    /**
     * 执行文本检测
     *
     * @param bitmap 输入图像
     * @return 检测到的文本框列表，按从左到右、从上到下排序
     */
    fun detect(bitmap: Bitmap): List<TextBox> {
        // 第一步：预处理图像
        val preprocessResult = preprocessBitmap(bitmap)
        if (preprocessResult == null) {
            Log.e(TAG, "预处理失败")
            return emptyList()
        }

        val (inputData, shape, ratioW, ratioH, srcWidth, srcHeight) = preprocessResult

        // 第二步：执行模型推理
        val outputData = predictor.run(inputData, shape)
        if (outputData.isEmpty()) {
            Log.e(TAG, "推理结果为空")
            return emptyList()
        }

        // 第三步：后处理 - 从热力图提取文本框
        val boxes = postprocess(
            outputData, srcWidth, srcHeight, ratioW, ratioH
        )

        Log.d(TAG, "检测到 ${boxes.size} 个文本框")
        return boxes
    }

    /**
     * 图像预处理
     *
     * 处理步骤：
     * 1. DetResizeImg：长边限制 maxSideLen，宽高对齐到 32 倍数
     * 2. 归一化：(pixel - mean) / std
     * 3. NHWC -> NCHW 格式转换
     *
     * @param bitmap 原始图像
     * @return 预处理结果元组：(输入数据, 形状, 宽度缩放比, 高度缩放比, 原始宽度, 原始高度)
     */
    private fun preprocessBitmap(bitmap: Bitmap): PreprocessResult? {
        return try {
            val srcWidth = bitmap.width
            val srcHeight = bitmap.height

            // DetResizeImg：计算缩放后的尺寸
            val (dstWidth, dstHeight, ratioW, ratioH) = detResizeImg(srcWidth, srcHeight)

            // 缩放图像
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, dstWidth, dstHeight, true)

            // 提取像素数据并转换为 float 数组
            val pixels = IntArray(dstWidth * dstHeight)
            resizedBitmap.getPixels(pixels, 0, dstWidth, 0, 0, dstWidth, dstHeight)

            // NHWC 格式：[height, width, channel]
            val channelSize = dstWidth * dstHeight
            val inputData = FloatArray(3 * channelSize)

            for (i in pixels.indices) {
                val pixel = pixels[i]
                // 提取 RGB 通道值并归一化到 [0, 1]
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f

                // 归一化：(pixel - mean) / std
                // NCHW 格式：channel 0 (R) 在前，channel 1 (G) 居中，channel 2 (B) 在后
                inputData[i] = (r - DET_MEAN[0]) / DET_STD[0]
                inputData[channelSize + i] = (g - DET_MEAN[1]) / DET_STD[1]
                inputData[2 * channelSize + i] = (b - DET_MEAN[2]) / DET_STD[2]
            }

            // 回收缩放后的 bitmap
            if (resizedBitmap !== bitmap) {
                resizedBitmap.recycle()
            }

            // 输入形状：[batch, channel, height, width]
            val shape = longArrayOf(1, 3, dstHeight.toLong(), dstWidth.toLong())

            PreprocessResult(inputData, shape, ratioW, ratioH, srcWidth, srcHeight)
        } catch (e: Exception) {
            Log.e(TAG, "预处理异常", e)
            null
        }
    }

    /**
     * DetResizeImg：计算检测模型的输入尺寸
     *
     * 规则：
     * 1. 长边限制到 maxSideLen
     * 2. 宽高对齐到 32 的倍数（下取整）
     * 3. 确保最小边不小于 32
     *
     * @param srcWidth 原始宽度
     * @param srcHeight 原始高度
     * @return (目标宽度, 目标高度, 宽度缩放比, 高度缩放比)
     */
    private fun detResizeImg(srcWidth: Int, srcHeight: Int): ResizeResult {
        var ratio = 1.0f
        val maxSide = maxOf(srcWidth, srcHeight).toFloat()

        if (maxSide > maxSideLen) {
            ratio = maxSideLen / maxSide
        }

        // 计算缩放后的尺寸
        var resizeW = (srcWidth * ratio).toInt().coerceAtLeast(LIMIT_MIN_SIDE_LEN)
        var resizeH = (srcHeight * ratio).toInt().coerceAtLeast(LIMIT_MIN_SIDE_LEN)

        // 对齐到 32 的倍数（下取整）
        resizeW = (resizeW / LIMIT_MIN_SIDE_LEN) * LIMIT_MIN_SIDE_LEN
        resizeH = (resizeH / LIMIT_MIN_SIDE_LEN) * LIMIT_MIN_SIDE_LEN

        // 确保最小尺寸
        resizeW = resizeW.coerceAtLeast(LIMIT_MIN_SIDE_LEN)
        resizeH = resizeH.coerceAtLeast(LIMIT_MIN_SIDE_LEN)

        val ratioW = resizeW.toFloat() / srcWidth
        val ratioH = resizeH.toFloat() / srcHeight

        return ResizeResult(resizeW, resizeH, ratioW, ratioH)
    }

    /**
     * 后处理：从模型输出的概率图中提取文本框
     *
     * 处理步骤：
     * 1. 将输出 reshape 为二维概率图
     * 2. 二值化（阈值 detDbThresh）
     * 3. 可选膨胀操作
     * 4. 查找连通区域轮廓
     * 5. 对每个轮廓计算最小外接矩形
     * 6. BoxScoreFast：计算框内区域在概率图上的平均得分
     * 7. 过滤低分框
     * 8. Unclip：使用 Vatti clipping 算法扩展文本框
     * 9. 坐标映射回原图尺寸
     * 10. 过滤太小的框
     *
     * @param outputData 模型输出数据
     * @param srcWidth 原始图像宽度
     * @param srcHeight 原始图像高度
     * @param ratioW 宽度缩放比
     * @param ratioH 高度缩放比
     * @return 过滤后的文本框列表
     */
    private fun postprocess(
        outputData: FloatArray,
        srcWidth: Int,
        srcHeight: Int,
        ratioW: Float,
        ratioH: Float
    ): List<TextBox> {
        // 获取输出形状 [1, 1, height, width]
        val outputShape = predictor.getOutputShape()
        val outHeight = if (outputShape.size >= 3) outputShape[2].toInt() else 0
        val outWidth = if (outputShape.size >= 4) outputShape[3].toInt() else 0

        if (outHeight == 0 || outWidth == 0) {
            Log.e(TAG, "输出形状异常: ${outputShape.toList()}")
            return emptyList()
        }

        // 构建概率图（二维数组）
        val probMap = Array(outHeight) { FloatArray(outWidth) }
        for (h in 0 until outHeight) {
            for (w in 0 until outWidth) {
                probMap[h][w] = outputData[h * outWidth + w]
            }
        }

        // 二值化
        val binaryMap = Array(outHeight) { BooleanArray(outWidth) }
        for (h in 0 until outHeight) {
            for (w in 0 until outWidth) {
                binaryMap[h][w] = probMap[h][w] >= detDbThresh
            }
        }

        // 可选膨胀操作（使用 3x3 核）
        val dilatedMap = if (detDbUseDilate) {
            dilate(binaryMap, outHeight, outWidth)
        } else {
            binaryMap
        }

        // 查找连通区域轮廓
        val contours = findContours(dilatedMap, outHeight, outWidth)

        // 对每个轮廓进行处理
        val boxes = mutableListOf<TextBox>()
        for (contour in contours) {
            if (contour.size < 4) continue

            // 计算最小外接矩形（使用最小面积外接矩形）
            val minBox = minAreaRect(contour)
            if (minBox.size < 8) continue

            // BoxScoreFast：计算框内区域在概率图上的平均得分
            val boxScore = boxScoreFast(minBox, probMap, outHeight, outWidth)
            if (boxScore < detDbBoxThresh) continue

            // Unclip：扩展文本框
            val unclippedBox = unclip(minBox, detDbUnclipRatio)
            if (unclippedBox.size < 8) continue

            // 坐标映射回原图尺寸
            val mappedBox = mapToOriginal(unclippedBox, ratioW, ratioH, srcWidth, srcHeight)

            // 过滤太小的框
            if (isValidBox(mappedBox)) {
                boxes.add(TextBox(mappedBox, boxScore))
            }
        }

        // 按从左到右、从上到下排序
        return boxes.sortedWith(compareBy({ it.centerY() }, { it.centerX() }))
    }

    /**
     * 3x3 膨胀操作
     *
     * 对二值图进行膨胀，连接相邻的断裂区域
     */
    private fun dilate(
        binaryMap: Array<BooleanArray>,
        height: Int,
        width: Int
    ): Array<BooleanArray> {
        val result = Array(height) { BooleanArray(width) }
        for (h in 0 until height) {
            for (w in 0 until width) {
                // 检查 3x3 邻域内是否有 true
                var found = false
                for (dh in -1..1) {
                    for (dw in -1..1) {
                        val nh = h + dh
                        val nw = w + dw
                        if (nh in 0 until height && nw in 0 until width && binaryMap[nh][nw]) {
                            found = true
                            break
                        }
                    }
                    if (found) break
                }
                result[h][w] = found
            }
        }
        return result
    }

    /**
     * 查找二值图中的连通区域轮廓
     *
     * 使用 BFS（广度优先搜索）算法查找所有连通的白色像素区域
     *
     * @param binaryMap 二值图
     * @param height 图像高度
     * @param width 图像宽度
     * @return 轮廓列表，每个轮廓是一组 (x, y) 坐标对
     */
    private fun findContours(
        binaryMap: Array<BooleanArray>,
        height: Int,
        width: Int
    ): List<List<Pair<Int, Int>>> {
        val visited = Array(height) { BooleanArray(width) }
        val contours = mutableListOf<List<Pair<Int, Int>>>()

        for (h in 0 until height) {
            for (w in 0 until width) {
                if (binaryMap[h][w] && !visited[h][w]) {
                    // BFS 查找连通区域
                    val contour = mutableListOf<Pair<Int, Int>>>()
                    val queue = ArrayDeque<Pair<Int, Int>>()
                    queue.add(Pair(w, h))
                    visited[h][w] = true

                    while (queue.isNotEmpty()) {
                        val (cx, cy) = queue.removeFirst()
                        contour.add(Pair(cx, cy))

                        // 四邻域搜索
                        val neighbors = listOf(
                            Pair(cx - 1, cy), Pair(cx + 1, cy),
                            Pair(cx, cy - 1), Pair(cx, cy + 1)
                        )
                        for ((nx, ny) in neighbors) {
                            if (nx in 0 until width && ny in 0 until height
                                && binaryMap[ny][nx] && !visited[ny][nx]
                            ) {
                                visited[ny][nx] = true
                                queue.add(Pair(nx, ny))
                            }
                        }
                    }

                    if (contour.size >= 4) {
                        contours.add(contour)
                    }
                }
            }
        }

        return contours
    }

    /**
     * 计算最小面积外接矩形
     *
     * 使用旋转卡壳算法的思想，尝试不同角度的旋转矩形，
     * 返回面积最小的旋转矩形的四个顶点坐标。
     *
     * @param contour 轮廓点集
     * @return 包含 8 个浮点数的数组 [x1,y1,x2,y2,x3,y3,x4,y4]，表示四个顶点
     */
    private fun minAreaRect(contour: List<Pair<Int, Int>>): FloatArray {
        if (contour.size < 4) return floatArrayOf()

        // 计算凸包
        val hull = convexHull(contour)

        // 如果凸包点数不足，返回空
        if (hull.size < 3) return floatArrayOf()

        // 尝试多个旋转角度，找到面积最小的外接矩形
        var bestArea = Float.MAX_VALUE
        var bestBox = FloatArray(8)

        for (i in hull.indices) {
            val p1 = hull[i]
            val p2 = hull[(i + 1) % hull.size]

            // 计算当前边的方向角
            val dx = p2.first - p1.first
            val dy = p2.second - p1.second
            val angle = Math.atan2(dy.toDouble(), dx.toDouble())

            // 旋转所有点到该角度
            val rotated = hull.map { (x, y) ->
                val rx = (x * Math.cos(-angle) - y * Math.sin(-angle)).toFloat()
                val ry = (x * Math.sin(-angle) + y * Math.cos(-angle)).toFloat()
                Pair(rx, ry)
            }

            // 计算轴对齐包围盒
            var minX = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var minY = Float.MAX_VALUE
            var maxY = Float.MIN_VALUE
            for ((rx, ry) in rotated) {
                minX = minOf(minX, rx)
                maxX = maxOf(maxX, rx)
                minY = minOf(minY, ry)
                maxY = maxOf(maxY, ry)
            }

            val area = (maxX - minX) * (maxY - minY)
            if (area < bestArea) {
                bestArea = area
                // 四个角点（旋转坐标系中）
                val corners = listOf(
                    Pair(minX, minY), Pair(maxX, minY),
                    Pair(maxX, maxY), Pair(minX, maxY)
                )
                // 旋转回原始坐标系
                val cosA = Math.cos(angle).toFloat()
                val sinA = Math.sin(angle).toFloat()
                for (j in corners.indices) {
                    val (cx, cy) = corners[j]
                    bestBox[j * 2] = cx * cosA - cy * sinA
                    bestBox[j * 2 + 1] = cx * sinA + cy * cosA
                }
            }
        }

        return bestBox
    }

    /**
     * 计算凸包（Graham Scan 算法）
     *
     * @param points 输入点集
     * @return 凸包顶点列表（逆时针方向）
     */
    private fun convexHull(points: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        if (points.size <= 3) return points

        // 找到最低点（y最大，y相同取x最小）
        val sorted = points.sortedWith(
            compareByDescending<Pair<Int, Int>> { it.second }
                .thenBy { it.first }
        )

        val start = sorted.first()
        val rest = sorted.drop(1).sortedWith { a, b ->
            val angleA = Math.atan2((a.second - start.second).toDouble(), (a.first - start.first).toDouble())
            val angleB = Math.atan2((b.second - start.second).toDouble(), (b.first - start.first).toDouble())
            angleA.compareTo(angleB)
        }

        val hull = mutableListOf<Pair<Int, Int>>()
        hull.add(start)
        hull.add(rest[0])

        for (i in 1 until rest.size) {
            while (hull.size > 1) {
                val a = hull[hull.size - 2]
                val b = hull[hull.size - 1]
                val c = rest[i]
                val cross = (b.first - a.first) * (c.second - a.second).toLong() -
                        (b.second - a.second).toLong() * (c.first - a.first)
                if (cross <= 0) {
                    hull.removeAt(hull.size - 1)
                } else {
                    break
                }
            }
            hull.add(rest[i])
        }

        return hull
    }

    /**
     * BoxScoreFast：计算文本框在概率图上的平均得分
     *
     * 将旋转矩形内的概率值求平均，得分越高表示该区域越可能是文本。
     *
     * @param box 文本框顶点坐标 [x1,y1,x2,y2,x3,y3,x4,y4]
     * @param probMap 概率图
     * @param height 概率图高度
     * @param width 概率图宽度
     * @return 平均得分，范围 [0, 1]
     */
    private fun boxScoreFast(
        box: FloatArray,
        probMap: Array<FloatArray>,
        height: Int,
        width: Int
    ): Float {
        // 计算轴对齐包围盒
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        for (i in 0 until box.size step 2) {
            minX = minOf(minX, box[i])
            maxX = maxOf(maxX, box[i])
            minY = minOf(minY, box[i + 1])
            maxY = maxOf(maxY, box[i + 1])
        }

        // 将坐标限制在图像范围内
        val x0 = minX.toInt().coerceIn(0, width - 1)
        val x1 = maxX.toInt().coerceIn(0, width - 1)
        val y0 = minY.toInt().coerceIn(0, height - 1)
        val y1 = maxY.toInt().coerceIn(0, height - 1)

        if (x0 >= x1 || y0 >= y1) return 0f

        // 使用点在多边形内判断来精确计算得分
        var totalScore = 0.0
        var count = 0

        // 采样计算（每隔2个像素采样一次以提高性能）
        val step = 2
        for (y in y0..y1 step step) {
            for (x in x0..x1 step step) {
                if (pointInPolygon(x.toFloat(), y.toFloat(), box)) {
                    totalScore += probMap[y][x]
                    count++
                }
            }
        }

        return if (count > 0) (totalScore / count).toFloat() else 0f
    }

    /**
     * 判断点是否在四边形内
     *
     * 使用射线法（Ray Casting）判断点是否在四边形内部
     *
     * @param px 点的 x 坐标
     * @param py 点的 y 坐标
     * @param box 四边形顶点 [x1,y1,x2,y2,x3,y3,x4,y4]
     * @return 点在四边形内返回 true
     */
    private fun pointInPolygon(px: Float, py: Float, box: FloatArray): Boolean {
        var inside = false
        val n = 4
        var j = n - 1

        for (i in 0 until n) {
            val xi = box[i * 2]
            val yi = box[i * 2 + 1]
            val xj = box[j * 2]
            val yj = box[j * 2 + 1]

            if (((yi > py) != (yj > py)) &&
                (px < (xj - xi) * (py - yi) / (yj - yi) + xi)
            ) {
                inside = !inside
            }
            j = i
        }

        return inside
    }

    /**
     * Unclip：使用 Vatti clipping 算法扩展文本框
     *
     * Vatti clipping 算法原理：
     * - 计算扩展距离 distance = area * unclip_ratio / perimeter
     * - 沿法线方向向外扩展文本框
     *
     * @param box 原始文本框顶点 [x1,y1,x2,y2,x3,y3,x4,y4]
     * @param unclipRatio 扩展比例系数
     * @return 扩展后的文本框顶点
     */
    private fun unclip(box: FloatArray, unclipRatio: Float): FloatArray {
        // 计算多边形面积（Shoelace 公式）
        val area = polygonArea(box)

        // 计算周长
        val perimeter = polygonPerimeter(box)

        if (perimeter == 0f) return box

        // Vatti clipping 扩展距离
        val distance = area * unclipRatio / perimeter

        // 计算每条边的外法线方向，然后沿法线方向扩展
        return expandPolygon(box, distance)
    }

    /**
     * 计算多边形面积（Shoelace 公式）
     */
    private fun polygonArea(box: FloatArray): Float {
        var area = 0f
        val n = box.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += box[i * 2] * box[j * 2 + 1]
            area -= box[j * 2] * box[i * 2 + 1]
        }
        return Math.abs(area) / 2f
    }

    /**
     * 计算多边形周长
     */
    private fun polygonPerimeter(box: FloatArray): Float {
        var perimeter = 0f
        val n = box.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            val dx = box[j * 2] - box[i * 2]
            val dy = box[j * 2 + 1] - box[i * 2 + 1]
            perimeter += Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        }
        return perimeter
    }

    /**
     * 沿外法线方向扩展多边形
     *
     * 对每条边计算外法线方向，然后将每个顶点沿相邻两条边的法线方向各移动 distance 的距离
     *
     * @param box 原始多边形顶点
     * @param distance 扩展距离
     * @return 扩展后的多边形顶点
     */
    private fun expandPolygon(box: FloatArray, distance: Float): FloatArray {
        val n = box.size / 2
        val expandedBox = FloatArray(box.size)

        for (i in 0 until n) {
            val prev = (i - 1 + n) % n
            val next = (i + 1) % n

            // 当前顶点
            val cx = box[i * 2]
            val cy = box[i * 2 + 1]

            // 前一条边的方向
            val prevDx = cx - box[prev * 2]
            val prevDy = cy - box[prev * 2 + 1]
            val prevLen = Math.sqrt((prevDx * prevDx + prevDy * prevDy).toDouble()).toFloat()

            // 后一条边的方向
            val nextDx = box[next * 2] - cx
            val nextDy = box[next * 2 + 1] - cy
            val nextLen = Math.sqrt((nextDx * nextDx + nextDy * nextDy).toDouble()).toFloat()

            if (prevLen == 0f || nextLen == 0f) {
                expandedBox[i * 2] = cx
                expandedBox[i * 2 + 1] = cy
                continue
            }

            // 前一条边的外法线（逆时针多边形的外法线为右侧法线）
            val prevNx = prevDy / prevLen
            val prevNy = -prevDx / prevLen

            // 后一条边的外法线
            val nextNx = nextDy / nextLen
            val nextNy = -nextDx / nextLen

            // 沿两条法线方向的平均方向扩展
            val nx = (prevNx + nextNx) / 2f
            val ny = (prevNy + nextNy) / 2f
            val nLen = Math.sqrt((nx * nx + ny * ny).toDouble()).toFloat()

            if (nLen > 0f) {
                expandedBox[i * 2] = cx + distance * nx / nLen
                expandedBox[i * 2 + 1] = cy + distance * ny / nLen
            } else {
                expandedBox[i * 2] = cx
                expandedBox[i * 2 + 1] = cy
            }
        }

        return expandedBox
    }

    /**
     * 将坐标从模型输出空间映射回原图空间
     *
     * @param box 模型输出空间的文本框坐标
     * @param ratioW 宽度缩放比（模型宽度 / 原图宽度）
     * @param ratioH 高度缩放比（模型高度 / 原图高度）
     * @param srcWidth 原图宽度
     * @param srcHeight 原图高度
     * @return 映射到原图坐标系的文本框
     */
    private fun mapToOriginal(
        box: FloatArray,
        ratioW: Float,
        ratioH: Float,
        srcWidth: Int,
        srcHeight: Int
    ): FloatArray {
        val mappedBox = FloatArray(box.size)
        for (i in 0 until box.size / 2) {
            mappedBox[i * 2] = (box[i * 2] / ratioW).coerceIn(0f, srcWidth.toFloat())
            mappedBox[i * 2 + 1] = (box[i * 2 + 1] / ratioH).coerceIn(0f, srcHeight.toFloat())
        }
        return mappedBox
    }

    /**
     * 验证文本框是否有效
     *
     * 过滤条件：
     * - 宽度和高度都不能太小（至少 3 个像素）
     * - 面积不能太小（至少 9 平方像素）
     *
     * @param box 文本框坐标
     * @return 有效返回 true
     */
    private fun isValidBox(box: FloatArray): Boolean {
        // 计算宽高
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        for (i in 0 until box.size step 2) {
            minX = minOf(minX, box[i])
            maxX = maxOf(maxX, box[i])
            minY = minOf(minY, box[i + 1])
            maxY = maxOf(maxY, box[i + 1])
        }

        val w = maxX - minX
        val h = maxY - minY

        // 最小尺寸过滤
        return w >= 3f && h >= 3f && w * h >= 9f
    }

    /**
     * 预处理结果数据类
     */
    private data class PreprocessResult(
        val inputData: FloatArray,
        val shape: LongArray,
        val ratioW: Float,
        val ratioH: Float,
        val srcWidth: Int,
        val srcHeight: Int
    )

    /**
     * 缩放结果数据类
     */
    private data class ResizeResult(
        val width: Int,
        val height: Int,
        val ratioW: Float,
        val ratioH: Float
    )
}
