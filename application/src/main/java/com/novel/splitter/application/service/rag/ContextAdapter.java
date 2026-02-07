package com.novel.splitter.application.service.rag;

import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Scene;
import org.springframework.stereotype.Component;

/**
 * 上下文适配器
 * <p>
 * 负责将领域模型 Scene 转换为 RAG 上下文块 ContextBlock。
 * </p>
 */
@Component
public class ContextAdapter {

    /**
     * 将 Scene 转换为 ContextBlock
     */
    public ContextBlock convert(Scene scene) {
        return ContextBlock.builder()
                .chunkId(scene.getId())
                .content(scene.getText())
                .sceneMetadata(scene.getMetadata())
                .build();
    }
}
