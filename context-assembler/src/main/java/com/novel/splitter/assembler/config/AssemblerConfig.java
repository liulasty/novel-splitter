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
}
