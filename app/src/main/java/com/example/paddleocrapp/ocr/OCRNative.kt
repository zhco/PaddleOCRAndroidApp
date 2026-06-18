package com.example.paddleocrapp.ocr

import android.graphics.Bitmap

/**
 * PaddleOCR Native JNI 接口封装
 * 
 * 注意：完整版需要 Paddle Lite 的 .so 库文件。
 * 请参考 README.md 下载模型和库文件后恢复 JNI 调用。
 */
object OCRNative {

    // 完整版请取消以下注释（需要 libpaddle_light_api_shared.so 和 libocr_native.so）:
    // init {
    //     System.loadLibrary("paddle_light_api_shared")
    //     System.loadLibrary("ocr_native")
    // }

    /**
     * 初始化 OCR 引擎
     */
    fun initOCR(
        detModel: String,
        recModel: String,
        clsModel: String,
        labelFile: String,
        threadNum: Int,
        useOpenCL: Boolean
    ): Boolean {
        // TODO: 替换为 native 调用: return nativeInitOCR(detModel, recModel, clsModel, labelFile, threadNum, useOpenCL)
        return true
    }

    /**
     * 识别图片中的文字
     */
    fun recognizeImage(bitmap: Bitmap): String {
        // TODO: 替换为 native 调用: return nativeRecognizeImage(bitmap)
        return "[模拟] 图片尺寸: ${bitmap.width}x${bitmap.height}\n请下载 Paddle Lite 库文件后启用真实 OCR 识别"
    }

    /**
     * 释放 OCR 资源
     */
    fun releaseOCR() {
        // TODO: 替换为 native 调用: nativeReleaseOCR()
    }
}
