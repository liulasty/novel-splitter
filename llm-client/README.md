# llm-client

## 模块概述
作为系统与各大语言模型（LLM）通信的抽象层与网关，负责屏蔽不同模型厂商 API 的差异，并提供统一的、具备高可用特性（重试、降级）的调用接口。

## 核心职责
- **模型厂商适配**：通过实现 `LlmClient` 接口，无缝对接 DeepSeek、Gemini、Coze 以及本地 Ollama 等多种大语言模型。
- **高可用与鲁棒性**：利用 `RobustLlmClient` 及 Spring Retry 机制，封装了网络超时、速率限制（Rate Limit）触发时的自动重试与降级逻辑。
- **参数动态装配**：结合 `application.yml` 和相应的 `Properties` 类（如 `DeepSeekProperties`），动态加载模型名称、API Key、Temperature 等推理参数。
- **统一交互数据模型**：规范模型输入的 Prompt 结构以及输出的 JSON/文本格式，提供针对特定供应商的参数适配（如 Ollama 的 `num_ctx`）。

## 技术栈
- 核心语言：Java 21
- 主要依赖：Google GenAI SDK, Spring Retry, Spring Boot AutoConfigure, Spring Web, Lombok

## 模块依赖
- 本模块依赖的内部子模块：`domain`
- 依赖本模块的内部子模块：`application`, `retrieval`

## 核心组件
| 组件名称 | 类型 | 核心职责 |
|----------|------|----------|
| `LlmClient` | 接口 | 大模型通信的核心契约，定义了基于系统提示词和用户输入生成回答的标准方法。 |
| `DeepSeekLlmClient` | 实现类 | 封装 DeepSeek 官方 API（基于 HTTP）的调用逻辑，处理专属响应解析。 |
| `OllamaLlmClient` | 实现类 | 对接本地化部署的 Ollama 服务，支持诸如 `num_ctx` 等底层性能优化参数。 |
| `RobustLlmClient` | 装饰器/包装类 | 基于代理模式或 AOP，为底层基础 Client 附加异常拦截、自动重试和超时恢复策略。 |
| `LlmClientConfig` | 配置类 | 根据环境变量或配置，动态决定向 Spring 容器注入哪一种具体的大模型客户端实例。 |

## 使用示例
```java
// 注入统一的大模型客户端
@Autowired
private LlmClient llmClient;

// 构建 Prompt 模型
Prompt prompt = Prompt.builder()
    .systemInstruction("你是一个专门分析小说的AI助手，仅返回JSON。")
    .userContext("文本片段...")
    .userQuery("主角的名字叫什么？")
    .build();

// 发起请求并获取纯文本或 JSON 结果
String answer = llmClient.chat(prompt);
```

## 扩展点
- **扩展点 1**：实现 `LlmClient` 接口并配合 `LlmClientConfig`，可快速新增对接 OpenAI、Anthropic 等新厂商的客户端。
- **扩展点 2**：在 `RobustLlmClient` 中引入 Resilience4j 替换或增强 Spring Retry，实现更复杂的熔断器（Circuit Breaker）逻辑。

## 注意事项
- **注意 1**：在处理 JSON 模式输出时（如 RAG 场景要求），需确保具体的 Client 实现能正确附加特定的提示词或 API 参数（如 Ollama 的 `format: json`）以约束模型行为。
- **注意 2**：避免在客户端内堆积任何与小说业务相关的逻辑，保持其作为“基础设施代理”的纯粹性。