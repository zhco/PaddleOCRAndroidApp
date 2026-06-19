package com.example.paddleocrapp.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract4android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream

/**
 * Tesseract OCR 引擎
 *
 * 使用 Tesseract 4.x 作为 OCR 引擎，支持中文和英文识别。
 * tessdata 语言包从 assets 复制到私有目录。
 */
class TesseractEngine(private val context: Context) : OCRManager.OCREngineInterface {

    companion object {
        private const val TAG = "TesseractEngine"
        private const val TESSDATA_DIR = "tessdata"
        private const val DEFAULT_LANG = "chi_sim+eng"
    }

    private var tessApi: TessBaseAPI? = null
    private var isInitialized = false

    /**
     * 初始化 Tesseract 引擎
     */
    fun initialize(): Boolean {
        return try {
            // 确保 tessdata 目录存在
            val tessDir = File(context.filesDir, TESSDATA_DIR)
            if (!tessDir.exists()) {
                tessDir.mkdirs()
            }

            // 复制语言数据文件
            copyTessDataIfNeeded(tessDir)

            // 初始化 TessBaseAPI
            val dataPath = context.filesDir.absolutePath
            tessApi = TessBaseAPI()
            val result = tessApi?.init(dataPath, DEFAULT_LANG)
            if (result == 0) {
                isInitialized = true
                Log.i(TAG, "Tesseract 初始化成功 (语言: $DEFAULT_LANG)")
                true
            } else {
                Log.e(TAG, "Tesseract 初始化失败 (code: $result)")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tesseract 初始化异常", e)
            false
        }
    }

    /**
     * 识别图片中的文字
     */
    fun recognize(bitmap: Bitmap): OCRResult {
        if (!isInitialized || tessApi == null) {
            return OCRResult.error("Tesseract 未初始化")
        }

        return try {
            tessApi?.setImage(bitmap)
            val text = tessApi?.utF8Text?.trim() ?: ""
            val confidence = tessApi?.meanConfidence()?.toFloat() ?: 0f

            // 转换置信度: Tesseract 返回 0-100, 转为 0-1
            val normalizedConfidence = confidence / 100f

            if (text.isNotEmpty()) {
                OCRResult.success(text, normalizedConfidence)
            } else {
                OCRResult.error("未识别到文字")
            }
        } catch (e: Exception) {
            Log.e(TAG, "识别失败", e)
            OCRResult.error("识别异常: ${e.message}")
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        try {
            tessApi?.end()
            tessApi = null
            isInitialized = false
            Log.i(TAG, "Tesseract 资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放资源异常", e)
        }
    }

    fun isReady(): Boolean = isInitialized

    /**
     * 从 assets 复制 tessdata 文件
     */
    private fun copyTessDataIfNeeded(tessDir: File) {
        val langFiles = listOf(
            "chi_sim.traineddata",
            "eng.traineddata"
        )

        for (filename in langFiles) {
            val destFile = File(tessDir, filename)
            if (!destFile.exists() || destFile.length() == 0L) {
                try {
                    context.assets.open("tessdata/$filename").use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "复制 tessdata: $filename (${destFile.length()} bytes)")
                } catch (e: Exception) {
                    Log.w(TAG, "复制 tessdata 失败: $filename (${e.message})")
                }
            }
        }
    }
}
