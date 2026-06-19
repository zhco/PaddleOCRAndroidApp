package com.example.paddleocrapp.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google ML Kit OCR 引擎
 *
 * 使用 ML Kit 中文文字识别，支持中英文混合识别。
 * 无需额外下载模型，开箱即用。
 */
class MLKitEngine : OCRManager.OCREngineInterface {

    companion object {
        private const val TAG = "MLKitEngine"
    }

    private var recognizer: TextRecognition? = null
    private var isInitialized = false

    override fun initialize(): Boolean {
        return try {
            recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            isInitialized = true
            Log.i(TAG, "ML Kit 中文识别引擎初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit 初始化失败", e)
            false
        }
    }

    override suspend fun recognize(bitmap: Bitmap): OCRResult {
        if (!isInitialized || recognizer == null) {
            return OCRResult.error("ML Kit 未初始化")
        }

        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { cont ->
                recognizer!!.process(image)
                    .addOnSuccessListener { visionText ->
                        cont.resume(visionText)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "ML Kit 识别失败", e)
                        cont.resume(null)
                    }
            }

            if (result == null) {
                return OCRResult.error("识别失败")
            }

            // 提取所有文本块
            val lines = mutableListOf<String>()
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val text = line.text.trim()
                    if (text.isNotEmpty()) {
                        lines.add(text)
                    }
                }
            }

            val fullText = lines.joinToString("\n")
            if (fullText.isNotEmpty()) {
                // ML Kit 没有直接的置信度，使用块级别置信度平均值
                val confidence = if (result.textBlocks.isNotEmpty()) {
                    result.textBlocks.mapNotNull { it.confidence?.toFloat() }.average().toFloat()
                } else {
                    0.8f // 默认置信度
                }
                OCRResult.success(fullText, confidence)
            } else {
                OCRResult.error("未识别到文字")
            }
        } catch (e: Exception) {
            Log.e(TAG, "识别异常", e)
            OCRResult.error("识别异常: ${e.message}")
        }
    }

    override fun release() {
        recognizer?.close()
        recognizer = null
        isInitialized = false
        Log.i(TAG, "ML Kit 资源已释放")
    }
}
