package com.novel.splitter.pipeline.orchestrator;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.domain.repository.SceneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

    public void embedBatch(List<Long> sceneIds) {
        if (sceneIds == null || sceneIds.isEmpty()) return;
        List<Scene> scenes = sceneRepository.findByIds(sceneIds);
        if (scenes == null || scenes.isEmpty()) return;

        try {
            List<String> texts = new ArrayList<>();
            List<Scene> validScenes = new ArrayList<>();
            for (Scene scene : scenes) {
                if (scene.getText() != null && !scene.getText().trim().isEmpty()) {
                    texts.add(scene.getText());
                    validScenes.add(scene);
                } else {
                    log.warn("Skipping scene ID {} due to empty text", scene.getId());
                }
            }
            if (validScenes.isEmpty()) return;

            List<float[]> embeddings = embeddingService.embedBatch(texts);
            vectorStore.saveBatch(validScenes, embeddings);
        } catch (Exception e) {
            log.error("Error processing embed batch (Scene IDs: {}-...)", sceneIds.get(0), e);
            throw new RuntimeException("Batch embed processing failed", e);
        }
    }
}
