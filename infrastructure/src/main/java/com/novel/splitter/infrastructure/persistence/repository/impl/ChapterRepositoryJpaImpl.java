package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.repository.ChapterRepository;
import com.novel.splitter.infrastructure.persistence.entity.JpaChapterEntity;
import com.novel.splitter.infrastructure.persistence.entity.JpaNovelEntity;
import com.novel.splitter.infrastructure.persistence.mapper.ChapterMapper;
import com.novel.splitter.infrastructure.persistence.repository.JpaChapterRepository;
import com.novel.splitter.infrastructure.persistence.repository.JpaNovelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChapterRepositoryJpaImpl implements ChapterRepository {

    private final JpaChapterRepository jpaChapterRepository;
    private final JpaNovelRepository jpaNovelRepository;
    private final ChapterMapper chapterMapper = ChapterMapper.INSTANCE;

    @Override
    public void saveAll(List<Chapter> chapters) {
        if (chapters.isEmpty()) return;
        
        String novelId = chapters.get(0).getNovelId();
        JpaNovelEntity novel = jpaNovelRepository.findById(novelId).orElse(null);
        
        List<JpaChapterEntity> entities = chapters.stream().map(c -> {
            JpaChapterEntity entity = chapterMapper.toEntity(c);
            if (novel != null) {
                entity.setNovel(novel);
            }
            return entity;
        }).collect(Collectors.toList());
        
        jpaChapterRepository.saveAll(entities);
    }

    @Override
    public List<Chapter> findByNovelId(String novelId) {
        return jpaChapterRepository.findByNovelIdOrderByIndexNumAsc(novelId)
                .stream()
                .map(chapterMapper::toDomain)
                .collect(Collectors.toList());
    }
}