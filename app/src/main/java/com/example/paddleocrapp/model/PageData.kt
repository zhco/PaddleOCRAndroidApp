package com.example.paddleocrapp.model

/**
 * 分页数据类 - 每页对应一张图片的识别结果
 */
data class PageData(
    val pageNumber: Int,           // 页码（从1开始）
    val imageItem: ImageItem,      // 对应的图片
    val textContent: String,       // 识别出的文字内容
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    /**
     * 获取格式化的页码显示
     */
    fun getFormattedPageNumber(): String = "第 ${pageNumber} 页"

    /**
     * 获取图片信息摘要
     */
    fun getImageInfo(): String {
        return "${imageItem.name} (${imageItem.width}x${imageItem.height})"
    }
}

/**
 * 识别任务状态
 */
sealed class RecognitionState {
    object Idle : RecognitionState()
    object Loading : RecognitionState()
    data class Processing(val current: Int, val total: Int) : RecognitionState()
    data class Success(val pages: List<PageData>) : RecognitionState()
    data class Error(val message: String) : RecognitionState()
}
