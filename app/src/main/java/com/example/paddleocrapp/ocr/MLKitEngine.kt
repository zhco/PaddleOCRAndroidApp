package com.example.paddleocrapp.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google ML Kit OCR 引擎
 *
 * 使用 ML Kit 中文文字识别，支持中英文混合识别。
 */
class MLKitEngine : OCRManager.OCREngineInterface {

    companion object {
        private const val TAG = "MLKitEngine"
    }

    private var textRecognizer: TextRecognizer? = null
    private var isInitialized = false

    override fun initialize(): Boolean {
        return try {
            val options = ChineseTextRecognizerOptions.Builder().build()
            textRecognizer = TextRecognition.getClient(options)
            isInitialized = true
            Log.i(TAG, "ML Kit 中文识别引擎初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit 初始化失败", e)
            false
        }
    }

    override fun recognize(bitmap: Bitmap): OCRResult {
        if (!isInitialized || textRecognizer == null) {
            return OCRResult.error("ML Kit 未初始化")
        }

        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = textRecognizer!!.process(image).get()

            val lines = mutableListOf<String>()
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val text = line.text.trim()
                    if (text.isNotEmpty()) {
                        lines.add(text)
                    }
                }
            }

            val fullText = lines.joinToString("\n")
            if (fullText.isNotEmpty()) {
                OCRResult.success(fullText, 0.9f)
            } else {
                OCRResult.error("未识别到文字")
            }
        } catch (e: Exception) {
            Log.e(TAG, "识别异常", e)
            OCRResult.error("识别异常: ${e.message}")
        }
    }

    override fun release() {
        textRecognizer?.close()
        textRecognizer = null
        isInitialized = false
        Log.i(TAG, "ML Kit 资源已释放")
    }
}
