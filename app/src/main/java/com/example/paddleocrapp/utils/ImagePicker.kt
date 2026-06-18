package com.example.paddleocrapp.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import com.example.paddleocrapp.model.ImageItem
import com.example.paddleocrapp.model.SortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 图片选择器 - 处理批量图片导入
 */
class ImagePicker(private val activity: FragmentActivity) {

    private var onImagesPicked: ((List<ImageItem>) -> Unit)? = null

    /**
     * 多选图片 Launcher
     */
    private val pickMultipleImages: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val imageUris = mutableListOf<Uri>()

                data?.clipData?.let { clipData ->
                    // 多选
                    for (i in 0 until clipData.itemCount) {
                        imageUris.add(clipData.getItemAt(i).uri)
                    }
                } ?: data?.data?.let { uri ->
                    // 单选
                    imageUris.add(uri)
                }

                // 异步处理图片信息
                activity.lifecycleScope?.launchWhenStarted {
                    val imageItems = processUris(imageUris)
                    onImagesPicked?.invoke(imageItems)
                }
            }
        }

    /**
     * 启动图片选择器
     */
    fun pickImages(callback: (List<ImageItem>) -> Unit) {
        onImagesPicked = callback
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        pickMultipleImages.launch(intent)
    }

    /**
     * 处理 URI 列表，获取图片详细信息
     */
    private suspend fun processUris(uris: List<Uri>): List<ImageItem> = withContext(Dispatchers.IO) {
        uris.mapIndexedNotNull { index, uri ->
            getImageItemFromUri(activity, uri, index.toLong())
        }
    }

    /**
     * 从 URI 获取图片详细信息
     */
    private fun getImageItemFromUri(context: Context, uri: Uri, id: Long): ImageItem? {
        return try {
            val projection = arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.SIZE
            )

            var name = ""
            var path = ""
            var dateTaken = 0L
            var dateModified = 0L
            var width = 0
            var height = 0
            var size = 0L

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: ""
                    path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)) ?: ""
                    dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN))
                    dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)) * 1000
                    width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH))
                    height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT))
                    size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                }
            }

            // 如果 MediaStore 查询失败，尝试从 OpenableColumns 获取基本信息
            if (name.isEmpty()) {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            name = cursor.getString(nameIndex) ?: "unknown"
                        }
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            }

            // 如果拍摄时间为空，使用修改时间
            if (dateTaken == 0L) {
                dateTaken = dateModified
            }

            ImageItem(
                id = id,
                uri = uri,
                name = name,
                path = path,
                dateTaken = dateTaken,
                dateModified = dateModified,
                width = width,
                height = height,
                size = size,
                order = index.toInt()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        /**
         * 对图片列表按指定方式排序
         */
        fun sortImages(images: List<ImageItem>, sortMode: SortMode): List<ImageItem> {
            return when (sortMode) {
                SortMode.BY_DATE_TAKEN -> ImageItem.sortByDateTaken(images)
                SortMode.BY_DATE_MODIFIED -> ImageItem.sortByDateModified(images)
                SortMode.BY_NAME -> ImageItem.sortByName(images)
            }
        }
    }
}

// Extension property for lifecycle scope
private val FragmentActivity.lifecycleScope
    get() = (this as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.let {
        androidx.lifecycle.lifecycleScope
    }
