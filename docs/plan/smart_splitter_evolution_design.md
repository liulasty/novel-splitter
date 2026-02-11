# 智能切分系统演进设计方案 (Smart Splitter Evolution Design)

## 1. 概述 (Overview)

本方案旨在重构现有的三层切分架构（物理清洗 -> 语义聚合 -> 场景组装），将其从基于固定规则的“机械尺子”升级为具备上下文感知和动态调整能力的“智能编辑”。

我们将遵循 **Trae 四阶段演进策略** 进行重构：
1.  **Trajectory (轨迹清洗)**：增强物理层，识别结构化标记。
2.  **Refinement (语义精细化)**：升级语义层，精准识别对话与动作。
3.  **Adaptation (动态窗口)**：改造场景层，引入信息熵密度动态调整与重叠上下文。
4.  **Evolution (自我进化)**：建立反馈闭环，基于质量评估优化切分策略。

---

## 2. 演进阶段详细设计

### Phase 1: Trajectory (轨迹清洗) - 增强物理层

**目标**：从单纯的“按行读取”升级为“结构化解析”，识别文本中的天然锚点。

**核心变更**：
*   **Markdown 结构识别**：
    *   标题 (`#`, `##`, `###`)：标记为强语义边界，通常对应新的章节或小节。
    *   列表 (`-`, `1.`)：标记为列表项，尽量保持列表完整性。
    *   引用 (`>`)：标记为引用块。
    *   代码块 (```` ``` ````)：**原子性保护**，代码块内部绝对不可切分，防止逻辑断裂。
*   **锚点标记 (Anchoring)**：
    *   为 `RawParagraph` 增加 `type` (TEXT, HEADER, CODE_BLOCK, LIST_ITEM) 和 `isAnchor` 属性。

**产出物**：
1.  **`RawParagraph` 模型升级**：增加类型和元数据字段。
2.  **`StructuredParagraphSplitter`**：替代或增强原有的 `ParagraphSplitter`，集成 Markdown 解析逻辑（可使用 regex 或轻量状态机）。

### Phase 2: Refinement (语义精细化) - 升级语义层

**目标**：打破仅靠引号识别对话的局限，精准捕捉“说话人+话语+动作”的混合结构。

**核心变更**：
*   **混合结构识别 (Mixed Structure Recognition)**：
    *   支持 `“...”说。` 或 `XX道：“...”` 等常见中文小说对话模式。
    *   支持无引号的动作描写与对话的紧密结合（如：*他沉默了一会儿，低声说道：快走。*）。
*   **语义吸附 (Semantic Adsorption)**：
    *   对话前后的短动作描写（Action Description）应被吸附到同一个 `SemanticSegment` 中。
    *   防止“孤儿行”（Orphan Line）：避免将一句完整的跨行对话切断。

**产出物**：
1.  **`DialogueRecognizer` 组件**：基于增强正则库的对话识别器。
2.  **`ContextAwareSegmentBuilder`**：升级版的语义段构建器，支持吸附逻辑。

### Phase 3: Adaptation (动态窗口) - 改造场景层

**目标**：废除固定字数阈值，根据文本内容的“信息密度”动态调整切分窗口。

**核心变更**：
*   **信息密度分析 (Density Analysis)**：
    *   **高密度 (High Density)**：代码、公式、密集技术术语。
        *   特征：包含代码块、特殊符号多、平均词长长。
        *   策略：目标长度缩短至 **800字**，便于 LLM 聚焦分析。
    *   **低密度 (Low Density)**：日常对话、流水账叙述。
        *   特征：常用词多、对话比例高。
        *   策略：目标长度延长至 **1500字**，提供更完整的上下文。
*   **重叠缓冲区 (Overlap Context)**：
    *   在强制切分点，将前一个 Scene 的末尾 N 个段落（或 M 个字）复制到下一个 Scene 的开头。
    *   **Overlap Window**：动态设定，通常为目标长度的 10%-15%（如 100-200 字）。

**产出物**：
1.  **`DensityAnalyzer`**：计算文本段的信息密度得分。
2.  **`DynamicWindowRule`**：替代 `LengthRule`，接受密度得分作为输入调整阈值。
3.  **`OverlapStrategy`**：处理切分边界的重叠逻辑。

### Phase 4: Evolution (自我进化) - 反馈机制

**目标**：建立切分质量的反馈闭环，实现“越用越准”。

**核心变更**：
*   **困惑度模拟 (Perplexity Simulation)**：
    *   由于无法实时调用大模型计算真实 PPL，采用启发式算法模拟：
        *   **断裂检测**：检查切分点是否切断了句子（以非终结标点结尾）。
        *   **指代悬空**：检查新切片开头是否包含未指代的代词（如“他”，“这”），且前文无对应名词。
*   **权重调整 (Weight Adjustment)**：
    *   如果某类切分点（如“列表项中间”）经常导致高困惑度，则在后续切分中提高该规则的“不可切分”权重。

**产出物**：
1.  **`SplitQualityEvaluator`**：切分质量评估接口。
2.  **`HeuristicFeedbackLoop`**：基于规则的简单反馈实现。

---

## 3. 类结构调整 (Class Structure Updates)

为了保持兼容性，我们将通过**继承**和**策略模式**扩展现有类，而不是直接修改原有逻辑（除非必要）。

### Domain Layer
*   `RawParagraph`
    *   `+ String type` (TEXT, HEADER, CODE...)
    *   `+ boolean isAnchor`
*   `SceneMetadata`
    *   `+ double densityScore`
    *   `+ boolean isOverlap`

### Core Layer (Splitter)
*   `SplitRule` (Interface) -> 保持不变
    *   `Decision evaluate(...)`
*   `DynamicWindowRule` implements `SplitRule` (New)
*   `ParagraphSplitter` -> `MarkdownParagraphSplitter` (extends or replaces)
*   `SemanticSegmentBuilder` -> `ContextAwareSegmentBuilder` (extends)

---

## 4. 实施路线图 (Implementation Roadmap)

1.  **Stage 1 (Trajectory)**:
    *   创建 `MarkdownParagraphSplitter`。
    *   更新 `RawParagraph` 定义。
    *   单元测试：验证 Markdown 识别。

2.  **Stage 2 (Refinement)**:
    *   开发 `DialogueRecognizer`。
    *   升级 `SemanticSegmentBuilder` 逻辑。
    *   单元测试：复杂对话场景测试。

3.  **Stage 3 (Adaptation)**:
    *   实现 `DensityAnalyzer`。
    *   实现 `DynamicWindowRule`。
    *   在 `SceneAssembler` 中集成 Overlap 逻辑。

4.  **Stage 4 (Evolution)**:
    *   实现 `HeuristicQualityEvaluator`。
    *   集成到 Pipeline 中（作为 Post-processing 或 Logging 步骤）。
