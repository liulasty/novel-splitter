package com.novel.splitter.pipeline.orchestrator;

import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.core.SceneAssembler;
import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.ChapterData;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.strategy.ChunkingStrategy;
import com.novel.splitter.domain.strategy.OverlapChunkingStrategy;
import com.novel.splitter.domain.task.IngestProgress;
import com.novel.splitter.domain.repository.ChapterRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.pipeline.model.ResolvedChunkingParams;
import com.novel.splitter.validation.core.SceneQualityScoreWriter;
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
    private final ChapterRepository chapterRepository;

    private final SceneAssembler sceneAssembler = new SceneAssembler();

    @Value("${splitter.rule.min-length:50}")
    private int minLength;

    @Value("${splitter.rule.max-length:3000}")
    private int maxLength;

    @Value("${splitter.ingestion.chunk-size:350}")
    private int chunkSize;

    @Value("${splitter.ingestion.chunk-overlap:65}")
    private int chunkOverlap;

    /**
     * 场景落库批次大小。与 {@code splitter.ingestion.batch-size}（Embed 向量化批次）区分，避免配置语义冲突。
     */
    @Value("${splitter.split.scene-persist-batch-size:1000}")
    private int scenePersistBatchSize;

    /**
     * 切分终点：本次实际写入的 persistenceId 列表、最后贡献场景的章节索引、本章结束后的全局 seq。
     * 下一次续传从 {@code (lastChapterIndex + 1, lastSceneSeq)} 开始。
     */
    public record SplitProgress(List<Long> sceneIds, int lastChapterIndex, long lastSceneSeq) {
    }

    private List<Scene> filterByLength(List<Scene> scenes) {
        List<Scene> valid = new ArrayList<>();
        for (Scene s : scenes) {
            String text = s.getText();
            // 统计非空白字符数（元数据较重的场景会因换行符导致长度虚高）
            int contentLen = text == null ? 0 : text.replaceAll("\\s+", "").length();
            if (contentLen < minLength) {
                log.warn("场景 {}（章节 {}）过短：有效内容 {} 字符（原始 {} 字符），已跳过",
                        s.getId(), s.getChapterTitle(), contentLen, text != null ? text.length() : 0);
                continue;
            }
            valid.add(s);
        }
        return valid;
    }

    private int effectivePersistBatchSize() {
        return scenePersistBatchSize < 1 ? 1000 : scenePersistBatchSize;
    }

    private int capRemaining(int maxScenes, int savedCount, int batchSize) {
        if (maxScenes <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, maxScenes - savedCount - batchSize);
    }

    /**
     * 与 {@link #split} 内部使用的滑窗参数计算一致，供 SplitWorker 等在落库前做幂等删除。
     */
    public ResolvedChunkingParams resolveChunkingParams(Integer overrideChunkSize, Integer overrideChunkOverlap) {
        int effectiveChunk = (overrideChunkSize != null && overrideChunkSize > 0) ? overrideChunkSize : chunkSize;
        int effectiveOverlap = (overrideChunkOverlap != null && overrideChunkOverlap >= 0) ? overrideChunkOverlap : chunkOverlap;
        if (effectiveChunk <= 0) {
            effectiveChunk = chunkSize;
        }
        if (effectiveOverlap < 0) {
            effectiveOverlap = chunkOverlap;
        }
        if (effectiveOverlap >= effectiveChunk) {
            effectiveOverlap = Math.max(0, effectiveChunk - 1);
        }
        return new ResolvedChunkingParams(effectiveChunk, effectiveOverlap);
    }

    public List<Long> split(String taskId, String novelId, String novelTitle, int maxScenes, String version, BiConsumer<Integer, String> progressCallback) {
        return split(taskId, novelId, novelTitle, maxScenes, version, null, null, progressCallback);
    }

    /**
     * @param overrideChunkSize    非空且 &gt;0 时覆盖全局 chunkSize
     * @param overrideChunkOverlap 非空且 ≥0 时覆盖全局 chunkOverlap（会校验 &lt; 有效块大小）
     */
    public List<Long> split(String taskId, String novelId, String novelTitle, int maxScenes, String version,
                            Integer overrideChunkSize, Integer overrideChunkOverlap,
                            BiConsumer<Integer, String> progressCallback) {
        return split(taskId, novelId, novelTitle, maxScenes, version, overrideChunkSize, overrideChunkOverlap,
                0, 0L, progressCallback).sceneIds();
    }

    /**
     * 每章 checkpoint 续传的切分入口：从 {@code startChapterIndex} 章开始，全局 seq 从 {@code startSceneSeq} 起
     * 为每个有效场景单调 +1；落库使用 {@code saveScenesIdempotent}（以 (novelId, version, seq) 唯一约束为界），
     * 因此重复执行/从中断处续传均无副作用。
     *
     * @param startChapterIndex 起始章节索引（含），续传时传上次 {@link SplitProgress#lastChapterIndex()} + 1
     * @param startSceneSeq     起始全局 seq，续传时传上次 {@link SplitProgress#lastSceneSeq()}（首个新场景 = +1）
     * @return 切分终点：sceneIds=本次实际写入的 persistenceId；lastChapterIndex=最后贡献场景的章节索引；
     *         lastSceneSeq=结束后的全局 seq
     */
    public SplitProgress split(String taskId, String novelId, String novelTitle, int maxScenes, String version,
                               Integer overrideChunkSize, Integer overrideChunkOverlap,
                               int startChapterIndex, long startSceneSeq,
                               BiConsumer<Integer, String> progressCallback) {
        log.info("=== 开始切分阶段：novelId={}，title={} ===", novelId, novelTitle);

        int persistBatch = effectivePersistBatchSize();
        List<Scene> batchScenes = new ArrayList<>(persistBatch);
        List<Long> allSavedSceneIds = new ArrayList<>();
        List<Chapter> chapters = chapterRepository.findByNovelId(novelId);
        int totalChapters = chapters.size();
        int scenesCount = 0;
        int totalValidAccepted = 0;
        int lastProgress = IngestProgress.CHAPTER_END;
        long seq = startSceneSeq;
        int lastChapterIndex = -1;
        int fromChapter = Math.max(0, startChapterIndex);

        if (progressCallback != null) {
            progressCallback.accept(lastProgress, String.format("准备逐章切分，共 %d 章，从第 %d 章续传（起始 seq=%d）",
                    totalChapters, fromChapter + 1, startSceneSeq));
        }

        ResolvedChunkingParams resolved = resolveChunkingParams(overrideChunkSize, overrideChunkOverlap);
        int effectiveChunk = resolved.chunkSize();
        int effectiveOverlap = resolved.chunkOverlap();

        ChunkingStrategy chunkingStrategy = new OverlapChunkingStrategy(effectiveChunk, effectiveOverlap);
        String finalVersion = (version != null && !version.isBlank()) ? version : "v1-ingestion";

        boolean savePhaseStarted = false;

        for (int i = fromChapter; i < totalChapters; i++) {
            if (maxScenes > 0 && allSavedSceneIds.size() >= maxScenes) {
                break;
            }

            Chapter chapter = chapters.get(i);

            ChapterData chapterData = novelCacheRepository.loadChapter(novelId, chapter.getIndex());

            List<Scene> chapterScenes = sceneAssembler.assembleChapter(chapter, chapterData.getParagraphs(), novelId);

            List<Scene> chunkedScenes = new ArrayList<>();
            for (Scene s : chapterScenes) {
                if (s.getMetadata() != null) {
                    s.getMetadata().setVersion(finalVersion);
                    s.getMetadata().setChunkSize(effectiveChunk);
                    s.getMetadata().setChunkOverlap(effectiveOverlap);
                }
                chunkedScenes.addAll(chunkingStrategy.split(s));
            }

            scenesCount += chunkedScenes.size();

            List<Scene> validScenes = filterByLength(chunkedScenes);

            SceneQualityScoreWriter.apply(validScenes, minLength, maxLength);

            int cap = capRemaining(maxScenes, allSavedSceneIds.size(), batchScenes.size());
            if (cap <= 0) {
                break;
            }
            if (validScenes.size() > cap) {
                validScenes = new ArrayList<>(validScenes.subList(0, cap));
            }

            // 全局连续 seq：每个有效场景分配唯一 (novelId, version, seq)；同时回写 metadata.sequenceNum 保持同步
            for (Scene s : validScenes) {
                seq += 1;
                s.setSeq(seq);
                if (s.getMetadata() != null) {
                    s.getMetadata().setSequenceNum((int) seq);
                }
            }
            lastChapterIndex = i;

            batchScenes.addAll(validScenes);
            totalValidAccepted += validScenes.size();

            while (batchScenes.size() >= persistBatch) {
                if (!savePhaseStarted && progressCallback != null) {
                    lastProgress = Math.max(lastProgress, IngestProgress.SAVE_START);
                    progressCallback.accept(lastProgress, "正在分批保存场景到本地存储...");
                    savePhaseStarted = true;
                }
                List<Scene> toSave = new ArrayList<>(batchScenes.subList(0, persistBatch));
                batchScenes.subList(0, persistBatch).clear();
                allSavedSceneIds.addAll(sceneRepository.saveScenesIdempotent(
                        novelId, finalVersion, effectiveChunk, effectiveOverlap, toSave));
                if (progressCallback != null) {
                    int denom = Math.max(1, Math.max(totalValidAccepted, allSavedSceneIds.size()));
                    int p = IngestProgress.calc(IngestProgress.SAVE_START, IngestProgress.SAVE_END,
                            allSavedSceneIds.size(), denom);
                    if (p < lastProgress) {
                        p = lastProgress;
                    } else {
                        lastProgress = p;
                    }
                    progressCallback.accept(p, String.format("已落盘 %d 个有效场景（预估总量约 %d）", allSavedSceneIds.size(), totalValidAccepted));
                }
            }

            if (progressCallback != null && (i % 10 == 0 || i == totalChapters - 1)) {
                int p = IngestProgress.calc(IngestProgress.SCENE_START, IngestProgress.SCENE_END, i + 1, totalChapters);
                if (p < lastProgress) {
                    p = lastProgress;
                } else {
                    lastProgress = p;
                }
                progressCallback.accept(p, String.format(
                        "正在切分章节：%d/%d，已生成 %d 个场景片段，已落盘 %d 个有效场景",
                        i + 1, totalChapters, scenesCount, allSavedSceneIds.size()));
            }

            if (maxScenes > 0 && scenesCount >= maxScenes) {
                break;
            }
            if (maxScenes > 0 && allSavedSceneIds.size() >= maxScenes) {
                break;
            }
        }

        log.info("已生成 {} 个场景片段（长度过滤前），接受 {} 个，novelId={}，title='{}'",
                scenesCount, totalValidAccepted, novelId, novelTitle);

        if (progressCallback != null) {
            lastProgress = Math.max(lastProgress, IngestProgress.VALIDATE_END);
            progressCallback.accept(lastProgress, String.format(
                    "切分完成：共 %d 个有效场景（长度校验后），已落盘 %d 个",
                    totalValidAccepted, allSavedSceneIds.size()));
        }

        if (!batchScenes.isEmpty()) {
            if (!savePhaseStarted && progressCallback != null) {
                lastProgress = Math.max(lastProgress, IngestProgress.SAVE_START);
                progressCallback.accept(lastProgress, "正在保存剩余场景到本地存储...");
            }
            allSavedSceneIds.addAll(sceneRepository.saveScenesIdempotent(
                    novelId, finalVersion, effectiveChunk, effectiveOverlap, batchScenes));
            batchScenes.clear();
        }

        if (allSavedSceneIds.isEmpty()) {
            log.warn("切分后无场景落盘，novelId={}", novelId);
            if (progressCallback != null) {
                lastProgress = Math.max(lastProgress, IngestProgress.SAVE_END);
                progressCallback.accept(lastProgress, "无有效场景落盘");
            }
            return new SplitProgress(new ArrayList<>(), lastChapterIndex, seq);
        }

        if (progressCallback != null) {
            lastProgress = Math.max(lastProgress, IngestProgress.SAVE_END);
            progressCallback.accept(lastProgress, String.format("本地存储完成，共 %d 个场景", allSavedSceneIds.size()));
        }

        return new SplitProgress(allSavedSceneIds, lastChapterIndex, seq);
    }
}
