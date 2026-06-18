package com.example.paddleocrapp.ocr

import android.graphics.Bitmap
import android.util.Log

/**
 * PaddleOCR Native JNI 接口封装（可选）
 *
 * 此接口保留用于 JNI 方式调用 Paddle Lite，作为 Java API 方式的备选方案。
 * 当前项目主要使用 Java API 方式（通过 PaddleLitePredictor + OCREngine），
 * 此 JNI 接口标记为可选，仅在需要更高性能时可启用。
 *
 * 启用方式：
 * 1. 编译 C++ native 库（参见 cpp/CMakeLists.txt 和 cpp/ocr_native.cpp）
 * 2. 将 libpaddle_light_api_shared.so 放入对应 ABI 目录
 * 3. 取消下方 System.loadLibrary 的注释
 * 4. 将 TODO 替换为实际的 native 调用
 */
object OCRNative {

    private const val TAG = "OCRNative"

    // 标记 JNI 是否可用
    private var isJniAvailable = false

    // 完整版请取消以下注释（需要 libpaddle_light_api_shared.so 和 libocr_native.so）:
    // init {
    //     try {
    //         System.loadLibrary("paddle_light_api_shared")
    //         System.loadLibrary("ocr_native")
    //         isJniAvailable = true
    //         Log.i(TAG, "JNI 库加载成功")
    //     } catch (e: UnsatisfiedLinkError) {
    //         Log.w(TAG, "JNI 库加载失败，将使用 Java API 方式", e)
    //         isJniAvailable = false
    //     }
    // }

    /**
     * 判断 JNI 接口是否可用
     *
     * @return JNI 库已加载返回 true
     */
    fun isAvailable(): Boolean = isJniAvailable

    /**
     * 初始化 OCR 引擎（JNI 方式）
     *
     * @param detModel 检测模型路径
     * @param recModel 识别模型路径
     * @param clsModel 方向分类模型路径
     * @param labelFile 字典文件路径
     * @param threadNum CPU 线程数
     * @param useOpenCL 是否使用 OpenCL
     * @return 初始化成功返回 true
     */
    fun initOCR(
        detModel: String,
        recModel: String,
        clsModel: String,
        labelFile: String,
        threadNum: Int,
        useOpenCL: Boolean
    ): Boolean {
        if (!isJniAvailable) {
            Log.w(TAG, "JNI 不可用，请使用 Java API 方式（OCREngine）")
            return false
        }
        // TODO: 替换为 native 调用: return nativeInitOCR(detModel, recModel, clsModel, labelFile, threadNum, useOpenCL)
        return false
    }

    /**
     * 识别图片中的文字（JNI 方式）
     *
     * @param bitmap 输入图像
     * @return 识别出的文本
     */
    fun recognizeImage(bitmap: Bitmap): String {
        if (!isJniAvailable) {
            Log.w(TAG, "JNI 不可用，请使用 Java API 方式（OCREngine）")
            return ""
        }
        // TODO: 替换为 native 调用: return nativeRecognizeImage(bitmap)
        return ""
    }

    /**
     * 释放 OCR 资源（JNI 方式）
     */
    fun releaseOCR() {
        if (!isJniAvailable) return
        // TODO: 替换为 native 调用: nativeReleaseOCR()
    }
}
