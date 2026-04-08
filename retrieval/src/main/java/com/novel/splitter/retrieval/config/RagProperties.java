package com.novel.splitter.retrieval.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG（检索增强生成）配置属性类
 * <p>
 * 将配置文件（如 application.yml 或 application.properties）中前缀为 "splitter.rag" 的属性
 * 绑定到当前类的字段中，用于控制 RAG 流程的各项参数。
 * </p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "splitter.rag")
public class RagProperties {

    /**
     * 系统指令（System Instruction）
     * 设定给大语言模型（LLM）的全局人设和行为规范。
     */
    private String systemInstruction;

    /**
     * 输出约束（Output Constraint）
     * 指定 LLM 生成回答时的格式要求或限制条件，例如是否需要 JSON 格式、引用格式等。
     */
    private String outputConstraint;

    /**
     * 默认检索数量（Top K）
     * 在用户请求未指定检索条数时，默认从向量库中检索出的最相关片段数量，默认值为 5。
     */
    private int defaultTopK = 5;

    /**
     * 最小置信度（Min Confidence）
     * 过滤或评估 LLM 回答及检索结果有效性的阈值，默认值为 0.5。
     */
    private double minConfidence = 0.5;

    /**
     * 最大重试次数（Max Retries）
     * 当调用 LLM 或其他外部依赖失败时，允许重试的最大次数，默认值为 2。
     */
    private int maxRetries = 2;
}
