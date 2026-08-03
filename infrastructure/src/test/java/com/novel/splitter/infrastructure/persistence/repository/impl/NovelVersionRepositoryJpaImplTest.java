package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.enums.SplitStrategy;
import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import com.novel.splitter.infrastructure.persistence.mapper.NovelVersionMapper;
import com.novel.splitter.infrastructure.persistence.repository.JpaNovelVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JpaNovelVersionRepository 集成测试：复合主键存取、按 novelId 有序查询、超时停滞扫描。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class NovelVersionRepositoryJpaImplTest {

    @Autowired
    private JpaNovelVersionRepository jpa;

    private NovelVersionRepository repo;

    @BeforeEach
    void setUp() {
        repo = new NovelVersionRepositoryJpaImpl(jpa, new NovelVersionMapper());
    }

    private NovelVersion version(String tag) {
        return NovelVersion.builder()
                .novelId("n-test").versionTag(tag)
                .splitStrategy(SplitStrategy.OVERLAP_CHUNK)
                .chunkSize(350).chunkOverlap(65)
                .status(VersionStatus.PENDING)
                .createdAt(System.currentTimeMillis()).updatedAt(System.currentTimeMillis())
                .build();
    }

    @Test
    void saveAndFindByCompositeId() {
        repo.save(version("v1"));
        Optional<NovelVersion> found = repo.findById("n-test", "v1");
        assertTrue(found.isPresent());
        assertEquals("v1", found.get().getVersionTag());
        assertEquals(SplitStrategy.OVERLAP_CHUNK, found.get().getSplitStrategy());
    }

    @Test
    void findByNovelIdReturnsAllVersionsOrdered() {
        repo.save(version("v2"));
        repo.save(version("v1"));
        List<NovelVersion> all = repo.findByNovelId("n-test");
        assertEquals(2, all.size());
        assertEquals("v1", all.get(0).getVersionTag());
        assertEquals("v2", all.get(1).getVersionTag());
    }

    @Test
    void findStalledReturnsOnlyStalledInStatuses() {
        NovelVersion stalled = version("v-stall");
        stalled.setStatus(VersionStatus.EMBEDDING);
        stalled.setUpdatedAt(System.currentTimeMillis() - 10_000);
        NovelVersion fresh = version("v-fresh");
        fresh.setStatus(VersionStatus.EMBEDDING);
        repo.save(stalled);
        repo.save(fresh);
        List<NovelVersion> stalledOnes = repo.findStalled(List.of(VersionStatus.SPLITTING, VersionStatus.EMBEDDING),
                System.currentTimeMillis() - 5_000);
        assertEquals(1, stalledOnes.size());
        assertEquals("v-stall", stalledOnes.get(0).getVersionTag());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackages = "com.novel.splitter.infrastructure.persistence.repository")
    @EntityScan(basePackages = "com.novel.splitter.infrastructure.persistence.entity")
    static class TestConfig {
    }
}
