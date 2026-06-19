package com.example.paddleocrapp.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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

    private var textRecognizer: TextRecognizer? = null
    private var isInitialized = false

    override fun initialize(): Boolean {
        return try {
            textRecognizer = ChineseTextRecognizer.getClient()
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
            val resultRef = AtomicReference<com.google.mlkit.vision.text.Text>(null)
            val errorRef = AtomicReference<Exception>(null)
            val latch = CountDownLatch(1)

            textRecognizer!!.process(image)
                .addOnSuccessListener { text ->
                    resultRef.set(text)
                    latch.countDown()
                }
                .addOnFailureListener { e ->
                    errorRef.set(e)
                    latch.countDown()
                }

            latch.await(30, TimeUnit.SECONDS)

            val error = errorRef.get()
            if (error != null) {
                return OCRResult.error("识别失败: ${error.message}")
            }

            val visionText = resultRef.get()
            if (visionText == null) {
                return OCRResult.error("识别超时")
            }

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
