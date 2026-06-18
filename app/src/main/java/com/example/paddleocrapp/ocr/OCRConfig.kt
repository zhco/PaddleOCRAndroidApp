package com.example.paddleocrapp.ocr

/**
 * OCR 配置类
 *
 * 包含 PaddleOCR 引擎运行所需的所有参数配置，包括：
 * - 检测模型参数（DB 文本检测算法）
 * - 方向分类器参数
 * - 识别模型参数（CRNN + CTC）
 * - 运行时参数（线程数、模型路径等）
 */
data class OCRConfig(
    // ==================== 检测模型参数 ====================
    /** 检测时图像长边最大长度，超过此值会等比缩放 */
    val maxSideLen: Int = 960,

    /** DB 二值化阈值，用于从概率图生成二值文本区域图 */
    val detDbThresh: Float = 0.3f,

    /** DB 文本框阈值，过滤得分低于此阈值的文本框 */
    val detDbBoxThresh: Float = 0.5f,

    /** DB 扩展系数（Vatti clipping 算法），用于扩展文本框使其更完整地包含文字 */
    val detDbUnclipRatio: Float = 1.6f,

    /** 是否对二值化结果进行膨胀操作，可帮助连接断裂的文本 */
    val detDbUseDilate: Boolean = false,

    // ==================== 方向分类器参数 ====================
    /** 是否启用方向分类器（判断文本是否需要旋转180度） */
    val useDirectionClassify: Boolean = true,

    /** 方向分类阈值，置信度高于此值才执行旋转 */
    val clsThresh: Float = 0.9f,

    // ==================== 识别模型参数 ====================
    /** 识别模型输入图像高度（固定值，CRNN 要求） */
    val recImageHeight: Int = 32,

    /** 识别模型输入图像宽度（固定值，CRNN 要求） */
    val recImageWidth: Int = 320,

    // ==================== 运行时参数 ====================
    /** CPU 推理线程数 */
    val cpuThreadNum: Int = 4,

    /** 检测模型文件路径（.nb 格式） */
    val detModelPath: String = "",

    /** 识别模型文件路径（.nb 格式） */
    val recModelPath: String = "",

    /** 方向分类模型文件路径（.nb 格式） */
    val clsModelPath: String = "",

    /** 字典标签文件路径（ppocr_keys_v1.txt） */
    val labelPath: String = ""
)
