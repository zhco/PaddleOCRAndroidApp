package com.example.paddleocrapp.ocr

import android.graphics.Bitmap

/**
 * PaddleOCR Native JNI 接口封装
 */
object OCRNative {

    init {
        System.loadLibrary("paddle_light_api_shared")
        System.loadLibrary("ocr_native")
    }

    /**
     * 初始化 OCR 引擎
     */
    external fun initOCR(
        detModel: String,
        recModel: String,
        clsModel: String,
        labelFile: String,
        threadNum: Int,
        useOpenCL: Boolean
    ): Boolean

    /**
     * 识别图片中的文字
     */
    external fun recognizeImage(bitmap: Bitmap): String

    /**
     * 释放 OCR 资源
     */
    external fun releaseOCR()
}
