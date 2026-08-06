package com.novel.splitter.application.service.novel;

import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.repository.ChapterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChapterServiceImpl implements ChapterService {

    private final ChapterRepository chapterRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveChapters(List<Chapter> chapters) {
        if (chapters != null && !chapters.isEmpty()) {
            chapterRepository.saveAll(chapters);
            log.info("已批量保存 {} 个章节。", chapters.size());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceAll(String novelId, List<Chapter> chapters) {
        chapterRepository.deleteByNovelId(novelId);
        if (chapters != null && !chapters.isEmpty()) {
            chapterRepository.saveAll(chapters);
        }
        log.info("已替换小说 {} 的章节基准 (共 {} 章)", novelId,
                chapters != null ? chapters.size() : 0);
    }

    @Override
    public List<Chapter> getChaptersByNovelId(String novelId) {
        return chapterRepository.findByNovelId(novelId);
    }

    @Override
    public boolean hasChapters(String novelId) {
        return chapterRepository.existsByNovelId(novelId);
    }

    @Override
    @Transactional
    public void deleteByNovelId(String novelId) {
        chapterRepository.deleteByNovelId(novelId);
    }
}
