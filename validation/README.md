# validation

## 模块概述
作为业务校验引擎，提供独立的校验规则体系，用于评估拆分后文本段落或语义场景是否满足既定的质量与格式标准。

## 核心职责
- **定义校验规范**：提供统一的 `SceneValidator` 校验接口和校验结果对象 `ValidationResult`，标准化校验输出。
- **提供基础校验实现**：内置基于长度（`LengthValidator`）等基础属性的校验逻辑，保证生成的片段满足大模型及向量数据库的要求。
- **支持语义段构建策略**：通过 `SemanticSegmentBuilder` 等核心类提供段落合并和语义处理相关的策略接口及实现（如对话策略、长度限制策略）。

## 技术栈
- 核心语言：Java 21
- 轻量依赖：内部依赖 `domain`，并复用父 POM 提供的 `lombok`。

## 模块依赖
- 本模块依赖的内部子模块：`domain`
- 当前主要依赖本模块的内部子模块：`text-processing`

## 核心组件
| 组件名称 | 类型 | 核心职责 |
|----------|------|----------|
| `SceneValidator` | 接口 | 核心校验契约，定义了对拆分后场景文本进行质量检查的规范。 |
| `ValidationResult` | 实体 | 封装校验结果，包含校验是否通过、错误信息和提示。 |
| `LengthValidator` | 实现类 | 实现了 `SceneValidator` 接口，具体检查文本片段是否在最小和最大长度限制之内。 |
| `SemanticSegmentBuilder` | 核心类 | 负责按给定策略及状态管理构建连续语义段。 |
| `SegmentMergeStrategy` | 接口 | 定义连续文本段合并的基础策略（位于 `core.strategy`）。 |

## 扩展点
- **扩展点 1**：通过实现 `SceneValidator` 接口可添加诸如特定字符过滤、关键词审查等高级验证逻辑。
- **扩展点 2**：可实现自定义的 `DialogueStrategy` 或 `LengthLimitStrategy`，并集成到 `SemanticSegmentBuilder`，调整组装逻辑。

## 注意事项
- **注意 1**：验证器应保持无状态和线程安全，确保可在并发批处理流中高效调用。
- **注意 2**：本模块只做“判断”不做“处理”，不应在 `Validator` 内部直接修改 `domain` 实体内容。