package com.example.paddleocrapp.ocr

import android.util.Log

/**
 * Paddle Lite 预测器封装类
 *
 * 通过 JNI 调用 Paddle Lite C++ API，提供统一的模型加载和推理接口。
 * 当 native 库不存在时，自动降级为不可用状态，不会崩溃。
 */
class PaddleLitePredictor {

    companion object {
        private const val TAG = "PaddleLitePredictor"

        /** 标记 native 库是否成功加载 */
        private var nativeLibLoaded = false

        init {
            try {
                System.loadLibrary("paddle_light_api_shared")
                System.loadLibrary("ocr_jni")
                nativeLibLoaded = true
                Log.i(TAG, "Paddle Lite native libraries loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                nativeLibLoaded = false
                Log.w(TAG, "Native libraries not available: ${e.message}")
                Log.w(TAG, "OCR engine will run in simulation mode")
            } catch (e: Exception) {
                nativeLibLoaded = false
                Log.w(TAG, "Failed to load native libraries: ${e.message}")
            }
        }

        /** 检查 native 库是否可用 */
        fun isNativeLibAvailable(): Boolean = nativeLibLoaded
    }

    /** Native 预测器句柄 */
    private var nativeHandle: Long = 0

    /** 模型是否已加载 */
    private var isLoaded = false

    /**
     * 加载 Paddle Lite 模型
     */
    fun loadModel(modelPath: String, threadNum: Int = 4, useOpenCL: Boolean = false): Boolean {
        if (!nativeLibLoaded) {
            Log.w(TAG, "Cannot load model: native library not available")
            return false
        }

        return try {
            Log.i(TAG, "Loading model: $modelPath")
            nativeHandle = nativeCreate(modelPath, threadNum, useOpenCL)
            isLoaded = nativeHandle != 0L
            if (isLoaded) {
                Log.i(TAG, "Model loaded successfully")
            } else {
                Log.e(TAG, "Failed to create predictor")
            }
            isLoaded
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: $modelPath", e)
            false
        }
    }

    /**
     * 执行模型推理
     */
    fun run(inputData: FloatArray, shape: LongArray): FloatArray {
        if (!isLoaded || nativeHandle == 0L) {
            return floatArrayOf()
        }
        return try {
            nativeRun(nativeHandle, inputData, shape)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            floatArrayOf()
        }
    }

    /**
     * 获取输出张量的形状
     */
    fun getOutputShape(): LongArray {
        return if (nativeHandle != 0L) {
            try { nativeGetOutputShape(nativeHandle) } catch (e: Exception) { longArrayOf() }
        } else {
            longArrayOf()
        }
    }

    /**
     * 释放预测器资源
     */
    fun release() {
        if (nativeHandle != 0L) {
            try { nativeRelease(nativeHandle) } catch (_: Exception) {}
            nativeHandle = 0L
        }
        isLoaded = false
    }

    fun isReady(): Boolean = isLoaded && nativeHandle != 0L
    fun hasNativeLibrary(): Boolean = nativeLibLoaded

    // ==================== JNI Native Methods ====================

    private external fun nativeCreate(modelPath: String, threadNum: Int, useOpenCL: Boolean): Long
    private external fun nativeRun(handle: Long, inputData: FloatArray, shape: LongArray): FloatArray
    private external fun nativeGetOutputShape(handle: Long): LongArray
    private external fun nativeRelease(handle: Long)
}
