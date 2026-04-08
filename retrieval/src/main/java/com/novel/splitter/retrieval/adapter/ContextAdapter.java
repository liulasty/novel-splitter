package com.novel.splitter.retrieval.adapter;

import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Scene;
import org.springframework.stereotype.Component;

/**
 * 上下文适配器
 * <p>
 * 负责将领域模型 Scene 转换为 RAG 上下文块 ContextBlock。
 * 该适配器充当了内部数据结构（Scene）与提供给大模型处理的数据结构（ContextBlock）之间的桥梁。
 * </p>
 */
@Component
public class ContextAdapter {

    /**
     * 将 Scene（场景）转换为 ContextBlock（上下文块）
     *
     * @param scene 从底层仓储或检索服务中获取的场景对象
     * @return 转换后供 LLM 提示词组装使用的 ContextBlock 对象
     */
    public ContextBlock convert(Scene scene) {
        // 使用 Builder 模式，将 Scene 的标识、文本内容以及相关元数据映射到 ContextBlock 中
        return ContextBlock.builder()
                .chunkId(scene.getId())
                .content(scene.getText())
                .sceneMetadata(scene.getMetadata())
                .build();
    }
}
