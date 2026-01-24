# 一、项目目标与边界

## 1.1 项目目标

构建一个**完全离线、本地运行**的小说文本切分系统，用于：

- 将单部 5MB～百万字级中文小说
- 稳定、可复现地切分为 **Scene（语义场景单元）**
- 为后续：向量化、检索、问答、分析提供**长期可靠的结构化语料**

> 本项目**只解决“切分与结构化”问题**，不包含：
>
> - LLM 推理
> - 向量召回
> - 问答编排

这是整个系统中**最底层、最不可返工的一层**。

------

## 1.2 设计原则（非常重要）

1. **工程优先，不赌模型能力**
2. **切分结果必须可审计、可回滚、可重建**
3. **不引入数据库、不引入外部服务**
4. **所有中间产物都是“稳定文件”**

------

# 二、总体架构视图（逻辑层级）

```
novel-splitter
│
├─ application        ← Spring Boot 启动 & 任务编排
│
├─ domain             ← 核心业务模型（Scene / Chapter / Segment）
│
├─ splitter           ← 切分规则引擎（纯业务核心）
│
├─ repository         ← 本地文件存储抽象
│
├─ pipeline           ← 多阶段处理流水线
│
├─ validation         ← 切分质量校验
│
└─ infrastructure     ← JSON / 文件 / 配置实现
│
└─ novelDownload     ← 小说文件下载，为项目提供原始材料

```

> **注意**：
>
> - 没有 DAO / Entity / Mapper
> - Repository 不是数据库概念，而是“文件产物管理器”

------

# 三、模块拆解（逐层说明）

## 3.1 application 层（Spring Boot）

### 职责

- 提供 CLI / REST 触发入口
- 串联切分 Pipeline
- 不包含任何切分规则

### 示例结构

```
application
├─ NovelSplitApplication.java
├─ SplitCommandRunner.java
└─ SplitController.java（可选）

```

### 示例职责说明

- `SplitCommandRunner`
  - 接收小说路径
  - 选择切分策略版本
  - 启动流水线

------

## 3.2 domain 层（纯模型）

### 核心对象

```
domain
├─ RawParagraph
├─ SemanticSegment
├─ Scene
├─ Chapter
└─ SceneMetadata

```

### Scene 示例（核心对象）

```
public class Scene {
    String sceneId;
    String chapterTitle;
    int chapterIndex;
    int startParagraph;
    int endParagraph;
    String text;
    SceneMetadata metadata;
}

```

> domain 层：
>
> - **不依赖 Spring**
> - 不依赖 Jackson
> - 不关心存储形式

------

## 3.3 splitter 层（最关键）

### 职责

实现**可解释、规则驱动**的文本切分逻辑。

### 切分阶段划分

```
Raw Text
  ↓
物理段落切分
  ↓
语义段落合并（时间 / 视角 / 场景）
  ↓
Scene 构建

```

### 目录结构

```
splitter
├─ ParagraphSplitter
├─ SemanticSegmentBuilder
├─ SceneAssembler
├─ rule
│   ├─ TimeShiftRule
│   ├─ LocationShiftRule
│   ├─ POVShiftRule
│   └─ DialogueDensityRule

```

### 规则特点

- 全部是 **deterministic（确定性）**
- 不调用模型
- 同一输入 → 永远同一输出

------

## 3.4 pipeline 层（流水线编排）

### 职责

把 splitter 的“原子能力”串成**稳定流程**。

```
pipeline
├─ SplitPipeline
├─ PipelineContext
└─ PipelineStage

```

### Pipeline 示例

```
pipeline.run(
  loadRawText,
  splitParagraphs,
  buildSemanticSegments,
  assembleScenes,
  validateScenes,
  persistScenes
);

```

------

## 3.5 repository 层（本地文件存储）

### 关键认知

> Repository ≠ Database
>
> Repository = **“版本化文件产物管理”**

### 目录结构（真实落地）

```
novel-storage/
├─ raw/
│   └─ novel.txt
│
├─ scene/
│   ├─ v1-rule-basic/
│   │   └─ scenes.json
│   └─ v2-rule-enhanced/
│       └─ scenes.json
│
└─ meta/
    └─ split-report.json

```

### Repository 接口示例

```
public interface SceneRepository {
    void save(List<Scene> scenes);
    List<Scene> load();
}

```

实现：`JsonSceneRepository`

------

## 3.6 validation 层（质量控制）

### 职责

保证切分结果**不是“看天吃饭”**。

### 校验项

- Scene 字数分布是否异常
- 是否出现极短 / 极长 Scene
- 是否跨章节异常
- Scene 连续性是否断裂

```
validation
├─ SceneLengthValidator
├─ ChapterBoundaryValidator
└─ ContinuityValidator

```

------
## 3.6 novelDownload 层


### 1. 项目包结构

```
com.example.demo.util.novel/
├── config/                 # 配置相关
│   ├── DownloadConfig.java
│   └── RequestHeaders.java
├── model/                  # 数据模型
│   ├── Chapter.java
│   ├── Stats.java
│   └── DownloadResult.java
├── service/                # 业务逻辑
│   ├── NovelDownloaderService.java
│   ├── ContentExtractionService.java
│   └── FileGenerationService.java
├── util/                   # 工具类
│   ├── ContentCleaner.java
│   ├── TextProcessor.java
│   └── NetworkUtil.java
├── exception/              # 自定义异常
│   └── DownloadException.java
└── controller/             # 主控制器
    └── NovelDownloadController.java
```


### 2. 各模块详细设计

#### 2.1 配置模块 (`config`)

```java
// DownloadConfig.java
package com.example.demo.util.novel.config;

public class DownloadConfig {
    public static final int THREAD_COUNT = 3;
    public static final long DELAY_MS = 2000;
    public static final int TIMEOUT = 30000;
    public static final String OUTPUT_FILE_NAME = "_完整版.txt";
    public static final String PROGRESS_LOG_FILE = "download_progress.log";
}

// RequestHeaders.java
package com.example.demo.util.novel.config;

import java.util.Map;
import java.util.HashMap;

public class RequestHeaders {
    public static final Map<String, String> HEADERS = new HashMap<String, String>() {{
        put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        // ... 其他头部
    }};
}
```


#### 2.2 模型模块 (`model`)

```java
// Chapter.java
package com.example.demo.util.novel.model;

public class Chapter {
    private int index;
    private String title;
    private String url;
    private String content;
    private boolean success;

    // 构造函数、getter、setter 和 toString 方法
}

// Stats.java
package com.example.demo.util.novel.model;

public class Stats {
    private int total = 0;
    private int success = 0;
    private int failed = 0;
    
    // 统计相关方法
    public synchronized void addSuccess() { success++; }
    public synchronized void addFailed() { failed++; }
    public void print();
}
```


#### 2.3 服务模块 (`service`)

```java
// NovelDownloaderService.java
package com.example.demo.util.novel.service;

public class NovelDownloaderService {
    private ContentExtractionService contentExtractor;
    private FileGenerationService fileGenerator;
    
    public Stats downloadChapters(List<Chapter> chapters, int startIndex, int endIndex);
    private String downloadChapter(String url);
    private String tryDownload(String url);
}

// ContentExtractionService.java
package com.example.demo.util.novel.service;

public class ContentExtractionService {
    public String extractContent(Document doc);
    public String cleanContent(String html);
    private int countChinesePunctuation(String text);
}

// FileGenerationService.java
package com.example.demo.util.novel.service;

public class FileGenerationService {
    public String generateNovelFile(List<Chapter> chapters, String filename);
    public void saveFailedChapters(List<Chapter> chapters);
    private void createProgressFile(int total);
    private void updateProgress(int current, int total, String title, boolean success);
}
```


#### 2.4 工具模块 (`util`)

```java
// ContentCleaner.java
package com.example.demo.util.novel.util;

public class ContentCleaner {
    public static String removeAdsAndJunk(String text);
    public static String formatParagraphs(String text);
    public static String normalizeSpaces(String text);
}

// NetworkUtil.java
package com.example.demo.util.novel.util;

public class NetworkUtil {
    public static boolean testConnection(String url);
    public static Document fetchPage(String url);
}
```


### 3. 重构后的主控制类

```java
// NovelDownloadController.java
package com.example.demo.util.novel.controller;

public class NovelDownloadController {
    private NovelDownloaderService downloaderService;
    private FileGenerationService fileService;
    
    public static void main(String[] args) {
        NovelDownloadController controller = new NovelDownloadController();
        controller.runInteractiveDownload();
    }
    
    private void runInteractiveDownload() {
        // 主流程控制
    }
    
    private List<Chapter> loadChaptersFromFile(String filename) {
        // 从文件加载章节
    }
}
```


### 4. 优势分析

- **职责分离**：每个类都有单一职责，易于维护
- **可测试性**：各组件独立，便于单元测试
- **可扩展性**：新增功能不影响现有代码
- **可重用性**：服务类可在不同场景中复用
- **可维护性**：结构清晰，降低维护成本

### 5. 额外改进建议

1. 使用配置文件管理参数，而非硬编码

2. 添加日志框架（如SLF4J）

3. 实现配置的动态加载

4. 添加更完善的异常处理机制

5. 考虑使用Spring Boot框架实现依赖注入

### 6.基本需要下面这样如此的配置即可完成下载准备工作
```
  "www.77xs.cn": {
    "chapterPath":"/html/body/div[8]",
    "checkUrl":"https://www.77xs.cn/html/70/70159/index.html",
    "contentPath":"/html/body/div[6]/div[4]",
    "nextLoop":false,
    "nextLoopCnt":15
  },
  "www.wanben2.com": {
    "chapterPath": "/html/body/div[1]/div[5]/div/dl",
    "checkUrl": "https://www.wanben2.com/95276352/",
    "contentPath": "/html/body/div/div[4]/div/div[3]",
    "nextLoop": false,
    "nextLoopCnt": 15
  },
  "www.biqugezw.us": 
  {"chapterPath":"/html/body/section/div[4]/div[2]",
  "checkUrl":"https://www.biqugezw.us/book/346016/",
  "contentPath":"/html/body/section/div/article/div[2]",
  "nextLoop":false,
  "nextLoopCnt":15
  },
  "wap.maxshuku.com": {
    "chapterPath": "/html/body/div",
    "checkUrl": "http://wap.maxshuku.com/read/disitianzaicongbuxiangxingangtiehongliu/",
    "contentPath": "/html/body/div",
    "nextLoop": false,
    "nextLoopCnt": 15
 },
 "biquge.my": {
     "chapterPath":"/html/body/div[6]",
     "checkUrl":"https://biquge.my/p/d/15705",
     "contentPath":"/html/body/div[3]/div[6]/div[2]",
     "nextLoop":false,
     "nextLoopCnt":15
 },
 "m.cheyil.cc": {"chapterPath":"#clist","checkUrl":"https://m.cheyil.cc/wapbook/book/1183844/1/1/","contentPath":"#chaptercontent","nextLoop":false,"nextLoopCnt":15},
 "www.xinghuowxw.com":
    {
        "chapterPath": "/html/body/div[2]/section/div[3]/div/ul",
        "checkUrl": "https://www.xinghuowxw.com/book/B7KB.html",
        "contentPath": "/html/body/div[2]/main/section/article",
        "nextLoop":true,
        "nextUrlPath":"/html/body/div[2]/main/div/a[3]",
        "nextPathText":"下一页",
        "nextLoopCnt":15
    }
```

# 四、关键业务流程（一步到位）

## 4.1 切分完整流程

1. 读取 raw 小说文本
2. 按换行切为物理段落
3. 识别章节标题
4. 基于规则合并为语义段
5. 聚合为 Scene（500～1500 字）
6. 校验 Scene 合理性
7. 写入 JSON（版本化）

------

# 五、核心依赖（极简）

```
dependencies {
  implementation 'org.springframework.boot:spring-boot-starter'
  implementation 'com.fasterxml.jackson.core:jackson-databind'
  implementation 'org.apache.commons:commons-lang3'
}

```

> 没有：
>
> - 数据库
> - ORM
> - 消息队列
> - Python

------

