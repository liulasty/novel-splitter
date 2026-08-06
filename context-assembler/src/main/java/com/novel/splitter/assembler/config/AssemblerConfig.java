package com.novel.splitter.assembler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Context Assembler 配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "assembler")
public class AssemblerConfig {

    /**
     * 最终进入 Context 的最大 Chunk 数量
     * 推荐值：3
     */
    private int maxChunks = 3;

    /**
     * 单个 Chunk 允许的最大字数（软上限）
     * 推荐值：900
     */
    private int maxChunkLength = 900;

    /**
     * 最大 Context Token 限制
     */
    private int maxContextTokens = 3000;

    /**
     * 为回答保留的 Token 数
     */
    private int reserveForAnswerTokens = 1000;

    /**
     * 是否开启邻接合并
     */
    private boolean enableMerge = true;

    /**
     * 是否开启重评分
     */
    private boolean enableRescore = true;

    /**
     * 是否开启关键词加权
     */
    private boolean enableKeywordBoost = true;

    /**
     * 是否启用 ONNX 重排模型 (bge-reranker-base) 替代启发式重评分
     * 开启后将使用交叉编码器进行深度语义相关性打分
     */
    private boolean enableReranker = false;

    /**
     * 最大 Scene 数量限制 (同 maxChunks，保留以兼容)
     */
    private int maxScenes = 5;

    /**
     * 质量软加权混合权重（仅 ONNX 重排路径使用；启发式路径固定 0.1）
     */
    private double qualityScoreWeight = 0.15;

    /**
     * 相邻块扩展半径（±N，按 Scene.seq）；-1 关闭该特性
     */
    private int expandRadius = 1;

    /**
     * 相邻块扩展是否允许跨章
     */
    private boolean expandAcrossChapters = false;
}
