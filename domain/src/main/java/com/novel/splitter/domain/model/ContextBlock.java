package com.novel.splitter.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Prompt 中的上下文块
 * <p>
 * 代表检索到的一个证据单元，包含内容及其元数据。
 * 禁止使用 String 拼接，保持结构化以便于后续处理（如 Token 计算、溯源）。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextBlock {
    /** 原始 Chunk ID (用于溯源和去重) */
    private String chunkId;

    /** 文本内容 */
    private String content;

    /** 关联的元数据 (直接引用 SceneMetadata) */
    private SceneMetadata sceneMetadata;
}
