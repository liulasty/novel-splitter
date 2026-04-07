package com.novel.splitter.pipeline.orchestrator;

import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.core.SceneAssembler;
import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.ChapterData;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.strategy.ChunkingStrategy;
import com.novel.splitter.domain.strategy.OverlapChunkingStrategy;
import com.novel.splitter.domain.task.IngestProgress;
import com.novel.splitter.domain.repository.SceneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

@Service
@Slf4j
@RequiredArgsConstructor
public class SplitNovelUseCase {

    private final NovelCacheRepository novelCacheRepository;
    private final SceneRepository sceneRepository;
    
    private final SceneAssembler sceneAssembler = new SceneAssembler();

    @Value("${splitter.rule.min-length:50}")
    private int minLength;

    @Value("${splitter.ingestion.chunk-size:500}")
    private int chunkSize;

    @Value("${splitter.ingestion.chunk-overlap:100}")
    private int chunkOverlap;

    private List<Scene> filterByLength(List<Scene> scenes) {
        List<Scene> valid = new ArrayList<>();
        for (Scene s : scenes) {
            int len = s.getText() == null ? 0 : s.getText().length();
            if (len < minLength) {
                log.warn("Scene {} (chapter {}) too short: {} chars, skipping",
                        s.getId(), s.getChapterTitle(), len);
                continue;
            }
            valid.add(s);
        }
        return valid;
    }

    public List<Long> split(String taskId, String novelId, Novel novel, int maxScenes, String version, BiConsumer<Integer, String> progressCallback) {
        log.info("=== Start Split Phase for: {} ===", novel.getTitle());
        
        List<Scene> scenes = new ArrayList<>();
        List<Chapter> chapters = novel.getChapters();
        int totalChapters = chapters.size();
        int scenesCount = 0;
        
        if (progressCallback != null) {
            progressCallback.accept(IngestProgress.CHAPTER_END, String.format("准备逐章切分，共 %d 章", totalChapters));
        }

        ChunkingStrategy chunkingStrategy = new OverlapChunkingStrategy(chunkSize, chunkOverlap);

        for (int i = 0; i < totalChapters; i++) {
            Chapter chapter = chapters.get(i);
            
            // Load chapter data from cache
            ChapterData chapterData = novelCacheRepository.loadChapter(taskId, chapter.getIndex());
            
            List<Scene> chapterScenes = sceneAssembler.assembleChapter(chapter, chapterData.getParagraphs(), novel.getTitle());
            
            // Apply fine-grained chunking immediately before DB save
            List<Scene> chunkedScenes = new ArrayList<>();
            for (Scene s : chapterScenes) {
                chunkedScenes.addAll(chunkingStrategy.split(s));
            }
            
            // Set absolute chunk index for ordering
            for (Scene s : chunkedScenes) {
                s.setChunkIndex(scenesCount++);
            }
            
            scenes.addAll(chunkedScenes);
            
            if (progressCallback != null && (i % 10 == 0 || i == totalChapters - 1)) {
                int progress = IngestProgress.calc(IngestProgress.SCENE_START, IngestProgress.SCENE_END, i + 1, totalChapters);
                progressCallback.accept(progress, String.format("正在切分章节：%d/%d，已生成 %d 个场景", i + 1, totalChapters, scenesCount));
            }
            
            if (maxScenes > 0 && scenes.size() >= maxScenes) {
                break;
            }
        }

        log.info("Generated {} scenes from novel '{}'", scenes.size(), novel.getTitle());

        if (progressCallback != null) {
            progressCallback.accept(IngestProgress.VALIDATE_END, String.format("切分完成：共 %d 个初步场景", scenes.size()));
        }

        scenes = filterByLength(scenes);
        if (scenes.isEmpty()) {
            log.warn("All scenes filtered out after length validation for: {}", novel.getTitle());
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

        if (progressCallback != null) {
            progressCallback.accept(IngestProgress.SAVE_START, "正在保存场景到本地存储...");
        }
        List<Long> sceneIds = sceneRepository.saveScenes(novelId, novel.getTitle(), finalVersion, scenes);
        if (progressCallback != null) {
            progressCallback.accept(IngestProgress.SAVE_END, String.format("本地存储完成，共 %d 个场景", scenes.size()));
        }
        
        return sceneIds;
    }
}
