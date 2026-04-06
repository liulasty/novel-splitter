package com.novel.splitter.pipeline.orchestrator;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.repository.api.SceneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
