# 模型文件目录

请将以下文件放入此目录：

## 必需文件

1. **ch_PP-OCRv4_det_slim_opt.nb** - 文本检测模型
2. **ch_PP-OCRv4_rec_slim_opt.nb** - 文本识别模型  
3. **ch_ppocr_mobile_v2.0_cls_slim_opt.nb** - 方向分类模型
4. **ppocr_keys_v1.txt** - 中文字典标签文件

## 模型下载与转换

### 1. 下载推理模型

从 PaddleOCR 官方仓库下载推理模型：
- https://github.com/PaddlePaddle/PaddleOCR/blob/release/2.7/doc/doc_ch/models_list.md

### 2. 安装 paddlelite 工具

```bash
pip install paddlelite==2.13rc0
```

### 3. 转换模型

```bash
# 检测模型
paddle_lite_opt \
  --model_file=./ch_PP-OCRv4_det_slim_infer/inference.pdmodel \
  --param_file=./ch_PP-OCRv4_det_slim_infer/inference.pdiparams \
  --optimize_out=./ch_PP-OCRv4_det_slim_opt \
  --valid_targets=arm \
  --optimize_out_type=naive_buffer

# 识别模型
paddle_lite_opt \
  --model_file=./ch_PP-OCRv4_rec_slim_infer/inference.pdmodel \
  --param_file=./ch_PP-OCRv4_rec_slim_infer/inference.pdiparams \
  --optimize_out=./ch_PP-OCRv4_rec_slim_opt \
  --valid_targets=arm \
  --optimize_out_type=naive_buffer

# 分类模型
paddle_lite_opt \
  --model_file=./ch_ppocr_mobile_v2.0_cls_slim_infer/inference.pdmodel \
  --param_file=./ch_ppocr_mobile_v2.0_cls_slim_infer/inference.pdiparams \
  --optimize_out=./ch_ppocr_mobile_v2.0_cls_slim_opt \
  --valid_targets=arm \
  --optimize_out_type=naive_buffer
```

### 4. 复制到项目

将生成的 `.nb` 文件和 `ppocr_keys_v1.txt` 复制到 `app/src/main/assets/models/` 目录下。

## 模型说明

| 模型 | 大小 | 用途 |
|------|------|------|
| PP-OCRv4 det slim | ~2.5M | 检测图片中的文本区域 |
| PP-OCRv4 rec slim | ~3.1M | 识别文本内容 |
| PP-OCRv2 cls slim | ~0.3M | 判断文本方向 |

## Paddle Lite 库

同时需要下载 Paddle Lite 预编译库：
- https://github.com/PaddlePaddle/Paddle-Lite/releases

将 `libpaddle_light_api_shared.so` 放入对应 ABI 目录：
- `app/libs/arm64-v8a/`
- `app/libs/armeabi-v7a/`
