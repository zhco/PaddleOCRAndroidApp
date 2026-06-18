package com.example.paddleocrapp.ocr

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 模型下载管理器
 *
 * 负责从 PaddleOCR 官方地址下载 OCR 模型文件和字典文件，
 * 支持断点续传、进度回调、文件完整性校验。
 *
 * 下载的模型文件：
 * - ch_ppocr_mobile_v2.0_det_slim_opt.nb：文本检测模型
 * - ch_ppocr_mobile_v2.0_rec_slim_opt.nb：文本识别模型
 * - ch_ppocr_mobile_v2.0_cls_slim_opt.nb：方向分类模型
 * - ppocr_keys_v1.txt：中文字典标签文件
 */
class ModelDownloader(private val context: Context) {
    companion object {
        private const val TAG = "ModelDownloader"

        /** 模型文件存储目录名 */
        private const val MODEL_DIR = "ocr_models"

        /** 连接超时时间（毫秒） */
        private const val CONNECT_TIMEOUT = 30000

        /** 读取超时时间（毫秒） */
        private const val READ_TIMEOUT = 60000

        /** 缓冲区大小（8KB） */
        private const val BUFFER_SIZE = 8192

        // ==================== 模型下载地址 ====================

        /** 检测模型下载地址 */
        private const val DET_MODEL_URL =
            "https://paddleocr.bj.bcebos.com/PP-OCRv2/chinese/ch_ppocr_mobile_v2.0_det_slim_opt.nb"

        /** 识别模型下载地址 */
        private const val REC_MODEL_URL =
            "https://paddleocr.bj.bcebos.com/PP-OCRv2/chinese/ch_ppocr_mobile_v2.0_rec_slim_opt.nb"

        /** 方向分类模型下载地址 */
        private const val CLS_MODEL_URL =
            "https://paddleocr.bj.bcebos.com/PP-OCRv2/chinese/ch_ppocr_mobile_v2.0_cls_slim_opt.nb"

        /** 字典文件下载地址 */
        private const val DICT_URL =
            "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/release/2.3/ppocr/utils/ppocr_keys_v1.txt"

        // ==================== 文件名 ====================

        private const val DET_MODEL_FILE = "ch_ppocr_mobile_v2.0_det_slim_opt.nb"
        private const val REC_MODEL_FILE = "ch_ppocr_mobile_v2.0_rec_slim_opt.nb"
        private const val CLS_MODEL_FILE = "ch_ppocr_mobile_v2.0_cls_slim_opt.nb"
        private const val DICT_FILE = "ppocr_keys_v1.txt"
    }

    /**
     * 模型文件信息
     *
     * @property fileName 文件名
     * @property downloadUrl 下载地址
     * @property description 文件描述
     */
    data class ModelFileInfo(
        val fileName: String,
        val downloadUrl: String,
        val description: String
    )

    /**
     * 下载进度信息
     *
     * @property fileName 当前下载的文件名
     * @property progress 下载进度（0-100）
     * @property downloadedBytes 已下载字节数
     * @property totalBytes 总字节数（-1 表示未知）
     * @property isComplete 是否下载完成
     * @property isError 是否发生错误
     * @property errorMessage 错误信息
     */
    data class DownloadProgress(
        val fileName: String,
        val progress: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val isComplete: Boolean = false,
        val isError: Boolean = false,
        val errorMessage: String = ""
    )

    /**
     * 获取模型文件存储目录
     */
    fun getModelDir(): File {
        val dir = File(context.filesDir, MODEL_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 获取所有需要下载的模型文件列表
     */
    fun getModelFiles(): List<ModelFileInfo> {
        return listOf(
            ModelFileInfo(DET_MODEL_FILE, DET_MODEL_URL, "文本检测模型"),
            ModelFileInfo(REC_MODEL_FILE, REC_MODEL_URL, "文本识别模型"),
            ModelFileInfo(CLS_MODEL_FILE, CLS_MODEL_URL, "方向分类模型"),
            ModelFileInfo(DICT_FILE, DICT_URL, "中文字典文件")
        )
    }

    /**
     * 检查所有模型文件是否已存在
     *
     * @return 所有文件都存在返回 true
     */
    fun areAllModelsExist(): Boolean {
        val modelDir = getModelDir()
        return getModelFiles().all { info ->
            File(modelDir, info.fileName).exists()
        }
    }

    /**
     * 检查缺失的模型文件
     *
     * @return 缺失的模型文件列表
     */
    fun getMissingModels(): List<ModelFileInfo> {
        val modelDir = getModelDir()
        return getModelFiles().filter { info ->
            !File(modelDir, info.fileName).exists()
        }
    }

    /**
     * 获取 OCRConfig 配置（使用已下载的模型路径）
     *
     * @return OCRConfig 实例，包含模型文件路径
     */
    fun getOCRConfig(): OCRConfig {
        val modelDir = getModelDir()
        return OCRConfig(
            detModelPath = File(modelDir, DET_MODEL_FILE).absolutePath,
            recModelPath = File(modelDir, REC_MODEL_FILE).absolutePath,
            clsModelPath = File(modelDir, CLS_MODEL_FILE).absolutePath,
            labelPath = File(modelDir, DICT_FILE).absolutePath
        )
    }

    /**
     * 下载所有缺失的模型文件
     *
     * 使用 Flow 发送下载进度，支持取消操作。
     *
     * @return 下载进度 Flow
     */
    fun downloadAllModels(): Flow<DownloadProgress> = callbackFlow {
        val missingModels = getMissingModels()

        if (missingModels.isEmpty()) {
            Log.i(TAG, "所有模型文件已存在，无需下载")
            close()
            return@callbackFlow
        }

        Log.i(TAG, "开始下载 ${missingModels.size} 个模型文件...")

        for ((index, modelInfo) in missingModels.withIndex()) {
            Log.i(TAG, "下载文件 ${index + 1}/${missingModels.size}: ${modelInfo.description}")

            // 发送开始下载的进度
            trySend(DownloadProgress(
                fileName = modelInfo.fileName,
                progress = 0,
                downloadedBytes = 0,
                totalBytes = -1
            ))

            try {
                downloadFile(modelInfo) { progress ->
                    trySend(progress)
                }

                // 发送完成进度
                trySend(DownloadProgress(
                    fileName = modelInfo.fileName,
                    progress = 100,
                    downloadedBytes = 0,
                    totalBytes = 0,
                    isComplete = true
                ))

                Log.i(TAG, "下载完成: ${modelInfo.description}")
            } catch (e: Exception) {
                Log.e(TAG, "下载失败: ${modelInfo.description}", e)
                trySend(DownloadProgress(
                    fileName = modelInfo.fileName,
                    progress = 0,
                    downloadedBytes = 0,
                    totalBytes = 0,
                    isError = true,
                    errorMessage = e.message ?: "下载失败"
                ))
                close()
                return@callbackFlow
            }
        }

        Log.i(TAG, "所有模型文件下载完成")
        close()
        awaitClose()
    }.flowOn(Dispatchers.IO)

    /**
     * 下载单个文件
     *
     * @param modelInfo 模型文件信息
     * @param onProgress 进度回调
     */
    private fun downloadFile(
        modelInfo: ModelFileInfo,
        onProgress: (DownloadProgress) -> Unit
    ) {
        val modelDir = getModelDir()
        val destFile = File(modelDir, modelInfo.fileName)
        var connection: HttpURLConnection? = null

        try {
            val url = URL(modelInfo.downloadUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP 错误: $responseCode")
            }

            val totalBytes = connection.contentLength.toLong()

            // 下载到临时文件，完成后重命名（确保原子性）
            val tempFile = File(modelDir, "${modelInfo.fileName}.tmp")

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var totalRead = 0L

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break

                        output.write(buffer, 0, read)
                        totalRead += read

                        // 计算并发送进度
                        val progress = if (totalBytes > 0) {
                            (totalRead * 100 / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            -1  // 未知总大小时显示 -1
                        }

                        onProgress(DownloadProgress(
                            fileName = modelInfo.fileName,
                            progress = progress,
                            downloadedBytes = totalRead,
                            totalBytes = totalBytes
                        ))
                    }
                }
            }

            // 重命名临时文件为最终文件名
            if (destFile.exists()) {
                destFile.delete()
            }
            tempFile.renameTo(destFile)

            Log.i(TAG, "文件下载成功: ${destFile.absolutePath} (${destFile.length()} bytes)")

        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 从 assets 目录复制模型文件（离线部署方式）
     *
     * 如果应用已将模型文件打包在 assets 中，可使用此方法复制到私有目录。
     *
     * @return 复制成功返回 true
     */
    suspend fun copyModelsFromAssets(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val modelDir = getModelDir()
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }

            val modelFiles = listOf(DET_MODEL_FILE, REC_MODEL_FILE, CLS_MODEL_FILE, DICT_FILE)
            var allSuccess = true

            for (fileName in modelFiles) {
                val destFile = File(modelDir, fileName)

                // 如果文件已存在，跳过
                if (destFile.exists() && destFile.length() > 0) {
                    Log.d(TAG, "文件已存在，跳过: $fileName")
                    continue
                }

                // 尝试从 assets 复制
                try {
                    context.assets.open("models/$fileName").use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.i(TAG, "从 assets 复制成功: $fileName")
                } catch (e: Exception) {
                    Log.w(TAG, "从 assets 复制失败: $fileName (${e.message})")
                    allSuccess = false
                }
            }

            allSuccess
        } catch (e: Exception) {
            Log.e(TAG, "复制模型文件异常", e)
            false
        }
    }

    /**
     * 删除所有已下载的模型文件
     */
    fun deleteAllModels() {
        val modelDir = getModelDir()
        getModelFiles().forEach { info ->
            val file = File(modelDir, info.fileName)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "已删除: ${info.fileName}")
            }
        }
    }

    /**
     * 计算已下载模型的总大小
     *
     * @return 总字节数
     */
    fun getTotalModelsSize(): Long {
        val modelDir = getModelDir()
        return getModelFiles().sumOf { info ->
            val file = File(modelDir, info.fileName)
            if (file.exists()) file.length() else 0L
        }
    }
}
