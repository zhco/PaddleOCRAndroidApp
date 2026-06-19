package com.example.paddleocrapp.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.paddleocrapp.model.PageData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件导出工具类
 */
object FileExporter {

    private const val MIME_TYPE_TEXT = "text/plain"

    /**
     * 导出识别结果为文本文件（合并所有页面）
     *
     * @param context 上下文
     * @param pages 识别结果页面列表
     * @param includePageHeader 是否在每页前添加页码标题
     * @return 保存文件的 Uri，失败返回 null
     */
    suspend fun exportToText(
        context: Context,
        pages: List<PageData>,
        includePageHeader: Boolean = true
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val content = buildMergedText(pages, includePageHeader)
            val fileName = "OCR_Result_${getTimestamp()}.txt"
            saveTextFile(context, fileName, content)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 导出识别结果为纯文本（无页码标题，适合直接阅读）
     */
    suspend fun exportToPlainText(context: Context, pages: List<PageData>): Uri? =
        exportToText(context, pages, includePageHeader = false)

    /**
     * 构建合并后的文本内容
     */
    private fun buildMergedText(pages: List<PageData>, includePageHeader: Boolean): String {
        return buildString {
            // 文件头信息
            appendLine("=".repeat(50))
            appendLine("PaddleOCR 文字识别结果")
            appendLine("导出时间: ${getCurrentTime()}")
            appendLine("共 ${pages.size} 页")
            appendLine("=".repeat(50))
            appendLine()

            pages.forEachIndexed { index, page ->
                if (includePageHeader) {
                    appendLine("【${page.getFormattedPageNumber()}】")
                    appendLine("-".repeat(30))
                }

                val text = page.textContent.trim()
                if (text.isNotEmpty()) {
                    appendLine(text)
                } else if (page.errorMessage != null) {
                    appendLine("[识别失败: ${page.errorMessage}]")
                } else {
                    appendLine("[未识别到文字]")
                }

                // 页面之间空一行（最后一页不加）
                if (index < pages.size - 1) {
                    appendLine()
                }
            }
        }
    }

    /**
     * 分享识别结果
     */
    fun shareText(context: Context, pages: List<PageData>) {
        val content = buildMergedText(pages, includePageHeader = true)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE_TEXT
            putExtra(Intent.EXTRA_SUBJECT, "OCR 识别结果")
            putExtra(Intent.EXTRA_TEXT, content)
        }

        val chooser = Intent.createChooser(shareIntent, "分享识别结果")
        context.startActivity(chooser)
    }

    /**
     * 复制文字到剪贴板
     */
    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("OCR Result", text)
        clipboard.setPrimaryClip(clip)
    }

    /**
     * 复制所有页面的文字到剪贴板
     */
    fun copyAllToClipboard(context: Context, pages: List<PageData>) {
        val content = buildMergedText(pages, includePageHeader = true)
        copyToClipboard(context, content)
    }

    /**
     * 保存文本文件（自动适配 Android 版本）
     */
    private fun saveTextFile(context: Context, fileName: String, content: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(context, fileName, content)
        } else {
            saveToExternalStorage(context, fileName, content)
        }
    }

    /**
     * 保存到 MediaStore (Android 10+)
     */
    private fun saveToMediaStore(context: Context, fileName: String, content: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, MIME_TYPE_TEXT)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(content)
                }
            }
        }
        return uri
    }

    /**
     * 保存到外部存储 (Android 9 及以下)
     */
    private fun saveToExternalStorage(context: Context, fileName: String, content: String): Uri? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)

        FileWriter(file, Charsets.UTF_8).use { writer ->
            writer.write(content)
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun getTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }
}
