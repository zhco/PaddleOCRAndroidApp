package com.example.paddleocrapp.model

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

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
) : Comparable<ImageItem>, Parcelable {

    /**
     * 按拍摄时间排序（默认）
     */
    override fun compareTo(other: ImageItem): Int {
        return this.dateTaken.compareTo(other.dateTaken)
    }

    constructor(parcel: Parcel) : this(
        id = parcel.readLong(),
        uri = parcel.readParcelable(Uri::class.java.classLoader)!!,
        name = parcel.readString()!!,
        path = parcel.readString()!!,
        dateTaken = parcel.readLong(),
        dateModified = parcel.readLong(),
        width = parcel.readInt(),
        height = parcel.readInt(),
        size = parcel.readLong(),
        recognizedText = parcel.readString() ?: "",
        isRecognized = parcel.readByte() != 0.toByte(),
        order = parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeParcelable(uri, flags)
        parcel.writeString(name)
        parcel.writeString(path)
        parcel.writeLong(dateTaken)
        parcel.writeLong(dateModified)
        parcel.writeInt(width)
        parcel.writeInt(height)
        parcel.writeLong(size)
        parcel.writeString(recognizedText)
        parcel.writeByte(if (isRecognized) 1 else 0)
        parcel.writeInt(order)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ImageItem> {
        override fun createFromParcel(parcel: Parcel): ImageItem {
            return ImageItem(parcel)
        }

        override fun newArray(size: Int): Array<ImageItem?> {
            return arrayOfNulls(size)
        }

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
