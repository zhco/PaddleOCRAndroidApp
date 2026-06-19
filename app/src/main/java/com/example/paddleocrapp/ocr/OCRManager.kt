package com.example.paddleocrapp.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * OCR 管理器 - 负责模型加载、下载和图片识别
 */
class OCRManager(private val context: Context) {

    companion object {
        private const val TAG = "OCRManager"
        private const val MODEL_DIR = "models"
        private const val DET_MODEL = "ch_ppocr_mobile_v2.0_det_slim_opt.nb"
        private const val REC_MODEL = "ch_ppocr_mobile_v2.0_rec_slim_opt.nb"
        private const val CLS_MODEL = "ch_ppocr_mobile_v2.0_cls_slim_opt.nb"
        private const val LABEL_FILE = "ppocr_keys_v1.txt"
    }

    /** OCR 引擎实例 */
    private var ocrEngine: OCREngine? = null

    /** 模型下载管理器 */
    private var modelDownloader: ModelDownloader? = null

    /** 引擎是否已初始化 */
    private var isInitialized = false

    /** 初始化失败的原因 */
    var initErrorMessage: String? = null
        private set

    /**
     * 初始化 OCR 引擎
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "开始初始化 OCR 管理器...")

            // 检查 native 库是否可用
            if (!PaddleLitePredictor.isNativeLibAvailable()) {
                initErrorMessage = "OCR 推理库未安装。\n\n请在设置中下载 OCR 模型和推理库，或联系开发者获取安装包。"
                Log.w(TAG, "Native library not available, OCR engine cannot initialize")
                return@withContext false
            }

            // 第一步：确保模型文件可用
            ensureModelFiles()

            // 第二步：构建 OCRConfig
            val config = buildOCRConfig()

            // 第三步：创建并初始化 OCREngine
            val engine = OCREngine(config)
            val success = engine.initialize()

            if (success) {
                ocrEngine = engine
                isInitialized = true
                initErrorMessage = null
                Log.i(TAG, "OCR 管理器初始化成功")
            } else {
                initErrorMessage = "OCR 模型加载失败，请检查模型文件是否完整。"
                Log.e(TAG, "OCR 引擎初始化失败")
                engine.release()
            }

            isInitialized
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
        if (!isInitialized || ocrEngine == null) {
            return@withContext OCRResult.error("OCR 未初始化")
        }

        try {
            ocrEngine!!.recognize(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "识别失败", e)
            OCRResult.error(e.message ?: "识别失败")
        }
    }

    /**
     * 批量识别图片
     */
    suspend fun recognizeBatch(bitmaps: List<Bitmap>): List<OCRResult> =
        withContext(Dispatchers.Default) {
            bitmaps.mapIndexed { index, bitmap ->
                Log.d(TAG, "处理图片 ${index + 1}/${bitmaps.size}")
                recognize(bitmap)
            }
        }

    /**
     * 释放资源
     */
    fun release() {
        ocrEngine?.release()
        ocrEngine = null
        isInitialized = false
        Log.i(TAG, "OCR 管理器资源已释放")
    }

    /**
     * 获取模型下载进度 Flow
     */
    fun getDownloadProgressFlow(): Flow<ModelDownloader.DownloadProgress> {
        if (modelDownloader == null) {
            modelDownloader = ModelDownloader(context)
        }
        return modelDownloader!!.downloadAllModels()
    }

    /**
     * 检查模型文件是否已全部就绪
     */
    fun isModelsReady(): Boolean {
        if (modelDownloader == null) {
            modelDownloader = ModelDownloader(context)
        }
        return modelDownloader!!.areAllModelsExist()
    }

    fun isReady(): Boolean = isInitialized

    // ==================== 私有方法 ====================

    /**
     * 确保模型文件可用
     */
    private fun ensureModelFiles() {
        val modelDir = File(context.filesDir, MODEL_DIR)
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        val modelFiles = listOf(DET_MODEL, REC_MODEL, CLS_MODEL, LABEL_FILE)

        for (filename in modelFiles) {
            val destFile = File(modelDir, filename)
            if (!destFile.exists() || destFile.length() == 0L) {
                copyAssetFile("models/$filename", destFile)
            }
        }
    }

    /**
     * 构建 OCRConfig 配置
     */
    private fun buildOCRConfig(): OCRConfig {
        val modelDir = File(context.filesDir, MODEL_DIR)
        val threadNum = getOptimalThreadCount()

        return OCRConfig(
            detModelPath = File(modelDir, DET_MODEL).absolutePath,
            recModelPath = File(modelDir, REC_MODEL).absolutePath,
            clsModelPath = File(modelDir, CLS_MODEL).absolutePath,
            labelPath = File(modelDir, LABEL_FILE).absolutePath,
            cpuThreadNum = threadNum,
            maxSideLen = 960,
            detDbThresh = 0.3f,
            detDbBoxThresh = 0.5f,
            detDbUnclipRatio = 1.6f,
            detDbUseDilate = false,
            useDirectionClassify = true,
            clsThresh = 0.9f,
            recImageHeight = 32,
            recImageWidth = 320
        )
    }

    /**
     * 复制单个 asset 文件到目标位置
     */
    private fun copyAssetFile(assetPath: String, destFile: File) {
        try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "从 assets 复制: $assetPath -> ${destFile.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "从 assets 复制失败: $assetPath (${e.message})")
        }
    }

    /**
     * 获取最优线程数
     */
    private fun getOptimalThreadCount(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return minOf(cores, 4)
    }
}
