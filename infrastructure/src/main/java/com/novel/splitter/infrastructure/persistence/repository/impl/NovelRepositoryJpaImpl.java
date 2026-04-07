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
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 结合 JPA 与本地文件系统实现的小说仓库
 */
@Service
@RequiredArgsConstructor
public class NovelRepositoryJpaImpl implements NovelRepository {

    private final JpaNovelRepository jpaNovelRepository;
    private final NovelMapper novelMapper = NovelMapper.INSTANCE;

    @Override
    public void save(Novel novel) {
        JpaNovelEntity entity = novelMapper.toEntity(novel);
        jpaNovelRepository.save(entity);
    }

    @Override
    public Optional<Novel> findById(String id) {
        return jpaNovelRepository.findById(id).map(novelMapper::toDomain);
    }

    @Override
    public Optional<Novel> findByFileMd5(String fileMd5) {
        return jpaNovelRepository.findByFileMd5(fileMd5).map(novelMapper::toDomain);
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
