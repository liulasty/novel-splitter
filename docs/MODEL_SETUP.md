# Embedding 模型配置指南

本项目默认使用 **BGE-Small-ZH-v1.5** 模型生成文本向量。为了方便开箱即用，我们在 JAR 包中内置了一个模型版本。但为了灵活性（例如更新模型或减小 JAR 包体积），你可以配置使用外部的模型文件。

## 默认行为
如果不进行任何配置，应用会自动从 classpath (`src/main/resources/embedding/`) 加载内置的模型和词表。

## 使用外部模型

### 1. 下载模型
你可以从以下渠道下载 ONNX 版本的 BGE-Small-ZH-v1.5 模型：

*   **Hugging Face**: [BAAI/bge-small-zh-v1.5](https://huggingface.co/BAAI/bge-small-zh-v1.5) (请寻找 ONNX 版本或自行导出)
*   **ModelScope**: [BAAI/bge-small-zh-v1.5](https://modelscope.cn/models/BAAI/bge-small-zh-v1.5)

你需要确保拥有以下文件：
*   `model.onnx` (模型主文件)
*   `model.onnx_data` (权重文件，如果模型较大被拆分；通常 `model.onnx` 会引用它)
*   `vocab.txt` (Tokenizer 使用的词表文件)

### 2. 修改配置
修改你的 `application.yml` 文件，指定外部文件的绝对路径。

```yaml
embedding:
  onnx:
    # model.onnx 文件的绝对路径
    model-path: "D:/models/bge-small-zh-v1.5-onnx/model.onnx"
    # vocab.txt 文件的绝对路径
    vocab-path: "D:/models/bge-small-zh-v1.5-onnx/vocab.txt"
```

### 3. 验证
重启应用。查看日志，如果看到以下信息，说明配置生效：

```
INFO ... OnnxModelHolder : Using external ONNX model from: D:/models/bge-small-zh-v1.5-onnx/model.onnx
INFO ... Vocabulary      : Using external vocabulary from: D:/models/bge-small-zh-v1.5-onnx/vocab.txt
```
