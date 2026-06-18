package com.example.paddleocrapp.ocr

import android.graphics.Bitmap
import android.util.Log

/**
 * CRNN 文本识别器
 *
 * 实现 CRNN（Convolutional Recurrent Neural Network）+ CTC（Connectionist Temporal Classification）
 * 解码的文本识别算法。
 *
 * 处理流程：
 * 1. 预处理：resize 到 32x320，归一化，NHWC->NCHW
 * 2. 推理：调用 Paddle Lite 预测器执行 CRNN 模型推理
 * 3. CTC 贪心解码：遍历时间步取 argmax，跳过 blank 和连续重复字符，通过字典查找字符
 *
 * CRNN 网络结构：
 * CNN（特征提取）-> BiLSTM（序列建模）-> CTC（解码）
 */
class Recognizer(
    private val predictor: PaddleLitePredictor,
    private val labels: List<String>,
    private val recImageHeight: Int = 32,
    private val recImageWidth: Int = 320
) {
    companion object {
        private const val TAG = "Recognizer"

        /** 识别模型归一化均值 */
        private val REC_MEAN = floatArrayOf(0.5f, 0.5f, 0.5f)

        /** 识别模型归一化标准差 */
        private val REC_STD = floatArrayOf(0.5f, 0.5f, 0.5f)

        /** CTC blank 标签的索引 */
        private const val CTC_BLANK_INDEX = 0
    }

    /**
     * 识别文本图像中的文字
     *
     * @param bitmap 裁剪出的文本区域图像
     * @return 识别结果对：(文本内容, 平均置信度得分)
     */
    fun recognize(bitmap: Bitmap): Pair<String, Float> {
        // 如果图像太小，直接返回空结果
        if (bitmap.width < 2 || bitmap.height < 2) {
            return Pair("", 0f)
        }

        // 预处理
        val (inputData, shape) = preprocess(bitmap)
        if (inputData == null) {
            Log.w(TAG, "识别预处理失败")
            return Pair("", 0f)
        }

        // 推理
        val outputData = predictor.run(inputData, shape)
        if (outputData.isEmpty()) {
            Log.w(TAG, "识别推理结果为空")
            return Pair("", 0f)
        }

        // CTC 贪心解码
        val (text, score) = ctcGreedyDecode(outputData)

        return Pair(text, score)
    }

    /**
     * 预处理文本图像
     *
     * 步骤：
     * 1. Resize 到 recImageHeight x recImageWidth（32 x 320）
     * 2. 归一化：(pixel - 0.5) / 0.5，将像素值从 [0,1] 映射到 [-1, 1]
     * 3. NHWC -> NCHW 格式转换
     *
     * @param bitmap 输入文本图像
     * @return (输入数据数组, 输入形状) 或 (null, null) 如果失败
     */
    private fun preprocess(bitmap: Bitmap): Pair<FloatArray?, LongArray> {
        return try {
            // Resize 到识别模型输入尺寸
            val resizedBitmap = Bitmap.createScaledBitmap(
                bitmap, recImageWidth, recImageHeight, true
            )

            // 提取像素数据
            val pixels = IntArray(recImageWidth * recImageHeight)
            resizedBitmap.getPixels(pixels, 0, recImageWidth, 0, 0, recImageWidth, recImageHeight)

            // 转换为 float 数组，NHWC -> NCHW
            val channelSize = recImageWidth * recImageHeight
            val inputData = FloatArray(3 * channelSize)

            for (i in pixels.indices) {
                val pixel = pixels[i]
                // 提取 RGB 通道值并归一化到 [0, 1]
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f

                // 归一化：(pixel - mean) / std = (pixel - 0.5) / 0.5 = pixel * 2 - 1
                // 将 [0, 1] 映射到 [-1, 1]
                inputData[i] = (r - REC_MEAN[0]) / REC_STD[0]
                inputData[channelSize + i] = (g - REC_MEAN[1]) / REC_STD[1]
                inputData[2 * channelSize + i] = (b - REC_MEAN[2]) / REC_STD[2]
            }

            // 回收临时 bitmap
            if (resizedBitmap !== bitmap) {
                resizedBitmap.recycle()
            }

            // 输入形状：[batch, channel, height, width]
            val shape = longArrayOf(1, 3, recImageHeight.toLong(), recImageWidth.toLong())

            Pair(inputData, shape)
        } catch (e: Exception) {
            Log.e(TAG, "识别预处理异常", e)
            Pair(null, longArrayOf())
        }
    }

    /**
     * CTC 贪心解码
     *
     * CTC（Connectionist Temporal Classification）解码算法：
     * 1. 模型输出形状为 [timeSteps, numClasses]，每个时间步对每个字符类别的概率
     * 2. 对每个时间步取概率最大的字符（argmax）
     * 3. 跳过 blank 标签（index=0）
     * 4. 跳过连续重复的字符（CTC 的合并规则）
     * 5. 通过字典查找最终字符
     *
     * @param outputData 模型输出数据，形状为 [timeSteps, numClasses] 展平后的一维数组
     * @return 解码结果对：(识别文本, 平均置信度得分)
     */
    private fun ctcGreedyDecode(outputData: FloatArray): Pair<String, Float> {
        // 获取输出形状 [1, timeSteps, numClasses] 或 [timeSteps, numClasses]
        val outputShape = predictor.getOutputShape()

        // 确定 timeSteps 和 numClasses
        val timeSteps: Int
        val numClasses: Int

        when {
            outputShape.size == 3 -> {
                // 形状 [1, timeSteps, numClasses]
                timeSteps = outputShape[1].toInt()
                numClasses = outputShape[2].toInt()
            }
            outputShape.size == 2 -> {
                // 形状 [timeSteps, numClasses]
                timeSteps = outputShape[0].toInt()
                numClasses = outputShape[1].toInt()
            }
            else -> {
                Log.e(TAG, "输出形状不正确: ${outputShape.toList()}")
                return Pair("", 0f)
            }
        }

        if (timeSteps == 0 || numClasses == 0) {
            Log.e(TAG, "输出维度为0: timeSteps=$timeSteps, numClasses=$numClasses")
            return Pair("", 0f)
        }

        // CTC 贪心解码
        val decodedChars = mutableListOf<Int>()  // 解码后的字符索引
        val decodedScores = mutableListOf<Float>()  // 每个字符的置信度
        var lastIndex = -1  // 上一个解码的字符索引，用于去重

        for (t in 0 until timeSteps) {
            // 获取当前时间步的输出（所有类别的分数）
            val offset = t * numClasses

            // 找到概率最大的类别（argmax）
            var maxProb = Float.NEGATIVE_INFINITY
            var maxIndex = 0

            for (c in 0 until numClasses) {
                val prob = outputData[offset + c]
                if (prob > maxProb) {
                    maxProb = prob
                    maxIndex = c
                }
            }

            // 跳过 blank 标签（CTC blank index = 0）
            if (maxIndex == CTC_BLANK_INDEX) {
                continue
            }

            // 跳过连续重复的字符（CTC 合并规则）
            if (maxIndex == lastIndex) {
                continue
            }

            // 记录解码字符
            decodedChars.add(maxIndex)
            decodedScores.add(maxProb)
            lastIndex = maxIndex
        }

        // 通过字典查找字符
        val textBuilder = StringBuilder()
        for (charIndex in decodedChars) {
            if (charIndex > 0 && charIndex < labels.size) {
                textBuilder.append(labels[charIndex])
            }
        }

        // 计算平均置信度
        val avgScore = if (decodedScores.isNotEmpty()) {
            decodedScores.average().toFloat()
        } else {
            0f
        }

        val text = textBuilder.toString()
        Log.d(TAG, "识别结果: \"$text\", 得分: ${"%.4f".format(avgScore)}")

        return Pair(text, avgScore)
    }
}
