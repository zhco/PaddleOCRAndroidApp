package com.example.paddleocrapp.ocr

import android.util.Log

/**
 * Paddle Lite 预测器封装类
 *
 * 通过 JNI 调用 Paddle Lite C++ API，提供统一的模型加载和推理接口。
 * 支持 CPU 和 OpenCL GPU 推理模式。
 *
 * 集成方式：
 * 1. 下载 Paddle Lite 预编译库 (.so 文件)
 *    https://github.com/PaddlePaddle/Paddle-Lite/releases
 * 2. 将 libpaddle_light_api_shared.so 放入 app/libs/arm64-v8a/
 * 3. 将 libocr_jni.so (由 CMake 编译生成) 放入 app/libs/arm64-v8a/
 * 4. 在 app/build.gradle 中配置 jniLibs 来源目录
 *
 * 使用方式：
 * 1. 调用 loadModel() 加载 .nb 模型文件
 * 2. 调用 run() 执行推理
 * 3. 使用完毕后调用 release() 释放资源
 */
class PaddleLitePredictor {

    companion object {
        private const val TAG = "PaddleLitePredictor"

        init {
            try {
                System.loadLibrary("paddle_light_api_shared")
                System.loadLibrary("ocr_jni")
                Log.i(TAG, "Paddle Lite native libraries loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Paddle Lite native libraries not available: ${e.message}")
                Log.w(TAG, "OCR engine will run in simulation mode")
                Log.w(TAG, "To enable real OCR, download .so files to app/libs/arm64-v8a/")
            }
        }
    }

    /** Native 预测器句柄 */
    private var nativeHandle: Long = 0

    /** 模型是否已加载 */
    private var isLoaded = false

    /** 是否有 native 库可用 */
    private var hasNativeLib = false

    init {
        hasNativeLib = checkNativeAvailable()
    }

    /**
     * 检查 native 库是否可用
     */
    private external fun checkNativeAvailable(): Boolean

    /**
     * Native: 创建预测器
     */
    private external fun nativeCreate(
        modelPath: String,
        threadNum: Int,
        useOpenCL: Boolean
    ): Long

    /**
     * Native: 执行推理
     */
    private external fun nativeRun(
        handle: Long,
        inputData: FloatArray,
        shape: LongArray
    ): FloatArray

    /**
     * Native: 获取输出形状
     */
    private external fun nativeGetOutputShape(handle: Long): LongArray

    /**
     * Native: 释放预测器
     */
    private external fun nativeRelease(handle: Long)

    /**
     * 加载 Paddle Lite 模型
     *
     * @param modelPath 模型文件路径（.nb 格式）
     * @param threadNum CPU 推理线程数，默认为 4
     * @param useOpenCL 是否使用 OpenCL GPU 加速
     * @return 加载成功返回 true
     */
    fun loadModel(modelPath: String, threadNum: Int = 4, useOpenCL: Boolean = false): Boolean {
        if (!hasNativeLib) {
            Log.w(TAG, "Native library not available, cannot load model")
            return false
        }

        return try {
            Log.i(TAG, "Loading model: $modelPath, threads=$threadNum, opencl=$useOpenCL")
            nativeHandle = nativeCreate(modelPath, threadNum, useOpenCL)
            isLoaded = nativeHandle != 0L
            if (isLoaded) {
                Log.i(TAG, "Model loaded successfully (handle=$nativeHandle)")
            } else {
                Log.e(TAG, "Failed to create predictor")
            }
            isLoaded
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: $modelPath", e)
            isLoaded = false
            false
        }
    }

    /**
     * 执行模型推理
     *
     * @param inputData 输入数据（float 数组），NCHW 格式
     * @param shape 输入张量形状，如 longArrayOf(1, 3, 640, 640)
     * @return 推理输出数据
     */
    fun run(inputData: FloatArray, shape: LongArray): FloatArray {
        if (!isLoaded || nativeHandle == 0L) {
            Log.e(TAG, "Predictor not initialized")
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
            try {
                nativeRelease(nativeHandle)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to release predictor", e)
            }
            nativeHandle = 0L
        }
        isLoaded = false
    }

    fun isReady(): Boolean = isLoaded && nativeHandle != 0L

    fun hasNativeLibrary(): Boolean = hasNativeLib
}
