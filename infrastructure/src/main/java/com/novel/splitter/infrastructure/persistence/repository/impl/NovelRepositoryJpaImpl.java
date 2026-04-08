package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.infrastructure.io.FileUtils;
import com.novel.splitter.infrastructure.persistence.entity.JpaNovelEntity;
import com.novel.splitter.infrastructure.persistence.mapper.NovelMapper;
import com.novel.splitter.infrastructure.persistence.repository.JpaNovelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 结合 JPA 与本地文件系统实现的小说仓库
 */
@Service
@RequiredArgsConstructor
public class NovelRepositoryJpaImpl implements NovelRepository {

    private final JpaNovelRepository jpaNovelRepository;
    private final NovelMapper novelMapper;

    @Override
    public void save(Novel novel) {
        String novelId = Objects.requireNonNull(novel.getId(), "novel.id must not be null");
        JpaNovelEntity entity = Objects.requireNonNull(
                jpaNovelRepository.findById(novelId).orElseGet(JpaNovelEntity::new),
                "entity must not be null"
        );
        novelMapper.toEntity(entity, novel);
        jpaNovelRepository.save(entity);
    }

    @Override
    public Optional<Novel> findById(String id) {
        String novelId = Objects.requireNonNull(id, "id must not be null");
        return jpaNovelRepository.findById(novelId).map(novelMapper::toDomain);
    }

    @Override
    public List<Novel> findAll() {
        return jpaNovelRepository.findAll().stream()
                .map(novelMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> loadRaw(Path path) throws IOException {
        return FileUtils.readLinesAutoDetectEncoding(path);
    }
}
