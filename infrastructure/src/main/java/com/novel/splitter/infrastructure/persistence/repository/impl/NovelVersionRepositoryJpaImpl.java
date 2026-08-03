package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import com.novel.splitter.infrastructure.persistence.entity.NovelVersionId;
import com.novel.splitter.infrastructure.persistence.mapper.NovelVersionMapper;
import com.novel.splitter.infrastructure.persistence.repository.JpaNovelVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * NovelVersionRepository 的 JPA 实现，委托 {@link JpaNovelVersionRepository} + {@link NovelVersionMapper}。
 */
@Repository
@RequiredArgsConstructor
public class NovelVersionRepositoryJpaImpl implements NovelVersionRepository {

    private final JpaNovelVersionRepository jpa;
    private final NovelVersionMapper mapper;

    @Override
    @Transactional
    public void save(NovelVersion version) {
        jpa.save(mapper.toEntity(Objects.requireNonNull(version, "version must not be null")));
    }

    @Override
    public Optional<NovelVersion> findById(String novelId, String versionTag) {
        return jpa.findById(new NovelVersionId(novelId, versionTag)).map(mapper::toDomain);
    }

    @Override
    public List<NovelVersion> findByNovelId(String novelId) {
        return jpa.findById_NovelIdOrderById_VersionTagAsc(novelId).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(String novelId, String versionTag) {
        jpa.deleteById(new NovelVersionId(novelId, versionTag));
    }

    @Override
    @Transactional
    public void deleteByNovelId(String novelId) {
        jpa.deleteById_NovelId(novelId);
    }

    @Override
    public List<NovelVersion> findStalled(List<VersionStatus> statuses, long beforeUpdatedAt) {
        return jpa.findStalled(statuses, beforeUpdatedAt).stream().map(mapper::toDomain).toList();
    }
}
