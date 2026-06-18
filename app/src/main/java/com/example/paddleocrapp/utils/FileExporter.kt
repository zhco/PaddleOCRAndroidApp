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
     * 导出识别结果为文本文件
     */
    suspend fun exportToText(context: Context, pages: List<PageData>): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val content = buildString {
                    appendLine("PaddleOCR 识别结果")
                    appendLine("导出时间: ${getCurrentTime()}")
                    appendLine("共 ${pages.size} 页")
                    appendLine("=".repeat(50))
                    appendLine()

                    pages.forEach { page ->
                        appendLine(page.getFormattedPageNumber())
                        appendLine("-".repeat(30))
                        appendLine(page.textContent)
                        appendLine()
                    }
                }

                val fileName = "OCR_Result_${getTimestamp()}.txt"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ 使用 MediaStore
                    saveToMediaStore(context, fileName, content)
                } else {
                    // Android 9 及以下使用传统方式
                    saveToExternalStorage(context, fileName, content)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * 分享识别结果
     */
    fun shareText(context: Context, pages: List<PageData>) {
        val content = buildString {
            pages.forEach { page ->
                appendLine(page.getFormattedPageNumber())
                appendLine(page.textContent)
                appendLine()
            }
        }

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
                OutputStreamWriter(outputStream).use { writer ->
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

        FileWriter(file).use { writer ->
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
