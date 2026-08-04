package com.novel.splitter.application.service.ingest;

import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.repository.ChapterRepository;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.domain.repository.NovelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 入库原子回滚：删除新建 Novel 的 DB 行、原始/parsed 文件产物与章节数据。
 * 幂等：novel 不存在或 novelId 空白时直接返回。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestRollbackService {

    private final NovelRepository novelRepository;
    private final ChapterRepository chapterRepository;
    private final NovelCacheRepository novelCacheRepository;

    public void rollback(String novelId) {
        if (novelId == null || novelId.isBlank()) {
            return;
        }
        Novel novel = novelRepository.findById(novelId).orElse(null);
        if (novel == null) {
            log.info("入库回滚跳过：novel {} 不存在", novelId);
            return;
        }
        // 文件产物（raw + parsed）整体清理；removeNovelArtifacts 内部已吞异常，尽力而为
        novelCacheRepository.removeNovelArtifacts(novelId);
        // 章节兜底（理论无半成品：replaceAll 单事务，未提交则不落库）
        chapterRepository.deleteByNovelId(novelId);
        // 硬删 DB 行
        novelRepository.hardDelete(novelId);
        log.info("入库回滚完成：已删除 novel {}", novelId);
    }
}
