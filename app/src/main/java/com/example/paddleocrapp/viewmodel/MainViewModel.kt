package com.example.paddleocrapp.viewmodel

import androidx.lifecycle.ViewModel
import com.example.paddleocrapp.model.ImageItem
import com.example.paddleocrapp.model.SortMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主界面 ViewModel
 */
class MainViewModel : ViewModel() {

    private val _selectedImages = MutableStateFlow<List<ImageItem>>(emptyList())
    val selectedImages: StateFlow<List<ImageItem>> = _selectedImages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * 添加图片
     */
    fun addImages(images: List<ImageItem>) {
        val current = _selectedImages.value.toMutableList()
        current.addAll(images)
        _selectedImages.value = current
    }

    /**
     * 移除图片
     */
    fun removeImage(imageItem: ImageItem) {
        val current = _selectedImages.value.toMutableList()
        current.remove(imageItem)
        _selectedImages.value = current
    }

    /**
     * 清空图片
     */
    fun clearImages() {
        _selectedImages.value = emptyList()
    }

    /**
     * 排序图片
     */
    fun sortImages(sortMode: SortMode) {
        val current = _selectedImages.value
        val sorted = when (sortMode) {
            SortMode.BY_DATE_TAKEN -> ImageItem.sortByDateTaken(current)
            SortMode.BY_DATE_MODIFIED -> ImageItem.sortByDateModified(current)
            SortMode.BY_NAME -> ImageItem.sortByName(current)
        }
        _selectedImages.value = sorted
    }

    /**
     * 移动项目位置
     */
    fun moveItem(fromPosition: Int, toPosition: Int) {
        val current = _selectedImages.value.toMutableList()
        if (fromPosition in current.indices && toPosition in current.indices) {
            val item = current.removeAt(fromPosition)
            current.add(toPosition, item)
            _selectedImages.value = current
        }
    }

    /**
     * 项目上移
     */
    fun moveItemUp(imageItem: ImageItem) {
        val current = _selectedImages.value
        val index = current.indexOf(imageItem)
        if (index > 0) {
            moveItem(index, index - 1)
        }
    }

    /**
     * 项目下移
     */
    fun moveItemDown(imageItem: ImageItem) {
        val current = _selectedImages.value
        val index = current.indexOf(imageItem)
        if (index >= 0 && index < current.size - 1) {
            moveItem(index, index + 1)
        }
    }

    /**
     * 更新图片的识别结果（从 TextResultActivity 返回）
     */
    fun updateRecognizedResults(updatedImages: List<ImageItem>) {
        val current = _selectedImages.value.toMutableList()
        for (updated in updatedImages) {
            val index = current.indexOfFirst { it.uri == updated.uri }
            if (index >= 0) {
                current[index] = updated
            }
        }
        _selectedImages.value = current
    }

    /**
     * 获取已识别的图片数量
     */
    fun getRecognizedCount(): Int {
        return _selectedImages.value.count { it.isRecognized }
    }

    /**
     * 获取所有已识别图片按当前顺序排列
     */
    fun getRecognizedImagesInOrder(): List<ImageItem> {
        return _selectedImages.value.filter { it.isRecognized }
    }

    /**
     * 是否有已识别的结果可以导出
     */
    fun hasRecognizedResults(): Boolean {
        return _selectedImages.value.any { it.isRecognized }
    }
}
