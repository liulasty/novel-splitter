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
     * 最大 Scene 数量限制 (同 maxChunks，保留以兼容)
     */
    private int maxScenes = 5;
}
