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
 *
 * 作为 OCR 引擎的高层封装，提供以下功能：
 * - 模型文件管理（从 assets 复制或从网络下载）
 * - OCR 引擎初始化和生命周期管理
 * - 图片识别接口（单张和批量）
 * - 下载进度回调
 *
 * 使用方式：
 * 1. 创建 OCRManager 实例
 * 2. 调用 initialize() 初始化（自动处理模型文件）
 * 3. 调用 recognize() 识别图片
 * 4. 使用完毕后调用 release() 释放资源
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
    private val modelDownloader = ModelDownloader(context)

    /** 引擎是否已初始化 */
    private var isInitialized = false

    /**
     * 初始化 OCR 引擎
     *
     * 初始化流程：
     * 1. 首先尝试从 assets 目录复制模型文件到私有目录
     * 2. 如果 assets 中没有，检查是否已下载
     * 3. 构建 OCRConfig 配置
     * 4. 创建并初始化 OCREngine
     *
     * @return 初始化成功返回 true
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "开始初始化 OCR 管理器...")

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
                Log.i(TAG, "OCR 管理器初始化成功")
            } else {
                Log.e(TAG, "OCR 引擎初始化失败")
                engine.release()
            }

            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "OCR 管理器初始化异常", e)
            false
        }
    }

    /**
     * 使用自定义配置初始化 OCR 引擎
     *
     * @param config 自定义 OCR 配置
     * @return 初始化成功返回 true
     */
    suspend fun initializeWithConfig(config: OCRConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "使用自定义配置初始化 OCR 引擎...")

            val engine = OCREngine(config)
            val success = engine.initialize()

            if (success) {
                // 释放旧引擎
                ocrEngine?.release()
                ocrEngine = engine
                isInitialized = true
                Log.i(TAG, "OCR 引擎初始化成功（自定义配置）")
            } else {
                Log.e(TAG, "OCR 引擎初始化失败（自定义配置）")
                engine.release()
            }

            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "OCR 引擎初始化异常", e)
            false
        }
    }

    /**
     * 识别单张图片
     *
     * @param bitmap 输入图像
     * @return OCR 识别结果
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
     *
     * @param bitmaps 图片列表
     * @return 识别结果列表
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
     *
     * @return 下载进度 Flow，可用于 UI 展示下载进度
     */
    fun getDownloadProgressFlow(): Flow<ModelDownloader.DownloadProgress> {
        return modelDownloader.downloadAllModels()
    }

    /**
     * 检查模型文件是否已全部就绪
     *
     * @return 模型文件全部存在返回 true
     */
    fun isModelsReady(): Boolean {
        return modelDownloader.areAllModelsExist()
    }

    /**
     * 获取缺失的模型文件列表
     *
     * @return 缺失的模型文件信息列表
     */
    fun getMissingModels(): List<ModelDownloader.ModelFileInfo> {
        return modelDownloader.getMissingModels()
    }

    /**
     * 获取已下载模型的总大小
     *
     * @return 总字节数
     */
    fun getTotalModelsSize(): Long {
        return modelDownloader.getTotalModelsSize()
    }

    /**
     * 删除所有已下载的模型文件
     */
    fun deleteAllModels() {
        modelDownloader.deleteAllModels()
        release()
    }

    fun isReady(): Boolean = isInitialized

    // ==================== 私有方法 ====================

    /**
     * 确保模型文件可用
     *
     * 优先从 assets 目录复制，如果 assets 中没有则检查是否已通过下载获取。
     */
    private fun ensureModelFiles() {
        val modelDir = File(context.filesDir, MODEL_DIR)
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        val modelFiles = listOf(DET_MODEL, REC_MODEL, CLS_MODEL, LABEL_FILE)
        var allExist = true

        for (filename in modelFiles) {
            val destFile = File(modelDir, filename)
            if (!destFile.exists() || destFile.length() == 0L) {
                allExist = false
                // 尝试从 assets 复制
                copyAssetFile("models/$filename", destFile)
            }
        }

        if (allExist) {
            Log.i(TAG, "所有模型文件已就绪")
        } else {
            Log.w(TAG, "部分模型文件缺失，请通过 ModelDownloader 下载")
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
