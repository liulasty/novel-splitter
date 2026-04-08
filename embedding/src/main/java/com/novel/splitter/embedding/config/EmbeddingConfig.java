package com.novel.splitter.embedding.config;

import org.springframework.context.annotation.Configuration;

/**
 * 嵌入服务配置类 (Embedding Configuration)
 * <p>
 * 该配置类用于初始化 NLP/RAG 小说处理系统中的嵌入模型 (Embedding Model) 和向量存储 (Vector Store) 等相关的 Bean。
 * 它是基于 Spring Boot 架构的配置中心，可以在此处统一管理诸如 ONNX 模型的加载、第三方向量库客户端的实例化等操作。
 * </p>
 */
@Configuration
public class EmbeddingConfig {

    // ------------------------------------------------------------------------
    // TODO: 根据实际引入的底层模型推理引擎（如 ONNX Runtime、DJL 等）取消下方的注释并完善实现
    // ------------------------------------------------------------------------

    // /**
    //  * 实例化 ONNX 模型持有者 (ONNX Model Holder)
    //  * <p>
    //  * 在内存中加载并持有一个预训练的深度学习模型（如基于 BERT 的文本嵌入模型）。
    //  * 将其注册为 Spring Bean 以确保在应用生命周期内作为单例复用，避免频繁加载模型导致显存或内存溢出。
    //  * </p>
    //  *
    //  * @return 初始化的 OnnxModelHolder 实例，供 EmbeddingService 依赖注入并使用
    //  */
    // @Bean
    // public OnnxModelHolder onnxModelHolder() {
    //     // 初始化并返回封装了 ONNX Session 和相关词表 (Tokenizer) 的持有者对象
    //     return new OnnxModelHolder();
    // }
}
