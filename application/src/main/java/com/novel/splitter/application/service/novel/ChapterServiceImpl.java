package com.novel.splitter.application.service.novel;

import com.novel.splitter.domain.entity.JpaChapterEntity;
import com.novel.splitter.repository.api.JpaChapterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChapterServiceImpl implements ChapterService {

    private final JpaChapterRepository jpaChapterRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveChapters(List<JpaChapterEntity> chapters) {
        if (chapters != null && !chapters.isEmpty()) {
            jpaChapterRepository.saveAll(chapters);
            log.info("Saved {} chapters in batch.", chapters.size());
        }
    }

    @Override
    public List<JpaChapterEntity> getChaptersByNovelId(String novelId) {
        return jpaChapterRepository.findByNovelIdOrderByIndexNumAsc(novelId);
    }
}
