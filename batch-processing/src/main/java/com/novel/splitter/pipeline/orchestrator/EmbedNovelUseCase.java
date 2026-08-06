package com.novel.splitter.pipeline.orchestrator;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.domain.repository.SceneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 批量写入向量库。场景行 embed 状态由应用层消费者在向量写入之后更新；
 * 顺序为：先 Chroma saveBatch，再 DB 标记 SUCCESS（先 DB 后向量失败时可能不一致，可二期收紧事务/补偿）。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmbedNovelUseCase {

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final SceneRepository sceneRepository;

    /**
     * 对给定 DB 主键做 ONNX + Chroma 批量写入；整批原子（任一步失败则抛异常，不返回部分成功）。
     *
     * @return 实际完成向量写入的场景 persistence id 列表（顺序与 validScenes 一致）
     */
    public List<Long> embedBatch(List<Long> scenePersistenceIds) {
        return embedBatch(scenePersistenceIds, null);
    }

    /**
     * 批量写入向量库到指定集合。
     * @param scenePersistenceIds DB 主键列表
     * @param collectionName      目标 Chroma 集合名；null 或空白时使用默认集合
     */
    public List<Long> embedBatch(List<Long> scenePersistenceIds, String collectionName) {
        if (scenePersistenceIds == null || scenePersistenceIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Scene> scenes = sceneRepository.findByIds(scenePersistenceIds);
        if (scenes == null || scenes.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> texts = new ArrayList<>();
        List<Scene> validScenes = new ArrayList<>();
        for (Scene scene : scenes) {
            if (scene.getText() != null && !scene.getText().trim().isEmpty()) {
                texts.add(scene.getText());
                validScenes.add(scene);
            } else {
                log.warn("场景 ID {} 文本为空，跳过", scene.getId());
            }
        }
        if (validScenes.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            List<float[]> embeddings = embeddingService.embedBatch(texts);
            if (collectionName != null && !collectionName.isBlank()) {
                vectorStore.saveBatch(validScenes, embeddings, collectionName);
            } else {
                vectorStore.saveBatch(validScenes, embeddings);
            }
            return validScenes.stream()
                    .map(Scene::getPersistenceId)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("嵌入批次处理失败（首个 persistence id: {}）", scenePersistenceIds.get(0), e);
            throw new RuntimeException("Batch embed processing failed", e);
        }
    }
}
