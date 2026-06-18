# PaddleOCR 图片转文字 Android App

基于百度飞桨 PaddleOCR 开源项目开发的 Android 图片转文字应用，支持批量导入拍摄的图片，按拍摄顺序生成一页一页的文字结果。

## 功能特性

- **批量导入图片**：支持从相册批量选择多张图片
- **按拍摄顺序排序**：自动读取图片 EXIF 拍摄时间，按拍摄先后顺序排列
- **手动拖拽排序**：支持长按拖拽调整图片顺序
- **分页展示结果**：每张图片对应一页，左右滑动查看
- **OCR 文字识别**：基于 PaddleOCR 的离线文字识别
- **导出分享**：支持保存为文本文件、分享、复制到剪贴板

## 技术架构

| 层级 | 技术 |
|------|------|
| OCR 引擎 | PaddleOCR + Paddle-Lite |
| 开发语言 | Kotlin |
| UI 框架 | Android Material Design 3 |
| 架构模式 | MVVM |
| 异步处理 | Kotlin Coroutines + Flow |
| 图片加载 | Glide |

## 项目结构

```
PaddleOCRAndroidApp/
├── app/
│   ├── src/main/
│   │   ├── cpp/                    # JNI Native 层
│   │   │   ├── CMakeLists.txt
│   │   │   └── ocr_native.cpp     # Paddle-Lite 推理封装
│   │   ├── java/com/example/paddleocrapp/
│   │   │   ├── ocr/
│   │   │   │   ├── OCRNative.kt   # JNI 接口
│   │   │   │   └── OCRManager.kt  # OCR 管理器
│   │   │   ├── model/
│   │   │   │   ├── ImageItem.kt   # 图片数据模型
│   │   │   │   └── PageData.kt    # 分页数据模型
│   │   │   ├── adapter/
│   │   │   │   ├── ImageGridAdapter.kt
│   │   │   │   └── PageAdapter.kt
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── TextResultActivity.kt
│   │   │   │   ├── BatchImportActivity.kt
│   │   │   │   └── SettingsActivity.kt
│   │   │   ├── viewmodel/
│   │   │   │   ├── MainViewModel.kt
│   │   │   │   └── TextResultViewModel.kt
│   │   │   └── utils/
│   │   │       ├── ImagePicker.kt
│   │   │       ├── BitmapUtils.kt
│   │   │       ├── PermissionHelper.kt
│   │   │       └── FileExporter.kt
│   │   ├── res/                    # 布局和资源文件
│   │   └── assets/models/          # 模型文件目录
│   └── build.gradle
├── build.gradle
└── settings.gradle
```

## 环境要求

- Android Studio Arctic Fox (2020.3.1) 或更高版本
- Android SDK 24+
- NDK r21+
- CMake 3.10.2+

## 模型准备

1. 下载 PaddleOCR 预训练模型：
   - 文本检测模型：`ch_PP-OCRv4_det_slim_infer`
   - 文本识别模型：`ch_PP-OCRv4_rec_slim_infer`
   - 方向分类模型：`ch_ppocr_mobile_v2.0_cls_slim_infer`

2. 使用 paddle_lite_opt 工具转换为 .nb 格式：
```bash
pip install paddlelite

paddle_lite_opt \
  --model_file=./ch_PP-OCRv4_det_slim_infer/inference.pdmodel \
  --param_file=./ch_PP-OCRv4_det_slim_infer/inference.pdiparams \
  --optimize_out=./ch_PP-OCRv4_det_slim_opt \
  --valid_targets=arm \
  --optimize_out_type=naive_buffer
```

3. 将转换后的 .nb 模型文件放入 `app/src/main/assets/models/` 目录

4. 下载标签文件 `ppocr_keys_v1.txt` 放入同一目录

## 编译运行

1. 克隆项目并导入 Android Studio
2. 同步 Gradle 依赖
3. 连接 Android 设备（arm64-v8a 或 armeabi-v7a）
4. 点击 Run 运行

## 使用说明

1. 打开应用，点击右下角 **+** 按钮批量导入图片
2. 图片会自动按拍摄时间排序，也可手动拖拽调整顺序
3. 点击 **开始识别** 按钮进行 OCR 识别
4. 识别结果按页展示，左右滑动切换页面
5. 支持保存、分享、复制识别结果

## 注意事项

- 首次运行需要加载模型文件，可能需要几秒钟
- 模型文件较大，建议仅在 WiFi 环境下下载
- 识别精度受图片质量影响，建议使用清晰的拍摄图片

## 参考资源

- [PaddleOCR 官方文档](https://www.paddleocr.ai/)
- [Paddle-Lite 文档](https://paddle-lite.readthedocs.io/)
- [PaddleOCR GitHub](https://github.com/PaddlePaddle/PaddleOCR)
