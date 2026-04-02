package com.novel.splitter.application.service.etl;

import com.novel.splitter.core.SceneAssembler;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.repository.api.SceneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final SceneRepository sceneRepository;
    
    // 实例化切分器 (也可配置为 Bean)
    private final SceneAssembler sceneAssembler = new SceneAssembler();
    
    // 批处理大小 (根据显存和 Chroma 性能调整)
    private static final int BATCH_SIZE = 10; 

    /**
     * 执行入库流程 (兼容旧接口)
     */
    public void ingest(Path novelPath, int maxScenes) {
        ingest(novelPath, maxScenes, null, null);
    }

    /**
     * 执行入库流程
     * @param novelPath 本地 TXT 文件路径
     * @param maxScenes 最大处理场景数 (-1 表示不限制)
     * @param version   版本标识 (可选)
     */
    public void ingest(Path novelPath, int maxScenes, String version) {
        ingest(novelPath, maxScenes, version, null);
    }

    /**
     * 执行入库流程，支持进度回调
     * @param novelPath 本地 TXT 文件路径
     * @param maxScenes 最大处理场景数 (-1 表示不限制)
     * @param version   版本标识 (可选)
     * @param progressCallback 进度回调 (进度 10~99, 描述信息)
     */
    public void ingest(Path novelPath, int maxScenes, String version, BiConsumer<Integer, String> progressCallback) {
        try {
            log.info("=== Start Ingestion for: {} ===", novelPath);
            if (progressCallback != null) {
                progressCallback.accept(10, "开始加载本地文件...");
            }
            
            // 1. Load (加载)
            Novel novel = novelLoader.load(novelPath);
            
            if (progressCallback != null) {
                progressCallback.accept(30, "开始语义切分...");
            }
            // 2. Split (语义切分)
            log.info("Splitting novel into scenes...");
            List<Scene> scenes = sceneAssembler.assemble(novel.getChapters(), novel.getParagraphs(), novel.getTitle());
            log.info("Generated {} scenes from novel '{}'", scenes.size(), novel.getTitle());
            
            if (scenes.isEmpty()) {
                log.warn("No scenes generated! Check split rules or input file.");
                return;
            }

            // Apply limit if set
            if (maxScenes > 0 && scenes.size() > maxScenes) {
                log.warn("Limiting ingestion to first {} scenes (Total: {})", maxScenes, scenes.size());
                scenes = scenes.subList(0, maxScenes);
            }
            
            // Set version in metadata
            String finalVersion = (version != null && !version.isBlank()) ? version : "v1-ingestion";
            scenes.forEach(s -> {
                if (s.getMetadata() != null) {
                    s.getMetadata().setVersion(finalVersion);
                }
            });

            if (progressCallback != null) {
                progressCallback.accept(40, "保存场景到本地存储...");
            }
            // 3. Persist Scenes to Disk (for Retrieval hydration)
            log.info("Saving scenes to repository (version: {})...", finalVersion);
            sceneRepository.saveScenes(novel.getTitle(), finalVersion, scenes);

            // 4. Embed & Store (向量化入库)
            processBatches(scenes, progressCallback);
            
            if (progressCallback != null) {
                progressCallback.accept(99, "向量化入库完成...");
            }
            log.info("=== Ingestion Completed Successfully ===");
            
        } catch (Exception e) {
            log.error("Failed to ingest novel", e);
            throw new RuntimeException(e);
        }
    }
    
    private void processBatches(List<Scene> scenes, BiConsumer<Integer, String> progressCallback) {
        int total = scenes.size();
        AtomicInteger processedCount = new AtomicInteger(0);
        
        log.info("Starting embedding and storage for {} scenes...", total);
        
        for (int i = 0; i < total; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, total);
            List<Scene> batchScenes = scenes.subList(i, end);
            
            try {
                // 3.1 Extract texts
                List<String> texts = new ArrayList<>();
                for (Scene scene : batchScenes) {
                    texts.add(scene.getText());
                }
                
                // 3.2 Embed (Batch)
                // 注意：embeddingService.embedBatch 需要底层支持 (目前 OnnxEmbeddingService 支持)
                List<float[]> embeddings = embeddingService.embedBatch(texts);
                
                // 3.3 Store (Batch)
                vectorStore.saveBatch(batchScenes, embeddings);
                
                int currentProcessed = processedCount.addAndGet(batchScenes.size());
                
                if (progressCallback != null) {
                    int progress = 40 + (int) ((currentProcessed * 100.0 / total) * 0.5); // 40 to 90
                    progressCallback.accept(progress, String.format("正在向量化入库: %d/%d", currentProcessed, total));
                }

                if (currentProcessed % 100 == 0 || currentProcessed == total) {
                    log.info("Processed {}/{} scenes ({}%)", currentProcessed, total, (currentProcessed * 100 / total));
                }
                
            } catch (Exception e) {
                log.error("Error processing batch {}-{} (Scene IDs: {}-...)", i, end, batchScenes.get(0).getId(), e);
                throw new RuntimeException("Batch processing failed", e); 
            }
        }
    }
}
