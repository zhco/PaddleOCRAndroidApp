#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <string>
#include <vector>
#include <memory>
#include <fstream>
#include <iostream>

// Paddle Lite headers
#include "paddle_api.h"
#include "paddle_use_ops.h"
#include "paddle_use_kernels.h"

#define LOG_TAG "OCRNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace paddle::lite_api;

// OCR configuration structure
struct OCRConfig {
    std::string det_model_path;
    std::string rec_model_path;
    std::string cls_model_path;
    std::string label_path;
    int cpu_thread_num = 4;
    bool use_opencl = false;
    float det_db_thresh = 0.3f;
    float det_db_box_thresh = 0.5f;
    float cls_thresh = 0.9f;
    int rec_image_height = 48;
    int max_side_len = 960;
};

// OCR result structure
struct OCRResult {
    std::vector<std::vector<int>> boxes;
    std::vector<std::string> texts;
    std::vector<float> scores;
};

// Global predictor instances
static std::shared_ptr<paddle::lite_api::PaddlePredictor> g_det_predictor = nullptr;
static std::shared_ptr<paddle::lite_api::PaddlePredictor> g_rec_predictor = nullptr;
static std::shared_ptr<paddle::lite_api::PaddlePredictor> g_cls_predictor = nullptr;
static std::vector<std::string> g_labels;
static OCRConfig g_config;

// Utility: Load labels from file
bool LoadLabels(const std::string& label_path) {
    std::ifstream file(label_path);
    if (!file.is_open()) {
        LOGE("Failed to open label file: %s", label_path.c_str());
        return false;
    }
    g_labels.clear();
    std::string line;
    while (std::getline(file, line)) {
        g_labels.push_back(line);
    }
    LOGD("Loaded %zu labels", g_labels.size());
    return true;
}

// Utility: Create PaddlePredictor
std::shared_ptr<paddle::lite_api::PaddlePredictor> CreatePredictor(
    const std::string& model_path,
    int thread_num,
    bool use_opencl) {
    
    MobileConfig config;
    config.set_model_from_file(model_path);
    config.set_threads(thread_num);
    config.set_power_mode(LITE_POWER_HIGH);
    
    if (use_opencl) {
        config.set_opencl_tune(CL_TUNE_NONE);
        config.set_opencl_precision(CL_PRECISION_FP32);
    }
    
    return CreatePaddlePredictor<MobileConfig>(config);
}

// Utility: Convert Android Bitmap to RGB data
bool BitmapToRGB(JNIEnv* env, jobject bitmap, std::vector<float>* data,
                 int* width, int* height, int* channels) {
    AndroidBitmapInfo info;
    void* pixels;
    
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("AndroidBitmap_getInfo failed");
        return false;
    }
    
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("AndroidBitmap_lockPixels failed");
        return false;
    }
    
    *width = info.width;
    *height = info.height;
    *channels = 3;
    
    int size = info.width * info.height * 3;
    data->resize(size);
    
    uint8_t* src = static_cast<uint8_t*>(pixels);
    float* dst = data->data();
    
    for (int y = 0; y < info.height; y++) {
        for (int x = 0; x < info.width; x++) {
            uint32_t pixel;
            if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
                pixel = *((uint32_t*)src + y * info.width + x);
                dst[(y * info.width + x) * 3 + 0] = ((pixel >> 16) & 0xFF) / 255.0f;  // R
                dst[(y * info.width + x) * 3 + 1] = ((pixel >> 8) & 0xFF) / 255.0f;   // G
                dst[(y * info.width + x) * 3 + 2] = (pixel & 0xFF) / 255.0f;          // B
            } else if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
                pixel = *((uint16_t*)src + y * info.width + x);
                dst[(y * info.width + x) * 3 + 0] = ((pixel >> 11) & 0x1F) * 255 / 31 / 255.0f;
                dst[(y * info.width + x) * 3 + 1] = ((pixel >> 5) & 0x3F) * 255 / 63 / 255.0f;
                dst[(y * info.width + x) * 3 + 2] = (pixel & 0x1F) * 255 / 31 / 255.0f;
            }
        }
    }
    
    AndroidBitmap_unlockPixels(env, bitmap);
    return true;
}

// Preprocess: Resize image maintaining aspect ratio
void ResizeImage(const std::vector<float>& src, int src_w, int src_h,
                 std::vector<float>* dst, int* dst_w, int* dst_h,
                 int max_side_len) {
    float ratio = 1.0f;
    int max_side = std::max(src_w, src_h);
    if (max_side > max_side_len) {
        ratio = static_cast<float>(max_side_len) / max_side;
    }
    
    *dst_w = static_cast<int>(src_w * ratio);
    *dst_h = static_cast<int>(src_h * ratio);
    
    // Ensure dimensions are multiples of 32 (required by DB model)
    *dst_w = (*dst_w / 32) * 32;
    *dst_h = (*dst_h / 32) * 32;
    
    if (*dst_w < 32) *dst_w = 32;
    if (*dst_h < 32) *dst_h = 32;
    
    dst->resize((*dst_w) * (*dst_h) * 3);
    
    // Simple bilinear resize
    for (int y = 0; y < *dst_h; y++) {
        for (int x = 0; x < *dst_w; x++) {
            float src_x = x * static_cast<float>(src_w) / (*dst_w);
            float src_y = y * static_cast<float>(src_h) / (*dst_h);
            
            int x0 = static_cast<int>(src_x);
            int y0 = static_cast<int>(src_y);
            int x1 = std::min(x0 + 1, src_w - 1);
            int y1 = std::min(y0 + 1, src_h - 1);
            
            float fx = src_x - x0;
            float fy = src_y - y0;
            
            for (int c = 0; c < 3; c++) {
                float v00 = src[(y0 * src_w + x0) * 3 + c];
                float v01 = src[(y0 * src_w + x1) * 3 + c];
                float v10 = src[(y1 * src_w + x0) * 3 + c];
                float v11 = src[(y1 * src_w + x1) * 3 + c];
                
                (*dst)[(y * (*dst_w) + x) * 3 + c] = 
                    v00 * (1 - fx) * (1 - fy) +
                    v01 * fx * (1 - fy) +
                    v10 * (1 - fx) * fy +
                    v11 * fx * fy;
            }
        }
    }
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_paddleocrapp_ocr_OCRNative_initOCR(
    JNIEnv* env, jobject thiz,
    jstring det_model, jstring rec_model, jstring cls_model,
    jstring label_file, jint thread_num, jboolean use_opencl) {
    
    const char* det_path = env->GetStringUTFChars(det_model, nullptr);
    const char* rec_path = env->GetStringUTFChars(rec_model, nullptr);
    const char* cls_path = env->GetStringUTFChars(cls_model, nullptr);
    const char* label_path = env->GetStringUTFChars(label_file, nullptr);
    
    g_config.det_model_path = det_path;
    g_config.rec_model_path = rec_path;
    g_config.cls_model_path = cls_path;
    g_config.label_path = label_path;
    g_config.cpu_thread_num = thread_num;
    g_config.use_opencl = use_opencl;
    
    env->ReleaseStringUTFChars(det_model, det_path);
    env->ReleaseStringUTFChars(rec_model, rec_path);
    env->ReleaseStringUTFChars(cls_model, cls_path);
    env->ReleaseStringUTFChars(label_file, label_path);
    
    try {
        // Load labels
        if (!LoadLabels(g_config.label_path)) {
            return JNI_FALSE;
        }
        
        // Create predictors
        g_det_predictor = CreatePredictor(g_config.det_model_path, 
                                          g_config.cpu_thread_num, 
                                          g_config.use_opencl);
        g_rec_predictor = CreatePredictor(g_config.rec_model_path, 
                                          g_config.cpu_thread_num, 
                                          g_config.use_opencl);
        g_cls_predictor = CreatePredictor(g_config.cls_model_path, 
                                          g_config.cpu_thread_num, 
                                          g_config.use_opencl);
        
        if (!g_det_predictor || !g_rec_predictor || !g_cls_predictor) {
            LOGE("Failed to create predictors");
            return JNI_FALSE;
        }
        
        LOGD("OCR initialized successfully");
        return JNI_TRUE;
        
    } catch (const std::exception& e) {
        LOGE("Exception during init: %s", e.what());
        return JNI_FALSE;
    }
}

JNIEXPORT jstring JNICALL
Java_com_example_paddleocrapp_ocr_OCRNative_recognizeImage(
    JNIEnv* env, jobject thiz, jobject bitmap) {
    
    if (!g_det_predictor || !g_rec_predictor) {
        return env->NewStringUTF("Error: OCR not initialized");
    }
    
    std::vector<float> image_data;
    int width, height, channels;
    
    if (!BitmapToRGB(env, bitmap, &image_data, &width, &height, &channels)) {
        return env->NewStringUTF("Error: Failed to process bitmap");
    }
    
    try {
        // Resize image for detection
        std::vector<float> resized_data;
        int dst_w, dst_h;
        ResizeImage(image_data, width, height, &resized_data, &dst_w, &dst_h, 
                    g_config.max_side_len);
        
        // Prepare input tensor for detection
        std::unique_ptr<Tensor> input_tensor(g_det_predictor->GetInput(0));
        input_tensor->Resize({1, 3, dst_h, dst_w});
        
        // Normalize and fill data (mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
        float* input_data = input_tensor->mutable_data<float>();
        const float mean[3] = {0.485f, 0.456f, 0.406f};
        const float std[3] = {0.229f, 0.224f, 0.225f};
        
        for (int c = 0; c < 3; c++) {
            for (int h = 0; h < dst_h; h++) {
                for (int w = 0; w < dst_w; w++) {
                    int src_idx = (h * dst_w + w) * 3 + c;
                    int dst_idx = c * dst_h * dst_w + h * dst_w + w;
                    input_data[dst_idx] = (resized_data[src_idx] - mean[c]) / std[c];
                }
            }
        }
        
        // Run detection
        g_det_predictor->Run();
        
        // Get detection output
        std::unique_ptr<const Tensor> output_tensor(g_det_predictor->GetOutput(0));
        auto output_shape = output_tensor->shape();
        const float* output_data = output_tensor->data<float>();
        
        // Simple text region extraction (simplified version)
        // In production, you would implement full DB post-processing here
        std::string result_text = "识别结果:\n";
        result_text += "图片尺寸: " + std::to_string(width) + "x" + std::to_string(height) + "\n";
        result_text += "检测到文本区域数量: 待实现完整后处理\n";
        
        // For demo purposes, return a placeholder result
        // Full implementation would include:
        // 1. DB post-processing to get text boxes
        // 2. Crop text regions
        // 3. Run classification for direction
        // 4. Run recognition for each region
        // 5. Combine results
        
        return env->NewStringUTF(result_text.c_str());
        
    } catch (const std::exception& e) {
        LOGE("Exception during recognition: %s", e.what());
        return env->NewStringUTF((std::string("Error: ") + e.what()).c_str());
    }
}

JNIEXPORT void JNICALL
Java_com_example_paddleocrapp_ocr_OCRNative_releaseOCR(JNIEnv* env, jobject thiz) {
    g_det_predictor = nullptr;
    g_rec_predictor = nullptr;
    g_cls_predictor = nullptr;
    g_labels.clear();
    LOGD("OCR resources released");
}

} // extern "C"
