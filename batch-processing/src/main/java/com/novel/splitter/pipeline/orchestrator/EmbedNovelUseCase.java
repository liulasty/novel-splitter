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
            List<String> vectorIds = vectorStore.saveBatch(validScenes, embeddings);
            
            if (vectorIds != null && vectorIds.size() == validScenes.size()) {
                for (int i = 0; i < validScenes.size(); i++) {
                    Scene scene = validScenes.get(i);
                    String vectorId = vectorIds.get(i);
                    sceneRepository.updateEmbedStatus(Long.valueOf(scene.getId()), "SUCCESS", vectorId);
                }
            } else {
                log.warn("Vector IDs returned do not match valid scenes count.");
                for (Scene scene : validScenes) {
                    sceneRepository.updateEmbedStatus(Long.valueOf(scene.getId()), "FAILED", null);
                }
            }
        } catch (Exception e) {
            log.error("Error processing embed batch (Scene IDs: {}-...)", sceneIds.get(0), e);
            for (Long sceneId : sceneIds) {
                sceneRepository.updateEmbedStatus(sceneId, "FAILED", null);
            }
            throw new RuntimeException("Batch embed processing failed", e);
        }
    }
}
