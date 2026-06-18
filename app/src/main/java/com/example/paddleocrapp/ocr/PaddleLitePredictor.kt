package com.example.paddleocrapp.ocr

import android.util.Log
import paddle.lite.MobileConfig
import paddle.lite.OpenclPrecision
import paddle.lite.OpenclTune
import paddle.lite.PaddlePredictor
import paddle.lite.PowerMode
import paddle.lite.Tensor

/**
 * Paddle Lite 预测器封装类
 *
 * 封装 Paddle Lite Java API，提供统一的模型加载和推理接口。
 * 支持 CPU 和 OpenCL GPU 推理模式。
 *
 * 使用方式：
 * 1. 调用 loadModel() 加载 .nb 模型文件
 * 2. 调用 run() 执行推理，传入输入数据和形状
 * 3. 使用完毕后调用 release() 释放资源
 */
class PaddleLitePredictor {
    companion object {
        private const val TAG = "PaddleLitePredictor"
    }

    /** Paddle Lite 预测器实例 */
    private var predictor: PaddlePredictor? = null

    /** 模型是否已加载 */
    private var isLoaded = false

    /**
     * 加载 Paddle Lite 模型
     *
     * @param modelPath 模型文件路径（.nb 格式，由 paddle_lite_opt 工具转换生成）
     * @param threadNum CPU 推理线程数，默认为 4
     * @param useOpenCL 是否使用 OpenCL GPU 加速，默认为 false（使用 CPU）
     * @return 加载成功返回 true，失败返回 false
     */
    fun loadModel(modelPath: String, threadNum: Int = 4, useOpenCL: Boolean = false): Boolean {
        return try {
            Log.i(TAG, "Loading model from: $modelPath, threads=$threadNum, opencl=$useOpenCL")

            val config = MobileConfig()
            // 设置模型文件路径
            config.setModelFromFile(modelPath)
            // 设置 CPU 线程数
            config.setThreads(threadNum)
            // 设置功耗模式：高性能模式
            config.setPowerMode(PowerMode.LITE_POWER_HIGH)

            // OpenCL GPU 加速配置
            if (useOpenCL) {
                config.setOpenclTune(OpenclTune.CL_TUNE_NONE)
                config.setOpenclPrecision(OpenclPrecision.CL_PRECISION_FP32)
            }

            // 创建预测器
            predictor = PaddlePredictor.createPaddlePredictor(config)
            isLoaded = predictor != null

            if (isLoaded) {
                Log.i(TAG, "Model loaded successfully")
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
     * @param inputData 输入数据（float 数组），数据格式为 NCHW
     * @param shape 输入张量的形状，例如 longArrayOf(1, 3, 640, 640) 表示 batch=1, channel=3, h=640, w=640
     * @return 推理输出的 float 数组，如果推理失败返回空数组
     */
    fun run(inputData: FloatArray, shape: LongArray): FloatArray {
        val pred = predictor
        if (pred == null) {
            Log.e(TAG, "Predictor not initialized")
            return floatArrayOf()
        }

        return try {
            // 获取输入张量
            val inputTensor: Tensor = pred.getInput(0)
            // 设置输入张量形状
            inputTensor.resize(shape)
            // 设置输入数据
            inputTensor.setData(inputData)

            // 执行推理
            pred.run()

            // 获取输出张量
            val outputTensor: Tensor = pred.getOutput(0)
            // 返回输出数据
            outputTensor.getFloatData()
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            floatArrayOf()
        }
    }

    /**
     * 获取输出张量的形状
     *
     * @return 输出张量的形状数组，如果预测器未初始化返回空数组
     */
    fun getOutputShape(): LongArray {
        return predictor?.getOutput(0)?.shape() ?: longArrayOf()
    }

    /**
     * 获取输入张量的形状
     *
     * @return 输入张量的形状数组，如果预测器未初始化返回空数组
     */
    fun getInputShape(): LongArray {
        return predictor?.getInput(0)?.shape() ?: longArrayOf()
    }

    /**
     * 获取指定索引的输出张量数据
     *
     * @param index 输出张量索引，默认为 0
     * @return 输出张量的 float 数据数组
     */
    fun getOutputData(index: Int = 0): FloatArray {
        return try {
            predictor?.getOutput(index)?.getFloatData() ?: floatArrayOf()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get output at index $index", e)
            floatArrayOf()
        }
    }

    /**
     * 获取指定索引的输出张量形状
     *
     * @param index 输出张量索引，默认为 0
     * @return 输出张量的形状数组
     */
    fun getOutputShape(index: Int = 0): LongArray {
        return try {
            predictor?.getOutput(index)?.shape() ?: longArrayOf()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get output shape at index $index", e)
            longArrayOf()
        }
    }

    /**
     * 释放预测器资源
     *
     * 在不再使用时调用此方法释放 native 内存
     */
    fun release() {
        predictor = null
        isLoaded = false
        Log.i(TAG, "Predictor released")
    }

    /**
     * 判断预测器是否已加载并可用
     *
     * @return 如果预测器已加载返回 true
     */
    fun isReady(): Boolean = isLoaded && predictor != null
}
