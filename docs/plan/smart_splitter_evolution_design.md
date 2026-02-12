# 智能切分系统演进设计方案 (Smart Splitter Evolution Design)

## 1. 概述 (Overview)

本方案旨在重构现有的三层切分架构（物理清洗 -> 语义聚合 -> 场景组装），将其从基于固定规则的“机械尺子”升级为具备上下文感知和动态调整能力的“智能编辑”。

目前（2026-02-11），**Phase 1 (Trajectory)**、**Phase 2 (Refinement)** 和 **Phase 3 (Adaptation)** 的核心逻辑已在代码中实现（采用简化或内联方式）。接下来的重点是 **Phase 4 (Evolution)** 的完善以及对现有逻辑的参数调优。

---

## 2. 演进阶段详细设计 & 当前状态

### Phase 1: Trajectory (轨迹清洗) - 增强物理层 [已完成]

**目标**：从单纯的“按行读取”升级为“结构化解析”，识别文本中的天然锚点。

**现状 (Current Status)**：
*   **Markdown 结构识别**：已通过 `MarkdownParagraphSplitter` 实现。
    *   支持标题 (`#`)、列表 (`-`, `1.`)、引用 (`>`)、代码块 (```` ``` ````) 的识别。
*   **锚点标记 (Anchoring)**：
    *   `RawParagraph` 已增加 `type` (TEXT, HEADER, CODE_BLOCK, LIST_ITEM, QUOTE) 和 `isAnchor` 属性。
    *   代码块和标题被标记为锚点，强制不切分。

**产出物**：
1.  **`RawParagraph`**：已升级。
2.  **`MarkdownParagraphSplitter`**：已替代 `ParagraphSplitter` 并在 Pipeline 中启用。

### Phase 2: Refinement (语义精细化) - 升级语义层 [已完成]

**目标**：精准捕捉“说话人+话语+动作”的混合结构，防止对话断裂。

**现状 (Current Status)**：
*   **混合结构识别**：
    *   `ContextAwareSegmentBuilder` 已实现。
    *   通过正则识别对话（Quote Pattern）并进行类型标记 (`DIALOGUE` vs `NARRATION`)。
*   **语义吸附**：
    *   Builder 内部实现了基本的吸附逻辑，将连续的对话或相关联的段落合并为 `SemanticSegment`。

**产出物**：
1.  **`ContextAwareSegmentBuilder`**：已集成到 `SceneAssembler` 中。
2.  **`DialogueRecognizer`**：逻辑已内嵌于 `ContextAwareSegmentBuilder` (Regex based)。

### Phase 3: Adaptation (动态窗口) - 改造场景层 [已初步实现]

**目标**：根据文本内容的“信息密度”动态调整切分窗口。

**现状 (Current Status)**：
*   **信息密度分析**：
    *   **实现方式**：目前在 `SceneAssembler` 中内联实现。
    *   **算法**：基于“对话/非对话”比例计算 `densityScore`。对话多则密度低，目标长度增加；对话少（叙述/代码）则密度高，目标长度缩短。
*   **动态窗口**：
    *   `DynamicWindowRule` 已实现并启用。
*   **重叠缓冲区 (Overlap Context)**：
    *   `SceneAssembler` 已实现 `prefixContext` 逻辑。
    *   策略：自动截取上一个 Scene 的末尾约 200 字符作为下一个 Scene 的上下文。

**产出物**：
1.  **`DynamicWindowRule`**：已实现。
2.  **`DensityAnalyzer`**：逻辑内嵌于 `SceneAssembler` (基于 Dialogue Ratio)。
3.  **`OverlapStrategy`**：逻辑内嵌于 `SceneAssembler`。

### Phase 4: Evolution (自我进化) - 反馈机制 [待完善]

**目标**：建立切分质量的反馈闭环，实现“越用越准”。

**现状 (Current Status)**：
*   **基础评估**：`SceneAssembler` 中包含一个简单的 `qualityScore` 计算（检查结尾标点是否完整），但尚未形成闭环或独立的评估模块。

**核心变更计划 (Planned Changes)**：
*   **提取评估逻辑**：将 `qualityScore` 计算从 `SceneAssembler` 中剥离，形成独立的 `SplitQualityEvaluator`。
*   **增强评估指标**：
    *   **断裂检测**：检测是否在句子中间切断（不仅是标点，还包括语义完整性）。
    *   **指代悬空检测**：(可选) 简单的代词检测。
*   **反馈应用**：
    *   在 Pipeline 后处理阶段，如果发现低分 Scene，记录日志或触发警告（暂不自动重切，避免复杂性）。

**产出物 (To Be Done)**：
1.  **`SplitQualityEvaluator`**：独立接口与实现。
2.  **`QualityReport`**：切分质量报告（日志或文件）。

---

## 3. 类结构现状 (Current Class Structure)

### Domain Layer
*   `RawParagraph`: 包含 `type`, `isAnchor`。
*   `SceneMetadata`: 包含 `densityScore`, `qualityScore`。
*   `Scene`: 包含 `prefixContext`。

### Core Layer (Splitter)
*   `MarkdownParagraphSplitter`: 负责物理切分与 Markdown 解析。
*   `ContextAwareSegmentBuilder`: 负责语义聚合与对话识别。
*   `SceneAssembler`: 核心组装器，内嵌了简单的密度分析与重叠逻辑。
*   `SplitRule` (Interface)
    *   `DynamicWindowRule`: 根据长度与密度决策。

---

## 4. 后续实施路线图 (Next Steps Roadmap)

鉴于核心功能已就位，接下来的工作重点是**验证**与**优化**，而非重构。

1.  **Stage 4 (Evolution) - 质量评估标准化**:
    *   **任务**：将 `SceneAssembler` 中的 `qualityScore` 逻辑提取为 `SplitQualityEvaluator` 接口。
    *   **目的**：解耦组装与评估，便于未来接入更复杂的评估逻辑（如 LLM 评分）。

2.  **测试与调优 (Testing & Tuning)**:
    *   **任务**：编写单元测试，针对不同类型的文本（代码密集、纯对话、混合），验证 `DynamicWindowRule` 的行为是否符合预期。
    *   **参数调整**：根据测试结果调整 `TARGET_SCENE_LENGTH` 基准值和密度系数。

3.  **代码清理 (Refactoring)**:
    *   **任务**：检查 `SceneAssembler` 是否过于臃肿，考虑是否需要将 `Density` 计算逻辑提取为独立 Helper 类。

4.  **集成测试**:
    *   运行完整 Pipeline，检查生成的 `Scene` JSON 结果，确认 `prefixContext` 和 `metadata` 字段的正确性。
