package com.novel.splitter.application.service.etl;

import com.novel.splitter.core.SceneAssembler;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.repository.api.SceneRepository;
import com.novel.splitter.infrastructure.progress.IngestProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.domain.task.SplitTaskMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * 小说入库服务 (Phase 3 核心入口)
 * <p>
 * 负责协调：
 * 1. 本地文件加载 (Load)
 * 2. 语义切分 (Split)
 * 3. 向量化与存储 (Embed & Store) - 存入 ChromaDB
 * 4. 持久化 (Persist) - 存入 SceneRepository (Disk)
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NovelIngestionService {

    private final LocalNovelLoader novelLoader;
    private final NovelCacheService novelCacheService;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final SceneRepository sceneRepository;
    private final com.novel.splitter.application.service.task.ProgressSseService progressSseService;
    private final RabbitTemplate rabbitTemplate;
    
    // 实例化切分器 (也可配置为 Bean)
    private final SceneAssembler sceneAssembler = new SceneAssembler();
    
    // 批处理大小 (根据显存和 Chroma 性能调整)
    private static final int BATCH_SIZE = 10; 

    public void ingestAsync(String taskId, String novelId, String novelPathStr, int maxScenes, String version) {
        SplitTaskMessage message = new SplitTaskMessage(taskId, novelId, novelPathStr, maxScenes, version);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "load", message);
        log.info("Sent taskId {} to load queue", taskId);
    }

    /**
     * 执行入库流程 (兼容旧接口)
     */
    public void ingest(Path novelPath, int maxScenes) {
        ingest(java.util.UUID.randomUUID().toString(), novelPath, maxScenes, null, null);
    }

    /**
     * 执行入库流程
     * @param novelPath 本地 TXT 文件路径
     * @param maxScenes 最大处理场景数 (-1 表示不限制)
     * @param version   版本标识 (可选)
     */
    public void ingest(Path novelPath, int maxScenes, String version) {
        ingest(java.util.UUID.randomUUID().toString(), novelPath, maxScenes, version, null);
    }

    /**
     * 兼容旧版调用的入库流程 (向后兼容)
     * @param novelPath 本地 TXT 文件路径
     * @param maxScenes 最大处理场景数 (-1 表示不限制)
     * @param version   版本标识 (可选)
     * @param rawProgressCallback 进度回调
     */
    public void ingest(Path novelPath, int maxScenes, String version, BiConsumer<Integer, String> rawProgressCallback) {
        ingest(java.util.UUID.randomUUID().toString(), novelPath, maxScenes, version, rawProgressCallback);
    }

    public void ingest(String taskId, Path novelPath, int maxScenes, String version, BiConsumer<Integer, String> rawProgressCallback) {
        BiConsumer<Integer, String> progressCallback = rawProgressCallback != null 
                ? rawProgressCallback 
                : (p, msg) -> log.debug("[Ingest Progress {}%] {}", p, msg);

        try {
            Novel novel = loadPhase(taskId, novelPath, progressCallback);
            splitPhase(taskId, novel, maxScenes, version, progressCallback);
            embedPhase(novel.getTitle(), version, progressCallback);
        } catch (java.io.IOException e) {
            log.error("[Ingest] 文件读取失败，不可重试: {}", novelPath, e);
            throw new IngestException("文件不可读", e, false);
        } catch (RuntimeException e) {
            log.warn("[Ingest] 处理异常，可重试: {}", e.getMessage(), e);
            throw new IngestException("处理失败", e, true);
        } catch (Exception e) {
            log.error("[Ingest] 处理异常，不可重试: {}", e.getMessage(), e);
            throw new IngestException("处理失败", e, false);
        }
    }

    public Novel loadPhase(String taskId, Path novelPath, BiConsumer<Integer, String> progressCallback) throws Exception {
        log.info("=== Start Load Phase for: {} (taskId: {}) ===", novelPath, taskId);
        progressCallback.accept(IngestProgress.LOAD_START, "开始读取文件...");
        Novel novel = novelLoader.load(taskId, novelPath);
        progressCallback.accept(IngestProgress.LOAD_END, String.format("文件读取完成，共 %d 个章节", novel.getChapters().size()));
        return novel;
    }

    public List<Long> splitPhase(String taskId, Novel novel, int maxScenes, String version, BiConsumer<Integer, String> progressCallback) {
        log.info("=== Start Split Phase for: {} ===", novel.getTitle());
        
        List<Scene> scenes = new ArrayList<>();
        List<com.novel.splitter.domain.model.Chapter> chapters = novel.getChapters();
        int totalChapters = chapters.size();
        int scenesCount = 0;
        
        if (progressCallback != null) {
            progressCallback.accept(IngestProgress.CHAPTER_END, String.format("准备逐章切分，共 %d 章", totalChapters));
        }

        for (int i = 0; i < totalChapters; i++) {
            com.novel.splitter.domain.model.Chapter chapter = chapters.get(i);
            
            // Load chapter data from cache
            com.novel.splitter.domain.model.ChapterData chapterData = novelCacheService.loadChapter(taskId, chapter.getIndex());
            
            List<Scene> chapterScenes = sceneAssembler.assembleChapter(chapter, chapterData.getParagraphs(), novel.getTitle());
            scenes.addAll(chapterScenes);
            scenesCount += chapterScenes.size();
            
            if (progressCallback != null && (i % 10 == 0 || i == totalChapters - 1)) {
                int progress = IngestProgress.calc(IngestProgress.SCENE_START, IngestProgress.SCENE_END, i + 1, totalChapters);
                progressCallback.accept(progress, String.format("正在切分章节：%d/%d，已生成 %d 个场景", i + 1, totalChapters, scenesCount));
            }
            
            if (maxScenes > 0 && scenes.size() >= maxScenes) {
                break;
            }
        }

        log.info("Generated {} scenes from novel '{}'", scenes.size(), novel.getTitle());

        progressCallback.accept(IngestProgress.VALIDATE_END, String.format("切分完成：共 %d 个有效场景", scenes.size()));

        if (scenes.isEmpty()) {
            log.warn("No scenes generated! Check split rules or input file.");
            return new ArrayList<>();
        }

        if (maxScenes > 0 && scenes.size() > maxScenes) {
            log.warn("Limiting ingestion to first {} scenes (Total: {})", maxScenes, scenes.size());
            scenes = scenes.subList(0, maxScenes);
        }

        String finalVersion = (version != null && !version.isBlank()) ? version : "v1-ingestion";
        scenes.forEach(s -> {
            if (s.getMetadata() != null) {
                s.getMetadata().setVersion(finalVersion);
            }
        });

        progressCallback.accept(IngestProgress.SAVE_START, "正在保存场景到本地存储...");
        List<Long> sceneIds = sceneRepository.saveScenes(novel.getTitle(), finalVersion, scenes);
        progressCallback.accept(IngestProgress.SAVE_END, String.format("本地存储完成，共 %d 个场景", scenes.size()));
        
        return sceneIds;
    }

    public void embedPhase(String novelTitle, String version, BiConsumer<Integer, String> progressCallback) {
        log.info("=== Start Embed Phase for: {} ===", novelTitle);
        String finalVersion = (version != null && !version.isBlank()) ? version : "v1-ingestion";
        List<Scene> scenes = sceneRepository.loadScenes(novelTitle, finalVersion);
        if (scenes == null || scenes.isEmpty()) {
            log.warn("No scenes found in repository for embedding (novel: {}, version: {})", novelTitle, finalVersion);
            return;
        }
        processBatches(scenes, progressCallback);
        progressCallback.accept(100, "入库完成");
        log.info("=== Ingestion Completed Successfully ===");
    }
    
    public void embedPhaseBatch(List<Long> sceneIds) {
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
    
    private void processBatches(List<Scene> scenes, BiConsumer<Integer, String> progressCallback) {
        int total = scenes.size();
        AtomicInteger processedCount = new AtomicInteger(0);
        
        log.info("Starting embedding and storage for {} scenes...", total);
        
        int totalBatches = (int) Math.ceil((double) total / BATCH_SIZE);
        int batchIndex = 0;

        for (int i = 0; i < total; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, total);
            List<Scene> batchScenes = scenes.subList(i, end);
            
            try {
                // 3.1 Extract texts
                List<String> texts = new ArrayList<>();
                List<Scene> validBatchScenes = new ArrayList<>();
                for (Scene scene : batchScenes) {
                    if (scene.getText() != null && !scene.getText().trim().isEmpty()) {
                        texts.add(scene.getText());
                        validBatchScenes.add(scene);
                    } else {
                        log.warn("Skipping scene ID {} due to empty text in processBatches", scene.getId());
                    }
                }
                
                if (validBatchScenes.isEmpty()) {
                    batchIndex++;
                    continue;
                }
                
                // 3.2 Embed (Batch)
                List<float[]> embeddings = embeddingService.embedBatch(texts);
                
                // 3.3 Store (Batch)
                vectorStore.saveBatch(validBatchScenes, embeddings);
                
                int currentProcessed = processedCount.addAndGet(validBatchScenes.size());
                
                // 每批次结束线性上报
                // 限频规则：如果 totalBatches > 50，每 5 批上报一次，避免消息过密
                boolean shouldReport = (totalBatches <= 50) || (batchIndex % 5 == 0) || (batchIndex == totalBatches - 1);
                if (shouldReport) {
                    int progress = IngestProgress.calc(IngestProgress.EMBED_START, IngestProgress.EMBED_END, batchIndex, totalBatches);
                    progressCallback.accept(progress, String.format("向量化中：第 %d/%d 批，已处理 %d/%d 个场景", batchIndex + 1, totalBatches, currentProcessed, total));
                }

                if (currentProcessed % 100 == 0 || currentProcessed == total) {
                    log.info("Processed {}/{} scenes ({}%)", currentProcessed, total, (currentProcessed * 100 / total));
                }
                
            } catch (Exception e) {
                log.error("Error processing batch {}-{} (Scene IDs: {}-...)", i, end, batchScenes.get(0).getId(), e);
                throw new RuntimeException("Batch processing failed", e); 
            }
            batchIndex++;
        }
    }
}
