package com.example.paddleocrapp.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * OCR 管理器 - 负责引擎初始化和图片识别
 *
 * 优先使用 Paddle Lite 引擎（如果 .so 可用），
 * 否则降级使用 Google ML Kit 引擎。
 */
class OCRManager(private val context: Context) {

    companion object {
        private const val TAG = "OCRManager"
        private const val MODEL_DIR = "models"
    }

    /** OCR 引擎接口 */
    interface OCREngineInterface {
        fun initialize(): Boolean
        fun recognize(bitmap: Bitmap): OCRResult
        fun release()
    }

    /** 当前使用的引擎 */
    private var engine: OCREngineInterface? = null

    /** 引擎是否已初始化 */
    private var isInitialized = false

    /** 初始化错误消息 */
    var initErrorMessage: String? = null
        private set

    /** 当前引擎名称 */
    var engineName: String = "未初始化"
        private set

    /**
     * 初始化 OCR 引擎
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "开始初始化 OCR 管理器...")

            // 方案1: 尝试 Paddle Lite 引擎
            if (PaddleLiteLoader.ensureLoaded()) {
                Log.i(TAG, "尝试使用 Paddle Lite 引擎...")
                val paddleEngine = createPaddleLiteEngine()
                if (paddleEngine != null && paddleEngine.initialize()) {
                    engine = paddleEngine
                    engineName = "Paddle OCR"
                    isInitialized = true
                    Log.i(TAG, "Paddle Lite 引擎初始化成功")
                    return@withContext true
                }
                paddleEngine?.release()
                Log.w(TAG, "Paddle Lite 引擎初始化失败，降级到 ML Kit")
            }

            // 方案2: 使用 ML Kit 引擎
            Log.i(TAG, "使用 Google ML Kit 引擎...")
            val mlKitEngine = MLKitEngine()
            if (mlKitEngine.initialize()) {
                engine = mlKitEngine
                engineName = "Google ML Kit"
                isInitialized = true
                initErrorMessage = null
                Log.i(TAG, "ML Kit 引擎初始化成功")
                return@withContext true
            } else {
                mlKitEngine.release()
            }

            initErrorMessage = "OCR 引擎初始化失败。\n\n请检查网络连接后重试。"
            Log.e(TAG, "所有 OCR 引擎初始化失败")
            false
        } catch (e: Exception) {
            initErrorMessage = "OCR 初始化异常: ${e.message}"
            Log.e(TAG, "OCR 管理器初始化异常", e)
            false
        }
    }

    /**
     * 识别单张图片
     */
    suspend fun recognize(bitmap: Bitmap): OCRResult = withContext(Dispatchers.Default) {
        if (!isInitialized || engine == null) {
            return@withContext OCRResult.error("OCR 未初始化")
        }
        try {
            engine!!.recognize(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "识别失败", e)
            OCRResult.error(e.message ?: "识别失败")
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        engine?.release()
        engine = null
        isInitialized = false
        Log.i(TAG, "OCR 管理器资源已释放")
    }

    fun isReady(): Boolean = isInitialized

    /**
     * 创建 Paddle Lite 引擎（如果模型文件就绪）
     */
    private fun createPaddleLiteEngine(): OCREngine? {
        return try {
            val modelDir = File(context.filesDir, MODEL_DIR)
            val detModel = File(modelDir, "ch_PP-OCRv4_mobile_det.nb")
            val recModel = File(modelDir, "ch_PP-OCRv4_mobile_rec.nb")
            val clsModel = File(modelDir, "ch_PP-OCRv4_mobile_cls.nb")
            val labelFile = File(modelDir, "ppocr_keys_v1.txt")

            // 检查所有模型文件是否存在
            if (!detModel.exists() || !recModel.exists() || !clsModel.exists() || !labelFile.exists()) {
                // 尝试从 assets 复制
                copyModelFilesFromAssets()
            }

            // 再次检查
            if (!detModel.exists() || !recModel.exists() || !clsModel.exists() || !labelFile.exists()) {
                Log.w(TAG, "Paddle OCR 模型文件不完整")
                return null
            }

            val config = OCRConfig(
                detModelPath = detModel.absolutePath,
                recModelPath = recModel.absolutePath,
                clsModelPath = clsModel.absolutePath,
                labelPath = labelFile.absolutePath,
                cpuThreadNum = minOf(Runtime.getRuntime().availableProcessors(), 4)
            )

            // 注意: OCREngine 需要 PaddleLitePredictor 的 native 方法
            // 当 .so 存在但 native 方法未注册时，这里会失败
            // 这是预期的 - 我们会降级到 ML Kit
            OCREngine(config)
        } catch (e: Exception) {
            Log.w(TAG, "创建 Paddle Lite 引擎失败: ${e.message}")
            null
        }
    }

    /**
     * 从 assets 复制模型文件
     */
    private fun copyModelFilesFromAssets() {
        val modelDir = File(context.filesDir, MODEL_DIR)
        if (!modelDir.exists()) modelDir.mkdirs()

        val files = listOf(
            "ch_PP-OCRv4_mobile_det.nb",
            "ch_PP-OCRv4_mobile_rec.nb",
            "ch_PP-OCRv4_mobile_cls.nb",
            "ppocr_keys_v1.txt"
        )

        for (filename in files) {
            val destFile = File(modelDir, filename)
            if (!destFile.exists() || destFile.length() == 0L) {
                try {
                    context.assets.open("models/$filename").use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "复制模型: $filename")
                } catch (e: Exception) {
                    Log.w(TAG, "复制模型失败: $filename")
                }
            }
        }
    }
}
