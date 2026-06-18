package com.example.paddleocrapp.ocr

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log

/**
 * 方向分类器
 *
 * 判断文本图像的方向是否需要旋转 180 度。
 * 用于处理倒置文本的情况，提高识别准确率。
 *
 * 处理流程：
 * 1. 预处理：resize 到 48x192，归一化，NHWC->NCHW
 * 2. 推理：调用 Paddle Lite 预测器
 * 3. 后处理：softmax 获取概率，判断是否需要旋转 180 度
 *
 * 分类结果：
 * - class 0：正常方向（0 度），无需旋转
 * - class 1：倒置方向（180 度），需要旋转
 */
class Classifier(
    private val predictor: PaddleLitePredictor,
    private val clsThresh: Float = 0.9f
) {
    companion object {
        private const val TAG = "Classifier"

        /** 分类模型输入宽度 */
        private const val CLS_IMAGE_WIDTH = 192

        /** 分类模型输入高度 */
        private const val CLS_IMAGE_HEIGHT = 48

        /** 分类模型归一化均值 */
        private val CLS_MEAN = floatArrayOf(0.5f, 0.5f, 0.5f)

        /** 分类模型归一化标准差 */
        private val CLS_STD = floatArrayOf(0.5f, 0.5f, 0.5f)
    }

    /**
     * 对文本图像进行方向分类，如果需要则旋转图像
     *
     * @param bitmap 裁剪出的文本区域图像
     * @return 如果需要旋转则返回旋转后的图像，否则返回原图
     */
    fun classify(bitmap: Bitmap): Bitmap {
        // 如果图像太小，不进行分类
        if (bitmap.width < 10 || bitmap.height < 10) {
            return bitmap
        }

        // 预处理
        val (inputData, shape) = preprocess(bitmap)
        if (inputData == null) {
            Log.w(TAG, "分类预处理失败，跳过方向分类")
            return bitmap
        }

        // 推理
        val outputData = predictor.run(inputData, shape)
        if (outputData.isEmpty()) {
            Log.w(TAG, "分类推理失败，跳过方向分类")
            return bitmap
        }

        // 后处理：softmax
        val probs = softmax(outputData)

        // 获取分类结果
        // class 0 = 正常方向（0度），class 1 = 倒置方向（180度）
        val class0Prob = probs[0]
        val class1Prob = probs[1]

        Log.d(TAG, "方向分类结果: 正常=${"%.4f".format(class0Prob)}, 倒置=${"%.4f".format(class1Prob)}")

        // 如果倒置方向的概率大于阈值，则旋转 180 度
        return if (class1Prob > clsThresh) {
            Log.d(TAG, "检测到倒置文本，旋转 180 度")
            rotateBitmap180(bitmap)
        } else {
            bitmap
        }
    }

    /**
     * 预处理文本图像
     *
     * 步骤：
     * 1. Resize 到 48x192（高 x 宽）
     * 2. 归一化：(pixel - 0.5) / 0.5
     * 3. NHWC -> NCHW 格式转换
     *
     * @param bitmap 输入文本图像
     * @return (输入数据数组, 输入形状) 或 (null, null) 如果失败
     */
    private fun preprocess(bitmap: Bitmap): Pair<FloatArray?, LongArray> {
        return try {
            // Resize 到分类模型输入尺寸
            val resizedBitmap = Bitmap.createScaledBitmap(
                bitmap, CLS_IMAGE_WIDTH, CLS_IMAGE_HEIGHT, true
            )

            // 提取像素数据
            val pixels = IntArray(CLS_IMAGE_WIDTH * CLS_IMAGE_HEIGHT)
            resizedBitmap.getPixels(pixels, 0, CLS_IMAGE_WIDTH, 0, 0, CLS_IMAGE_WIDTH, CLS_IMAGE_HEIGHT)

            // 转换为 float 数组，NHWC -> NCHW
            val channelSize = CLS_IMAGE_WIDTH * CLS_IMAGE_HEIGHT
            val inputData = FloatArray(3 * channelSize)

            for (i in pixels.indices) {
                val pixel = pixels[i]
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f

                // 归一化：(pixel - mean) / std
                inputData[i] = (r - CLS_MEAN[0]) / CLS_STD[0]
                inputData[channelSize + i] = (g - CLS_MEAN[1]) / CLS_STD[1]
                inputData[2 * channelSize + i] = (b - CLS_MEAN[2]) / CLS_STD[2]
            }

            // 回收临时 bitmap
            if (resizedBitmap !== bitmap) {
                resizedBitmap.recycle()
            }

            // 输入形状：[batch, channel, height, width]
            val shape = longArrayOf(1, 3, CLS_IMAGE_HEIGHT.toLong(), CLS_IMAGE_WIDTH.toLong())

            Pair(inputData, shape)
        } catch (e: Exception) {
            Log.e(TAG, "分类预处理异常", e)
            Pair(null, longArrayOf())
        }
    }

    /**
     * Softmax 激活函数
     *
     * 将原始输出分数转换为概率分布，使所有概率之和为 1
     *
     * @param scores 模型原始输出分数
     * @return 概率分布数组
     */
    private fun softmax(scores: FloatArray): FloatArray {
        // 找到最大值（数值稳定性）
        var maxScore = Float.NEGATIVE_INFINITY
        for (s in scores) {
            if (s > maxScore) maxScore = s
        }

        // 计算 exp(score - max) 并求和
        var sum = 0f
        val expScores = FloatArray(scores.size)
        for (i in scores.indices) {
            expScores[i] = Math.exp((scores[i] - maxScore).toDouble()).toFloat()
            sum += expScores[i]
        }

        // 归一化
        val probs = FloatArray(scores.size)
        for (i in scores.indices) {
            probs[i] = expScores[i] / sum
        }

        return probs
    }

    /**
     * 将图像旋转 180 度
     *
     * @param bitmap 原始图像
     * @return 旋转后的新图像
     */
    private fun rotateBitmap180(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply {
            postRotate(180f, bitmap.width / 2f, bitmap.height / 2f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
