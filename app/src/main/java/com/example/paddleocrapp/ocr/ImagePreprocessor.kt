package com.example.paddleocrapp.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log

/**
 * 图像预处理工具类
 *
 * 在 OCR 识别前对图像进行增强处理，提升识别准确率。
 * 支持：对比度增强、自适应二值化、锐化、深色模式反转。
 */
object ImagePreprocessor {

    private const val TAG = "ImagePreprocessor"

    /**
     * 自动检测并应用最佳预处理
     *
     * 根据图像特征自动选择预处理策略：
     * - 深色背景（深色模式截屏）：反色处理
     * - 低对比度：对比度增强
     * - 正常图像：轻度锐化
     *
     * @param bitmap 原始图像
     * @return 预处理后的图像
     */
    fun autoPreprocess(bitmap: Bitmap): Bitmap {
        val stats = analyzeImage(bitmap)

        return when {
            // 深色背景（平均亮度低于 80）
            stats.avgBrightness < 80f -> {
                Log.d(TAG, "检测到深色背景，执行反色处理")
                invertColors(bitmap)
            }
            // 低对比度（标准差低于 40）
            stats.contrast < 40f -> {
                Log.d(TAG, "检测到低对比度，执行对比度增强")
                enhanceContrast(bitmap, 1.5f)
            }
            // 正常图像：轻度锐化
            else -> {
                Log.d(TAG, "正常图像，执行轻度锐化")
                sharpen(bitmap, 0.5f)
            }
        }
    }

    /**
     * 对比度增强
     *
     * 将像素值从 [mean-std, mean+std] 范围映射到 [0, 255]。
     *
     * @param bitmap 原始图像
     * @param factor 增强系数（1.0=不增强，>1=增强）
     * @return 增强后的图像
     */
    fun enhanceContrast(bitmap: Bitmap, factor: Float = 1.5f): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 计算均值和标准差
        var sum = 0.0
        for (pixel in pixels) {
            val gray = grayValue(pixel)
            sum += gray
        }
        val mean = sum / pixels.size

        var variance = 0.0
        for (pixel in pixels) {
            val gray = grayValue(pixel)
            variance += (gray - mean) * (gray - mean)
        }
        val std = Math.sqrt(variance / pixels.size).toFloat()

        if (std < 1f) return bitmap

        val result = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            val newR = ((r - mean) * factor + mean).coerceIn(0f, 255f).toInt()
            val newG = ((g - mean) * factor + mean).coerceIn(0f, 255f).toInt()
            val newB = ((b - mean) * factor + mean).coerceIn(0f, 255f).toInt()

            result[i] = Color.argb(
                (pixel shr 24) and 0xFF,
                newR, newG, newB
            )
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(result, 0, width, 0, 0, width, height)
        }
    }

    /**
     * 反色处理（用于深色模式截屏）
     *
     * 将深色背景白字反转为白底黑字，便于 OCR 检测。
     *
     * @param bitmap 原始图像
     * @return 反色后的图像
     */
    fun invertColors(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val result = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = (pixel shr 24) and 0xFF
            val r = 255 - ((pixel shr 16) and 0xFF)
            val g = 255 - ((pixel shr 8) and 0xFF)
            val b = 255 - (pixel and 0xFF)
            result[i] = Color.argb(a, r, g, b)
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(result, 0, width, 0, 0, width, height)
        }
    }

    /**
     * 图像锐化（Unsharp Mask）
     *
     * 使用拉普拉斯算子增强边缘细节，使文字笔画更清晰。
     *
     * @param bitmap 原始图像
     * @param amount 锐化强度（0.0-1.0）
     * @return 锐化后的图像
     */
    fun sharpen(bitmap: Bitmap, amount: Float = 0.5f): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val result = IntArray(pixels.size)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x

                // 拉普拉斯 3x3 卷积核
                val center = grayValue(pixels[idx]).toFloat()
                val top = grayValue(pixels[(y - 1) * width + x]).toFloat()
                val bottom = grayValue(pixels[(y + 1) * width + x]).toFloat()
                val left = grayValue(pixels[y * width + (x - 1)]).toFloat()
                val right = grayValue(pixels[y * width + (x + 1)]).toFloat()

                // 拉普拉斯值 = 4*center - top - bottom - left - right
                val laplacian = 4 * center - top - bottom - left - right

                // Unsharp Mask: original + amount * laplacian
                val pixel = pixels[idx]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val newR = (r + amount * laplacian).coerceIn(0f, 255f).toInt()
                val newG = (g + amount * laplacian).coerceIn(0f, 255f).toInt()
                val newB = (b + amount * laplacian).coerceIn(0f, 255f).toInt()

                result[idx] = Color.argb(
                    (pixel shr 24) and 0xFF,
                    newR, newG, newB
                )
            }
        }

        // 边界像素直接复制
        for (x in 0 until width) {
            result[x] = pixels[x]
            result[(height - 1) * width + x] = pixels[(height - 1) * width + x]
        }
        for (y in 0 until height) {
            result[y * width] = pixels[y * width]
            result[y * width + width - 1] = pixels[y * width + width - 1]
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(result, 0, width, 0, 0, width, height)
        }
    }

    /**
     * 自适应二值化（Sauvola 算法简化版）
     *
     * 对灰度图像进行局部自适应二值化，增强文字与背景的对比。
     * 适用于光照不均匀或背景复杂的图像。
     *
     * @param bitmap 原始图像
     * @param blockSize 局部窗口大小（默认 15）
     * @param k 权重系数（默认 0.2）
     * @return 二值化后的图像（灰度图，非彩色）
     */
    fun adaptiveBinarize(bitmap: Bitmap, blockSize: Int = 15, k: Float = 0.2f): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 先转灰度
        val gray = FloatArray(width * height)
        for (i in pixels.indices) {
            gray[i] = grayValue(pixels[i]).toFloat()
        }

        // 计算积分图（用于快速计算局部均值和标准差）
        val integral = FloatArray(width * height)
        val integralSq = FloatArray(width * height)

        for (y in 0 until height) {
            var rowSum = 0f
            var rowSumSq = 0f
            for (x in 0 until width) {
                val idx = y * width + x
                rowSum += gray[idx]
                rowSumSq += gray[idx] * gray[idx]
                if (y == 0) {
                    integral[idx] = rowSum
                    integralSq[idx] = rowSumSq
                } else {
                    integral[idx] = integral[(y - 1) * width + x] + rowSum
                    integralSq[idx] = integralSq[(y - 1) * width + x] + rowSumSq
                }
            }
        }

        val halfBlock = blockSize / 2
        val result = IntArray(pixels.size)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x

                val y0 = (y - halfBlock).coerceAtLeast(0)
                val y1 = (y + halfBlock).coerceAtMost(height - 1)
                val x0 = (x - halfBlock).coerceAtLeast(0)
                val x1 = (x + halfBlock).coerceAtMost(width - 1)

                val count = (y1 - y0 + 1) * (x1 - x0 + 1)

                // 局部均值
                val sum = getIntegral(integral, width, y1, x1) -
                        getIntegral(integral, width, y0 - 1, x1) -
                        getIntegral(integral, width, y1, x0 - 1) +
                        getIntegral(integral, width, y0 - 1, x0 - 1)
                val mean = sum / count

                // 局部标准差
                val sumSq = getIntegral(integralSq, width, y1, x1) -
                        getIntegral(integralSq, width, y0 - 1, x1) -
                        getIntegral(integralSq, width, y1, x0 - 1) +
                        getIntegral(integralSq, width, y0 - 1, x0 - 1)
                val variance = sumSq / count - mean * mean
                val std = Math.sqrt(variance.coerceAtLeast(0f).toDouble()).toFloat()

                // Sauvola 阈值
                val threshold = mean * (1f + k * (std / 128f - 1f))

                val pixelVal = if (gray[idx] > threshold) 255 else 0
                result[idx] = Color.argb(255, pixelVal, pixelVal, pixelVal)
            }
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(result, 0, width, 0, 0, width, height)
        }
    }

    /**
     * 从积分图获取区域和
     */
    private fun getIntegral(integral: FloatArray, width: Int, y: Int, x: Int): Float {
        if (y < 0 || x < 0) return 0f
        return integral[y.coerceAtMost(integral.size / width - 1) * width + x.coerceAtMost(width - 1)]
    }

    /**
     * 分析图像特征
     */
    private fun analyzeImage(bitmap: Bitmap): ImageStats {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var sum = 0.0
        var sumSq = 0.0
        val sampleStep = maxOf(1, pixels.size / 10000) // 采样以提高性能

        var count = 0
        for (i in pixels.indices step sampleStep) {
            val gray = grayValue(pixels[i]).toFloat()
            sum += gray
            sumSq += gray * gray
            count++
        }

        val mean = (sum / count).toFloat()
        val variance = (sumSq / count - mean * mean).coerceAtLeast(0f)
        val std = Math.sqrt(variance.toDouble()).toFloat()

        return ImageStats(
            avgBrightness = mean,
            contrast = std
        )
    }

    /**
     * 计算灰度值
     */
    private fun grayValue(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        // 使用 ITU-R BT.601 加权
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    /**
     * 图像统计数据
     */
    private data class ImageStats(
        val avgBrightness: Float,
        val contrast: Float
    )
}
