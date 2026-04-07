# text-processing

## 模块概述
作为本项目的核心自然语言与文本引擎，负责小说原始文本的章节识别、段落切割、语义密度分析以及基于上下文的场景组装，是实现智能拆分的“大脑”。

## 核心职责
- **章节与段落切分**：提供基于正则或特定标记的 `ChapterRecognizer` 及针对 Markdown 等格式的 `ParagraphSplitter`，将长文本初步结构化。
- **语义特征提取**：通过 `SemanticDensityAnalyzer` 提取段落的语义特征与相关性（如对话特征、关键词密度）。
- **上下文感知组装**：通过 `ContextAwareSegmentBuilder` 和 `SceneAssembler`，结合各类切割规则（`SplitRule`），将离散段落组装成具有完整连贯语义的场景片段。
- **规则策略应用**：提供长度限制、动态窗口等核心切分与合并规则的落地实现。

## 技术栈
- 核心语言：Java 21
- 主要依赖：Apache Commons Lang3

## 模块依赖
- 本模块依赖的内部子模块：`domain`, `embedding`, `validation`
- 依赖本模块的内部子模块：`batch-processing`

## 核心组件
| 组件名称 | 类型 | 核心职责 |
|----------|------|----------|
| `MarkdownParagraphSplitter` | 实现类 | 继承自 `ParagraphSplitter`，专门处理 Markdown 格式的段落切分。 |
| `ChapterRecognizer` | 服务类 | 识别小说章节边界，抽取章节标题及内容元数据。 |
| `SceneAssembler` | 服务类 | 核心组装引擎，基于相关性打分将段落聚合为完整的连贯场景。 |
| `SemanticDensityAnalyzer` | 工具/服务类 | 分析文本语义密度（关键词频率、实体密度等），辅助决定切分点。 |
| `SplitRule` | 接口 | 文本拆分与组合规则的抽象接口，其实现类如 `LengthRule` 规范拆分逻辑。 |

## 扩展点
- **扩展点 1**：扩展 `ParagraphSplitter` 支持对 PDF 提取文本或 HTML 的段落切分逻辑。
- **扩展点 2**：实现新的 `SplitRule` 或增强 `BoundaryKeywordDictionary`（边界词典），以支持特定流派小说的专属拆分策略。

## 注意事项
- **注意 1**：文本处理算法应重点考虑大文本输入情况下的内存消耗与垃圾回收压力。
- **注意 2**：`SceneAssembler` 组装出的场景必须符合 `validation` 模块的校验规则。