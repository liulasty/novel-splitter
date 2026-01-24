# Novel Splitter 使用说明

## 1. 项目简介
Novel Splitter 是一个本地化、高性能的小说文本切分工具，旨在将长篇小说文本转换为结构化的 Scene（场景）数据，为后续的向量化和 AI 分析提供高质量语料。

## 2. 快速开始

### 2.1 环境要求
- JDK 17+
- Maven 3.6+

### 2.2 构建项目
在项目根目录下执行：
```bash
mvn clean package -DskipTests
```
构建完成后，在 `application/target` 目录下会生成 `application-1.0.0-SNAPSHOT.jar`。

## 3. 运行方式

### 3.1 命令行模式 (CLI)
适用于批量处理或脚本调用。

```bash
java -jar application/target/application-1.0.0-SNAPSHOT.jar \
  --file="D:/books/my_novel.txt" \
  --version="v1-basic"
```

**参数说明**：
- `--file`: 小说文件的绝对路径（支持 .txt）。
- `--version`: 切分策略版本标识（用于区分不同的输出目录，默认为 v1-default）。

### 3.2 Web 服务模式
启动服务后，通过 REST API 触发任务。

**启动服务**：
```bash
java -jar application/target/application-1.0.0-SNAPSHOT.jar
```
（默认端口 8080，可在 `application.yml` 中修改）

**调用接口**：
```bash
curl -X POST http://localhost:8080/api/v1/split \
  -H "Content-Type: application/json" \
  -d '{
    "filePath": "D:/books/my_novel.txt",
    "version": "v2-web"
  }'
```

## 4. 配置说明
配置文件位置：`application/src/main/resources/application.yml`

```yaml
splitter:
  storage:
    root-path: "data/novel-storage" # 输出文件存储根目录
  rule:
    target-length: 1200  # 目标 Scene 字数
    min-length: 200      # 最小字数警告阈值
    max-length: 3000     # 最大字数警告阈值
```

## 5. 输出结果
运行成功后，结果将保存在 `data/novel-storage/scene/{小说名}/{版本号}/scenes.json`。

**JSON 结构示例**：
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "chapterTitle": "第一章 风起",
    "chapterIndex": 1,
    "text": "这里是场景的正文内容...",
    "wordCount": 1250
  }
]
```

## 6. 常见问题
- **乱码问题**：系统会自动尝试 UTF-8 和 GBK 编码，如果仍乱码，请手动将源文件转换为 UTF-8。
- **切分不准确**：请检查源文件的章节标题格式是否规范（如“第十章”），目前正则规则覆盖了大多数常见格式。
