package com.example.paddleocrapp.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * OCR 管理器 - 负责模型加载和图片识别
 */
class OCRManager(private val context: Context) {

    companion object {
        private const val TAG = "OCRManager"
        private const val MODEL_DIR = "models"
        private const val DET_MODEL = "ch_PP-OCRv4_det_slim_opt.nb"
        private const val REC_MODEL = "ch_PP-OCRv4_rec_slim_opt.nb"
        private const val CLS_MODEL = "ch_ppocr_mobile_v2.0_cls_slim_opt.nb"
        private const val LABEL_FILE = "ppocr_keys_v1.txt"
    }

    private var isInitialized = false

    /**
     * 初始化 OCR 模型
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 复制模型文件到应用私有目录
            copyModelFiles()

            val modelDir = File(context.filesDir, MODEL_DIR)
            val detPath = File(modelDir, DET_MODEL).absolutePath
            val recPath = File(modelDir, REC_MODEL).absolutePath
            val clsPath = File(modelDir, CLS_MODEL).absolutePath
            val labelPath = File(modelDir, LABEL_FILE).absolutePath

            // 调用 native 初始化
            isInitialized = OCRNative.initOCR(
                detPath, recPath, clsPath, labelPath,
                threadNum = getOptimalThreadCount(),
                useOpenCL = false
            )

            Log.d(TAG, "OCR initialization result: $isInitialized")
            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OCR", e)
            false
        }
    }

    /**
     * 识别单张图片
     */
    suspend fun recognize(bitmap: Bitmap): OCRResult = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            return@withContext OCRResult.Error("OCR 未初始化")
        }

        try {
            val resultText = OCRNative.recognizeImage(bitmap)
            OCRResult.Success(resultText)
        } catch (e: Exception) {
            Log.e(TAG, "Recognition failed", e)
            OCRResult.Error(e.message ?: "识别失败")
        }
    }

    /**
     * 批量识别图片
     */
    suspend fun recognizeBatch(bitmaps: List<Bitmap>): List<OCRResult> = withContext(Dispatchers.Default) {
        bitmaps.mapIndexed { index, bitmap ->
            Log.d(TAG, "Processing image ${index + 1}/${bitmaps.size}")
            recognize(bitmap)
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        OCRNative.releaseOCR()
        isInitialized = false
    }

    /**
     * 复制模型文件从 assets 到私有目录
     */
    private fun copyModelFiles() {
        val modelDir = File(context.filesDir, MODEL_DIR)
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        val modelFiles = listOf(DET_MODEL, REC_MODEL, CLS_MODEL, LABEL_FILE)
        modelFiles.forEach { filename ->
            val destFile = File(modelDir, filename)
            if (!destFile.exists()) {
                copyAssetFile("$MODEL_DIR/$filename", destFile)
            }
        }
    }

    /**
     * 复制单个 asset 文件
     */
    private fun copyAssetFile(assetPath: String, destFile: File) {
        context.assets.open(assetPath).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        Log.d(TAG, "Copied asset: $assetPath -> ${destFile.absolutePath}")
    }

    /**
     * 获取最优线程数
     */
    private fun getOptimalThreadCount(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return minOf(cores, 4)
    }

    fun isReady(): Boolean = isInitialized
}

/**
 * OCR 识别结果密封类
 */
sealed class OCRResult {
    data class Success(val text: String) : OCRResult()
    data class Error(val message: String) : OCRResult()
}
