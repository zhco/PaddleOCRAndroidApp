package com.example.paddleocrapp.ocr

import android.util.Log
import java.io.File

/**
 * Paddle Lite 动态加载器
 *
 * 通过 System.loadLibrary 加载 Paddle Lite .so 文件。
 * 当 .so 不存在时优雅降级。
 */
object PaddleLiteLoader {
    private const val TAG = "PaddleLiteLoader"
    private var loaded = false

    fun ensureLoaded(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("paddle_light_api_shared")
            loaded = true
            Log.i(TAG, "Paddle Lite library loaded successfully")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Paddle Lite .so not found: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load Paddle Lite: ${e.message}")
            false
        }
    }

    fun isAvailable(): Boolean = loaded

    /**
     * 检查 .so 文件是否存在（不实际加载）
     */
    fun checkSoFileExists(libDir: File): Boolean {
        val soFile = File(libDir, "libpaddle_light_api_shared.so")
        return soFile.exists()
    }
}
