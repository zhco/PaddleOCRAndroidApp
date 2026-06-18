package com.example.paddleocrapp.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.paddleocrapp.model.ImageItem
import com.example.paddleocrapp.ocr.OCRResult
import com.example.paddleocrapp.model.PageData
import com.example.paddleocrapp.model.RecognitionState
import com.example.paddleocrapp.ocr.OCRManager
import com.example.paddleocrapp.utils.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 识别结果界面 ViewModel
 *
 * 负责管理 OCR 识别的生命周期，包括：
 * - 模型初始化
 * - 批量图片识别
 * - 识别进度管理
 * - 资源释放
 */
class TextResultViewModel(application: Application) : AndroidViewModel(application) {

    private val ocrManager = OCRManager(application.applicationContext)

    private val _recognitionState = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()

    /**
     * 开始批量识别
     *
     * @param images 待识别的图片列表
     */
    fun startRecognition(images: List<ImageItem>) {
        viewModelScope.launch {
            _recognitionState.value = RecognitionState.Loading

            // 初始化 OCR
            val initialized = ocrManager.initialize()
            if (!initialized) {
                _recognitionState.value = RecognitionState.Error("OCR 模型初始化失败")
                return@launch
            }

            val pages = mutableListOf<PageData>()
            val total = images.size

            images.forEachIndexed { index, imageItem ->
                _recognitionState.value = RecognitionState.Processing(index + 1, total)

                val pageData = processImage(imageItem, index + 1)
                pages.add(pageData)
            }

            _recognitionState.value = RecognitionState.Success(pages)
        }
    }

    /**
     * 处理单张图片
     *
     * @param imageItem 图片信息
     * @param pageNumber 页码
     * @return 页面数据（包含识别文本或错误信息）
     */
    private suspend fun processImage(imageItem: ImageItem, pageNumber: Int): PageData {
        return try {
            val context = getApplication<Application>().applicationContext
            val bitmap = BitmapUtils.loadBitmapFromUri(context, imageItem.uri)

            if (bitmap == null) {
                return PageData(
                    pageNumber = pageNumber,
                    imageItem = imageItem,
                    textContent = "",
                    errorMessage = "无法加载图片"
                )
            }

            val result = ocrManager.recognize(bitmap)

            // 使用新的 OCRResult 数据类提取文本
            val text = when {
                result.isSuccess() -> result.text
                result.isError() -> "识别失败: ${result.text}"
                else -> ""
            }

            // 释放 bitmap
            bitmap.recycle()

            PageData(
                pageNumber = pageNumber,
                imageItem = imageItem,
                textContent = text
            )
        } catch (e: Exception) {
            PageData(
                pageNumber = pageNumber,
                imageItem = imageItem,
                textContent = "",
                errorMessage = e.message
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        ocrManager.release()
    }
}
