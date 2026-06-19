package com.example.paddleocrapp.ocr

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * OCR 引擎 - 完整的 PaddleOCR 流水线
 *
 * 实现完整的 OCR 识别流程：
 * 1. 文本检测（DB 算法）：检测图像中的文本区域
 * 2. 方向分类：判断文本是否需要旋转 180 度
 * 3. 文本识别（CRNN + CTC）：识别每个文本区域中的文字内容
 *
 * 使用方式：
 * 1. 创建 OCREngine 实例，传入 OCRConfig 配置
 * 2. 调用 initialize() 加载模型和字典
 * 3. 调用 recognize() 对图像执行 OCR 识别
 * 4. 使用完毕后调用 release() 释放资源
 */
class OCREngine(private val config: OCRConfig) : OCRManager.OCREngineInterface {
    companion object {
        private const val TAG = "OCREngine"
    }

    /** 检测模型预测器 */
    private val detPredictor = PaddleLitePredictor()

    /** 识别模型预测器 */
    private val recPredictor = PaddleLitePredictor()

    /** 方向分类模型预测器 */
    private val clsPredictor = PaddleLitePredictor()

    /** 字典标签列表（索引 -> 字符） */
    private var labels: List<String> = emptyList()

    /** 引擎是否已初始化 */
    private var isInitialized = false

    /** DB 文本检测器 */
    private var detector: Detector? = null

    /** 方向分类器 */
    private var classifier: Classifier? = null

    /** CRNN 文本识别器 */
    private var recognizer: Recognizer? = null

    /**
     * 初始化 OCR 引擎
     *
     * 加载检测模型、识别模型、方向分类模型和字典文件。
     * 所有模型文件路径通过 OCRConfig 传入。
     *
     * @return 初始化成功返回 true，失败返回 false
     */
    override fun initialize(): Boolean {
        Log.i(TAG, "开始初始化 OCR 引擎...")

        // 第一步：加载字典文件
        labels = loadLabels(config.labelPath)
        if (labels.isEmpty()) {
            Log.e(TAG, "字典加载失败: ${config.labelPath}")
            return false
        }
        Log.i(TAG, "字典加载成功，共 ${labels.size} 个字符")

        // 第二步：加载检测模型
        if (!detPredictor.loadModel(config.detModelPath, config.cpuThreadNum)) {
            Log.e(TAG, "检测模型加载失败: ${config.detModelPath}")
            return false
        }
        Log.i(TAG, "检测模型加载成功")

        // 第三步：加载识别模型
        if (!recPredictor.loadModel(config.recModelPath, config.cpuThreadNum)) {
            Log.e(TAG, "识别模型加载失败: ${config.recModelPath}")
            return false
        }
        Log.i(TAG, "识别模型加载成功")

        // 第四步：加载方向分类模型（可选）
        if (config.useDirectionClassify) {
            if (!clsPredictor.loadModel(config.clsModelPath, config.cpuThreadNum)) {
                Log.e(TAG, "方向分类模型加载失败: ${config.clsModelPath}")
                return false
            }
            Log.i(TAG, "方向分类模型加载成功")
        }

        // 第五步：创建检测器、分类器和识别器实例
        detector = Detector(
            predictor = detPredictor,
            maxSideLen = config.maxSideLen,
            detDbThresh = config.detDbThresh,
            detDbBoxThresh = config.detDbBoxThresh,
            detDbUnclipRatio = config.detDbUnclipRatio,
            detDbUseDilate = config.detDbUseDilate
        )

        classifier = if (config.useDirectionClassify) {
            Classifier(
                predictor = clsPredictor,
                clsThresh = config.clsThresh
            )
        } else {
            null
        }

        recognizer = Recognizer(
            predictor = recPredictor,
            labels = labels,
            recImageHeight = config.recImageHeight,
            recImageWidth = config.recImageWidth
        )

        isInitialized = true
        Log.i(TAG, "OCR 引擎初始化完成")
        return true
    }

    /**
     * 对图像执行完整的 OCR 识别
     *
     * 完整流程：
     * 1. 检测文本框（Det）
     * 2. 对每个文本框裁剪图像区域
     * 3. 方向分类（Cls），判断是否需要旋转
     * 4. 文本识别（Rec），识别文字内容
     * 5. 结果合并，按从下到上顺序排列文本
     *
     * @param bitmap 输入图像
     * @return OCR 识别结果，包含文本内容、置信度和文本框位置
     */
    fun recognize(bitmap: Bitmap): OCRResult {
        if (!isInitialized) {
            return OCRResult.error("OCR 引擎未初始化")
        }

        if (bitmap.width <= 0 || bitmap.height <= 0) {
            return OCRResult.error("无效的图像尺寸: ${bitmap.width}x${bitmap.height}")
        }

        Log.d(TAG, "开始识别图像: ${bitmap.width}x${bitmap.height}")

        // Stage 1: 检测文本框
        val boxes = detect(bitmap)
        if (boxes.isEmpty()) {
            Log.d(TAG, "未检测到文本框")
            return OCRResult.success("", 0f, emptyList())
        }

        Log.d(TAG, "检测到 ${boxes.size} 个文本框，开始识别...")

        // Stage 2: 对每个文本框进行分类 + 识别
        val textResults = mutableListOf<Pair<String, Float>>()

        // 按从下到上的顺序遍历文本框（y 坐标从大到小）
        val sortedBoxes = boxes.sortedByDescending { box ->
            (box.points[1] + box.points[3] + box.points[5] + box.points[7]) / 4f
        }

        for ((index, box) in sortedBoxes.withIndex()) {
            Log.d(TAG, "处理文本框 ${index + 1}/${sortedBoxes.size}")

            // 裁剪文本区域（使用透视变换）
            var cropBitmap = cropFromBitmap(bitmap, box)
            if (cropBitmap == null) {
                Log.w(TAG, "裁剪文本框失败，跳过")
                continue
            }

            // 方向分类（可选）
            if (classifier != null) {
                cropBitmap = classifier!!.classify(cropBitmap)
            }

            // 文本识别
            val (text, score) = recognizeText(cropBitmap)
            if (text.isNotEmpty()) {
                textResults.add(Pair(text, score))
                Log.d(TAG, "识别结果: \"$text\" (得分: ${"%.4f".format(score)})")
            }

            // 释放裁剪的 bitmap
            if (cropBitmap !== bitmap) {
                cropBitmap.recycle()
            }
        }

        // 合并结果
        val fullText = textResults.joinToString("\n") { it.first }
        val avgScore = if (textResults.isNotEmpty()) {
            textResults.map { it.second }.average().toFloat()
        } else {
            0f
        }

        Log.d(TAG, "识别完成，共 ${textResults.size} 行文本，平均得分: ${"%.4f".format(avgScore)}")
        return OCRResult.success(fullText, avgScore, boxes)
    }

    /**
     * 执行文本检测
     *
     * @param bitmap 输入图像
     * @return 检测到的文本框列表
     */
    private fun detect(bitmap: Bitmap): List<TextBox> {
        return try {
            detector?.detect(bitmap) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "文本检测异常", e)
            emptyList()
        }
    }

    /**
     * 执行文本识别
     *
     * @param bitmap 裁剪出的文本区域图像
     * @return 识别结果对：(文本, 得分)
     */
    private fun recognizeText(bitmap: Bitmap): Pair<String, Float> {
        return try {
            recognizer?.recognize(bitmap) ?: Pair("", 0f)
        } catch (e: Exception) {
            Log.e(TAG, "文本识别异常", e)
            Pair("", 0f)
        }
    }

    /**
     * 从原图中裁剪文本区域（使用透视变换）
     *
     * 根据文本框的四个顶点坐标，使用透视变换将倾斜的文本区域
     * 矫正为正矩形，便于后续的识别处理。
     *
     * 裁剪步骤：
     * 1. 计算文本框的轴对齐包围盒
     * 2. 如果文本框倾斜角度较大（>5度），使用透视变换矫正
     * 3. 否则直接裁剪矩形区域
     *
     * @param bitmap 原始图像
     * @param box 文本框（四个顶点坐标）
     * @return 裁剪并矫正后的文本区域图像，失败返回 null
     */
    private fun cropFromBitmap(bitmap: Bitmap, box: TextBox): Bitmap? {
        return try {
            val points = box.points

            // 计算轴对齐包围盒
            var minX = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var minY = Float.MAX_VALUE
            var maxY = Float.MIN_VALUE

            for (i in 0 until points.size step 2) {
                minX = minOf(minX, points[i])
                maxX = maxOf(maxX, points[i])
                minY = minOf(minY, points[i + 1])
                maxY = maxOf(maxY, points[i + 1])
            }

            // 确保坐标在图像范围内
            val x0 = minX.toInt().coerceIn(0, bitmap.width - 1)
            val y0 = minY.toInt().coerceIn(0, bitmap.height - 1)
            val x1 = maxX.toInt().coerceIn(x0 + 1, bitmap.width)
            val y1 = maxY.toInt().coerceIn(y0 + 1, bitmap.height)

            // 计算文本框的宽高
            val boxWidth = x1 - x0
            val boxHeight = y1 - y0

            if (boxWidth < 2 || boxHeight < 2) {
                return null
            }

            // 计算倾斜角度（使用上边方向）
            val topDx = points[2] - points[0]  // 右上x - 左上x
            val topDy = points[3] - points[1]  // 右上y - 左上y
            val angle = Math.toDegrees(Math.atan2(topDy.toDouble(), topDx.toDouble())).toFloat()

            // 如果倾斜角度较大，使用透视变换矫正
            return if (Math.abs(angle) > 5f) {
                cropWithPerspective(bitmap, points, x0, y0, x1, y1, boxWidth, boxHeight)
            } else {
                // 直接裁剪矩形区域
                Bitmap.createBitmap(bitmap, x0, y0, boxWidth, boxHeight)
            }
        } catch (e: Exception) {
            Log.e(TAG, "裁剪文本区域异常", e)
            null
        }
    }

    /**
     * 使用透视变换裁剪文本区域
     *
     * 将倾斜的四边形文本区域通过透视变换矫正为正矩形。
     * 步骤：
     * 1. 确定源四边形的四个顶点
     * 2. 确定目标矩形的四个顶点
     * 3. 使用 Matrix.perspectiveTransform 计算透视变换矩阵
     * 4. 应用变换裁剪图像
     *
     * @param bitmap 原始图像
     * @param points 文本框四个顶点坐标 [x1,y1,x2,y2,x3,y3,x4,y4]
     * @param x0 包围盒左上角 x
     * @param y0 包围盒左上角 y
     * @param x1 包围盒右下角 x
     * @param y1 包围盒右下角 y
     * @param boxWidth 包围盒宽度
     * @param boxHeight 包围盒高度
     * @return 透视变换矫正后的图像
     */
    private fun cropWithPerspective(
        bitmap: Bitmap,
        points: FloatArray,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        boxWidth: Int,
        boxHeight: Int
    ): Bitmap {
        // 源四边形顶点（文本框的四个顶点）
        val src = floatArrayOf(
            points[0], points[1],  // 左上
            points[2], points[3],  // 右上
            points[4], points[5],  // 右下
            points[6], points[7]   // 左下
        )

        // 目标矩形顶点
        val dst = floatArrayOf(
            0f, 0f,                     // 左上
            boxWidth.toFloat(), 0f,     // 右上
            boxWidth.toFloat(), boxHeight.toFloat(),  // 右下
            0f, boxHeight.toFloat()      // 左下
        )

        // 计算透视变换矩阵
        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)

        // 应用透视变换裁剪图像
        return Bitmap.createBitmap(bitmap, x0, y0, boxWidth, boxHeight, matrix, true)
    }

    /**
     * 加载字典文件
     *
     * 字典文件格式：每行一个字符，第一行为空白字符（blank）。
     * 字典的索引对应 CTC 解码时的字符类别。
     *
     * @param labelPath 字典文件路径
     * @return 字符列表，加载失败返回空列表
     */
    private fun loadLabels(labelPath: String): List<String> {
        return try {
            val file = File(labelPath)
            if (!file.exists()) {
                Log.e(TAG, "字典文件不存在: $labelPath")
                return emptyList()
            }

            val labels = mutableListOf<String>()
            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    // 去除首尾空白字符，但保留中间的空格
                    labels.add(line!!.trim())
                }
            }

            Log.d(TAG, "字典加载完成: ${labels.size} 个字符")
            labels
        } catch (e: Exception) {
            Log.e(TAG, "加载字典失败: $labelPath", e)
            emptyList()
        }
    }

    /**
     * 释放所有资源
     *
     * 释放检测模型、识别模型和分类模型的预测器资源
     */
    override fun release() {
        Log.i(TAG, "释放 OCR 引擎资源")

        detector = null
        classifier = null
        recognizer = null

        detPredictor.release()
        recPredictor.release()
        clsPredictor.release()

        labels = emptyList()
        isInitialized = false

        Log.i(TAG, "OCR 引擎资源已释放")
    }

    /**
     * 判断引擎是否已初始化并可用
     *
     * @return 已初始化返回 true
     */
    fun isReady(): Boolean = isInitialized
}
