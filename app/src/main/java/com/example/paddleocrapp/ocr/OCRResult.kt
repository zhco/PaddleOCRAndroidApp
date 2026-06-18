package com.example.paddleocrapp.ocr

/**
 * OCR 识别结果数据类
 *
 * 包含完整的 OCR 识别结果，包括识别出的文本、置信度得分和检测到的文本框位置信息。
 *
 * @property text 识别出的完整文本内容，多行文本以换行符分隔
 * @property score 识别结果的平均置信度得分，范围 [0, 1]
 * @property textBoxes 检测到的所有文本框列表，包含位置坐标和检测得分
 */
data class OCRResult(
    val text: String,
    val score: Float,
    val textBoxes: List<TextBox> = emptyList()
) {
    companion object {
        /**
         * 创建一个错误结果
         *
         * @param msg 错误描述信息
         * @return 包含错误信息的 OCRResult，得分为 0
         */
        fun error(msg: String) = OCRResult(msg, 0f)

        /**
         * 创建一个成功结果
         *
         * @param text 识别出的文本
         * @param score 识别置信度得分，默认为 1.0
         * @param boxes 检测到的文本框列表，默认为空
         * @return 包含识别结果的 OCRResult
         */
        fun success(text: String, score: Float = 1f, boxes: List<TextBox> = emptyList()) =
            OCRResult(text, score, boxes)
    }

    /**
     * 判断识别是否成功（文本非空且得分大于0）
     */
    fun isSuccess(): Boolean = text.isNotEmpty() && score > 0f

    /**
     * 判断是否为错误结果
     */
    fun isError(): Boolean = score == 0f && text.isNotEmpty()
}
