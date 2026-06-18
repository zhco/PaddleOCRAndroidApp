#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <memory>

// Paddle Lite headers
#include "paddle_api.h"

#define LOG_TAG "OCR_JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace paddle::lite_api;

// 全局标记：native 库是否可用
static bool g_native_available = true;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_paddleocrapp_ocr_PaddleLitePredictor_checkNativeAvailable(
    JNIEnv* env, jobject thiz) {
    return g_native_available ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_example_paddleocrapp_ocr_PaddleLitePredictor_nativeCreate(
    JNIEnv* env, jobject thiz,
    jstring model_path, jint thread_num, jboolean use_opencl) {

    const char* path = env->GetStringUTFChars(model_path, nullptr);
    std::string model_path_str(path);
    env->ReleaseStringUTFChars(model_path, path);

    try {
        MobileConfig config;
        config.set_model_from_file(model_path_str);
        config.set_threads(static_cast<int>(thread_num));
        config.set_power_mode(LITE_POWER_HIGH);

        if (use_opencl) {
            config.set_opencl_tune(CL_TUNE_NONE);
            config.set_opencl_precision(CL_PRECISION_FP32);
        }

        auto predictor = CreatePaddlePredictor<MobileConfig>(config);
        if (!predictor) {
            LOGE("Failed to create predictor for model: %s", model_path_str.c_str());
            return 0;
        }

        // 将指针转为 jlong 存储
        auto* pred_ptr = new std::shared_ptr<PaddlePredictor>(predictor);
        return reinterpret_cast<jlong>(pred_ptr);

    } catch (const std::exception& e) {
        LOGE("Exception creating predictor: %s", e.what());
        return 0;
    }
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_paddleocrapp_ocr_PaddleLitePredictor_nativeRun(
    JNIEnv* env, jobject thiz,
    jlong handle, jfloatArray input_data, jlongArray shape) {

    if (handle == 0) {
        LOGE("Invalid handle");
        return env->NewFloatArray(0);
    }

    auto* pred_ptr = reinterpret_cast<std::shared_ptr<PaddlePredictor>*>(handle);
    auto& predictor = *pred_ptr;

    try {
        // 获取输入数据
        jsize data_len = env->GetArrayLength(input_data);
        jfloat* data_ptr = env->GetFloatArrayElements(input_data, nullptr);

        // 获取 shape
        jsize shape_len = env->GetArrayLength(shape);
        jlong* shape_ptr = env->GetLongArrayElements(shape, nullptr);

        // 设置输入张量
        std::unique_ptr<Tensor> input_tensor(predictor->GetInput(0));
        std::vector<int64_t> input_shape(shape_len);
        for (int i = 0; i < shape_len; i++) {
            input_shape[i] = static_cast<int64_t>(shape_ptr[i]);
        }
        input_tensor->Resize(input_shape);

        auto* tensor_data = input_tensor->mutable_data<float>();
        int tensor_size = 1;
        for (int i = 0; i < shape_len; i++) {
            tensor_size *= static_cast<int>(shape_ptr[i]);
        }
        memcpy(tensor_data, data_ptr, tensor_size * sizeof(float));

        env->ReleaseFloatArrayElements(input_data, data_ptr, 0);
        env->ReleaseLongArrayElements(shape, shape_ptr, 0);

        // 执行推理
        predictor->Run();

        // 获取输出
        std::unique_ptr<const Tensor> output_tensor(predictor->GetOutput(0));
        auto output_shape = output_tensor->shape();
        const float* output_data = output_tensor->data<float>();

        int output_size = 1;
        for (size_t i = 0; i < output_shape.size(); i++) {
            output_size *= static_cast<int>(output_shape[i]);
        }

        jfloatArray result = env->NewFloatArray(output_size);
        env->SetFloatArrayRegion(result, 0, output_size, output_data);

        return result;

    } catch (const std::exception& e) {
        LOGE("Exception during inference: %s", e.what());
        return env->NewFloatArray(0);
    }
}

JNIEXPORT jlongArray JNICALL
Java_com_example_paddleocrapp_ocr_PaddleLitePredictor_nativeGetOutputShape(
    JNIEnv* env, jobject thiz, jlong handle) {

    if (handle == 0) {
        return env->NewLongArray(0);
    }

    auto* pred_ptr = reinterpret_cast<std::shared_ptr<PaddlePredictor>*>(handle);
    auto& predictor = *pred_ptr;

    try {
        std::unique_ptr<const Tensor> output_tensor(predictor->GetOutput(0));
        auto shape = output_tensor->shape();

        jlongArray result = env->NewLongArray(static_cast<jsize>(shape.size()));
        jlong* buf = new jlong[shape.size()];
        for (size_t i = 0; i < shape.size(); i++) {
            buf[i] = static_cast<jlong>(shape[i]);
        }
        env->SetLongArrayRegion(result, 0, static_cast<jsize>(shape.size()), buf);
        delete[] buf;

        return result;
    } catch (const std::exception& e) {
        LOGE("Exception getting output shape: %s", e.what());
        return env->NewLongArray(0);
    }
}

JNIEXPORT void JNICALL
Java_com_example_paddleocrapp_ocr_PaddleLitePredictor_nativeRelease(
    JNIEnv* env, jobject thiz, jlong handle) {

    if (handle != 0) {
        auto* pred_ptr = reinterpret_cast<std::shared_ptr<PaddlePredictor>*>(handle);
        delete pred_ptr;
        LOGD("Predictor released (handle=%ld)", handle);
    }
}

} // extern "C"
