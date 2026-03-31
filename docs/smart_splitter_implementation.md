# 智能语义切分 (Smart Splitter) 详细实现文档

智能语义切分是 `novel-splitter` 的核心能力，旨在将长篇小说文本按照合理的结构和语义边界进行切割，为后续的大模型处理（如 RAG 或摘要提取）提供高质量的上下文片段。该系统由三大核心组件构成：`ChapterRecognizer`、`MarkdownParagraphSplitter` 和 `ContextAwareSegmentBuilder`。

---

## 1. 章节感知 (ChapterRecognizer)

`ChapterRecognizer` 负责从原始段落流中识别物理章节边界，是实现结构化的第一步。

**核心机制：**
- **精准正则匹配**：
  使用强大的正则表达式识别常见的中文和英文章节格式。
  - 匹配模式示例：`^\s*第[0-9一二三四五六七八九十百千万零]+[章回节卷].*` 或 `^\s*Chapter\s*\d+.*`。
- **防止误判**：
  为避免将正文中的长句误认为标题，系统设置了 `MAX_TITLE_LENGTH = 50` 的限制。超过该长度的匹配行将被视为普通段落。
- **前言/序章处理**：
  如果小说的开头部分没有明确的章节标题，系统会自动创建一个“序章/前言”（Prologue/Preface），将第一个正式章节之前的文本安全归档。
- **输出**：
  输出一个结构化的 `Chapter` 对象列表，每个对象包含章节名称以及其在全局段落列表中的起止索引。

**代码参考：**
- [ChapterRecognizer.java](file:///d:/soft/novel-splitter/splitter/src/main/java/com/novel/splitter/core/ChapterRecognizer.java)

---

## 2. 动态窗口切分 (MarkdownParagraphSplitter)

`MarkdownParagraphSplitter` 是物理切分层，主要基于 Markdown 格式和空行，将文本切分为基础段落（Paragraphs），尽量保证每个 Scene 都是一个完整的小故事或场景。

**核心机制：**
- **Markdown 语法解析**：
  不仅处理纯文本，还能识别 Markdown 元素，包括：
  - **多级标题** (`#` 至 `######`)
  - **列表项** (`-`, `*`, `+`, `1.`)
  - **引用块** (`>`)
  - **代码块** (` ``` `)
- **锚点标记 (Anchor System)**：
  切分器会自动将 **标题** 和 **代码块** 标记为 `isAnchor = true`。锚点代表“不可分割的强结构单元”。在后续的上下文合并阶段，锚点段落会被特殊保护，不会与其他无关段落随意拼接。
- **状态机处理**：
  使用轻量级状态机来追踪代码块的开启和闭合状态，确保代码块内部的所有空行、缩进和内容都被完整保留为一个逻辑段落。

**代码参考：**
- [MarkdownParagraphSplitter.java](file:///d:/soft/novel-splitter/splitter/src/main/java/com/novel/splitter/core/MarkdownParagraphSplitter.java)

---

## 3. 上下文合并 (ContextAwareSegmentBuilder)

`ContextAwareSegmentBuilder` 是整个智能切分的灵魂，它将物理段落（Paragraphs）聚合成具备完整语义的片段（Segments），避免对话被生硬切断。

**核心机制：**
- **段落类型推断**：
  能够区分“对话”（Dialogue）和“叙述”（Narration）。通常包含引号的段落会被优先视为对话相关。
- **智能语义吸附 (Adsorption)**：
  - **前缀合并**：当遇到一段简短的叙述（如“张三冷笑一声：”，长度 `< 50` 字），且紧接着是一段对话时，构建器会将其视为“说话人前缀”，并主动与对话段落合并。
  - **后缀合并**：类似地，当对话结束后紧跟简短的动作或表情描写（如“他转身离开了房间。”，长度 `< 50` 字），会被视为“动作后缀”并与前面的对话合并。
- **锚点独立性**：
  识别到 `isAnchor = true` 的段落（如章节内的子标题）时，会强制结束当前的语义合并，让标题单独成段，确保大纲结构的清晰。
- **安全边界控制**：
  通过 `MAX_SEGMENT_LENGTH = 800` 强制控制每个语义段落的最大长度，防止过度合并导致单段文本超出下游 LLM 的最佳处理窗口。

**代码参考：**
- [ContextAwareSegmentBuilder.java](file:///d:/soft/novel-splitter/splitter/src/main/java/com/novel/splitter/core/ContextAwareSegmentBuilder.java)

---

## 总结

通过 `MarkdownParagraphSplitter`（物理拆解）、`ChapterRecognizer`（宏观结构化）和 `ContextAwareSegmentBuilder`（微观语义聚合）的三级流水线，`novel-splitter` 实现了真正的“智能语义切分”。这种切分方式在保留小说剧情连贯性、对话完整性以及章节层次感方面表现出色。