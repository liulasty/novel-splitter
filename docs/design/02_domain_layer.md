# Domain 层详细设计

## 1. 概述
Domain 层包含系统的核心业务模型。它是纯粹的 Java POJO，不依赖任何框架（如 Spring, Hibernate, Jackson），确保核心逻辑的纯净性和可移植性。

## 2. 核心原则
- **无依赖**：不依赖外部框架。
- **贫血/充血模型**：主要作为数据载体（DTO/VO），但可包含简单的业务校验逻辑。
- **不可变性**：核心属性一旦创建，尽量保持不可变（Immutable），推荐使用 Builder 模式。

## 3. 核心对象模型

### 3.1 RawParagraph (原始段落)
表示从文本文件中读取的最小物理单位（通常是一行）。
```java
public class RawParagraph {
    private final int index;       // 全局段落索引（行号）
    private final String content;  // 文本内容（已去除首尾空白）
    private final boolean isEmpty; // 是否为空行
    // ...
}
```

### 3.2 Chapter (章节)
表示小说的一个章节，通常由标题和一系列段落组成。
```java
public class Chapter {
    private int index;             // 章节序号
    private String title;          // 章节标题
    private Range paragraphRange;  // 包含的段落范围 [start, end]
    // ...
}
```

### 3.3 SemanticSegment (语义段)
表示具有某种语义连续性的文本块。它是 Scene 的中间构建产物。
```java
public class SemanticSegment {
    private List<RawParagraph> paragraphs;
    private SegmentType type; // 描述、对话、混合
    // ...
}
```

### 3.4 Scene (场景 - 核心聚合根)
Scene 是本系统的最终产出单位，代表一个相对独立的语义场景。
```java
public class Scene {
    private String id;              // 唯一标识 (UUID 或 基于Hash)
    private String chapterTitle;    // 所属章节标题
    private int chapterIndex;       // 所属章节索引
    private int startParagraph;     // 起始段落索引
    private int endParagraph;       // 结束段落索引
    private String text;            // 完整文本内容
    private int tokenCount;         // 预估 Token 数（可选）
    private SceneMetadata metadata; // 元数据
    
    // 构造函数、Getters、Builder
}
```

### 3.5 SceneMetadata (场景元数据)
存储关于 Scene 的辅助信息，用于后续分析。
```java
public class SceneMetadata {
    private List<String> characters; // 出现的人物（预留）
    private String location;         // 地点（预留）
    private String time;             // 时间（预留）
    private Map<String, Object> extra; // 扩展字段
}
```

## 4. 模块结构
```
domain
├── model
│   ├── RawParagraph.java
│   ├── Chapter.java
│   ├── SemanticSegment.java
│   ├── Scene.java
│   └── SceneMetadata.java
├── type
│   ├── SegmentType.java
│   └── SplitStrategyType.java
└── exception
    └── DomainException.java
```

## 5. 待办事项
- [ ] 确定是否引入 Lombok（建议引入以减少样板代码，但需评估"无依赖"原则的权衡，通常 Lombok 是编译期依赖，运行时无害）。
- [ ] 完善 `Scene` 对象的 `equals` 和 `hashCode` 方法，确保可比性。
- [ ] 定义 `Range` 值对象用于处理区间。
