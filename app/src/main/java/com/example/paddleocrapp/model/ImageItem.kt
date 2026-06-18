package com.example.paddleocrapp.model

import android.net.Uri

/**
 * 图片项数据类 - 包含图片元数据和识别结果
 */
data class ImageItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val path: String,
    val dateTaken: Long,        // 拍摄时间戳
    val dateModified: Long,     // 修改时间戳
    val width: Int,
    val height: Int,
    val size: Long,
    var recognizedText: String = "",  // 识别出的文字
    var isRecognized: Boolean = false,
    var order: Int = 0          // 排序顺序
) : Comparable<ImageItem> {

    /**
     * 按拍摄时间排序（默认）
     */
    override fun compareTo(other: ImageItem): Int {
        return this.dateTaken.compareTo(other.dateTaken)
    }

    companion object {
        /**
         * 按拍摄时间排序
         */
        fun sortByDateTaken(items: List<ImageItem>): List<ImageItem> {
            return items.sortedBy { it.dateTaken }
                .mapIndexed { index, item -> item.copy(order = index) }
        }

        /**
         * 按文件名排序
         */
        fun sortByName(items: List<ImageItem>): List<ImageItem> {
            return items.sortedBy { it.name }
                .mapIndexed { index, item -> item.copy(order = index) }
        }

        /**
         * 按修改时间排序
         */
        fun sortByDateModified(items: List<ImageItem>): List<ImageItem> {
            return items.sortedBy { it.dateModified }
                .mapIndexed { index, item -> item.copy(order = index) }
        }
    }
}

/**
 * 排序方式枚举
 */
enum class SortMode {
    BY_DATE_TAKEN,      // 按拍摄时间
    BY_DATE_MODIFIED,   // 按修改时间
    BY_NAME             // 按文件名
}
