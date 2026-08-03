package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.infrastructure.persistence.entity.JpaChapterEntity;
import com.novel.splitter.infrastructure.persistence.entity.JpaNovelEntity;
import com.novel.splitter.infrastructure.persistence.mapper.SceneMapper;
import com.novel.splitter.infrastructure.persistence.repository.JpaChapterRepository;
import com.novel.splitter.infrastructure.persistence.repository.JpaNovelRepository;
import com.novel.splitter.infrastructure.persistence.repository.JpaSceneRepository;
import jakarta.persistence.EntityManager;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scene 幂等落库契约：以 (novel_id, version, seq) 唯一约束为界，重复保存同 seq 无副作用；
 * {@code maxSeqByVersion} 按 version 隔离。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SceneIdempotentSaveTest {

    @Autowired
    private JpaSceneRepository jpaSceneRepository;
    @Autowired
    private JpaNovelRepository jpaNovelRepository;
    @Autowired
    private JpaChapterRepository jpaChapterRepository;
    @Autowired
    private EntityManager entityManager;

    private SceneRepositoryJpaImpl repo;

    @BeforeEach
    void setUp() {
        repo = new SceneRepositoryJpaImpl(jpaSceneRepository, jpaNovelRepository, jpaChapterRepository, new SceneMapper());
        org.springframework.test.util.ReflectionTestUtils.setField(repo, "entityManager", entityManager);
        JpaNovelEntity novel = JpaNovelEntity.builder()
                .id("n-idem").title("Test Novel").status(NovelStatus.PARSED)
                .createdAt(System.currentTimeMillis()).updatedAt(System.currentTimeMillis())
                .build();
        jpaNovelRepository.saveAndFlush(novel);
        jpaChapterRepository.saveAndFlush(JpaChapterEntity.builder()
                .novel(novel).title("第一章").indexNum(1).build());
    }

    private Scene scene(long seq) {
        return Scene.builder()
                .id("s" + seq)
                .chapterTitle("第一章")
                .chapterIndex(1)
                .startParagraphIndex(0)
                .endParagraphIndex(1)
                .text("正文正文正文正文正文正文正文正文正文正文正文正文正文正文")
                .wordCount(20)
                .seq(seq)
                .build();
    }

    @Test
    void savingSameSeqTwiceIsIdempotent() {
        List<Long> first = repo.saveScenesIdempotent("n-idem", "v1", 350, 65, List.of(scene(1), scene(2)));
        List<Long> second = repo.saveScenesIdempotent("n-idem", "v1", 350, 65, List.of(scene(1), scene(2)));

        assertEquals(2, first.size(), "首次保存应写入 2 条");
        assertTrue(second.isEmpty(), "重复保存同 seq 应无副作用（返回空）");
        assertEquals(2, repo.maxSeqByVersion("n-idem", "v1"), "maxSeq 应为已写入的最大 seq");
    }

    @Test
    void maxSeqIsScopedToVersion() {
        // 同一 novel、同一 seq、不同 version 可并存
        List<Long> v1 = repo.saveScenesIdempotent("n-idem", "v1", 350, 65, List.of(scene(1)));
        List<Long> v2 = repo.saveScenesIdempotent("n-idem", "v2", 350, 65, List.of(scene(1)));

        assertEquals(1, v1.size());
        assertEquals(1, v2.size());
        assertEquals(1, repo.maxSeqByVersion("n-idem", "v1"));
        assertEquals(1, repo.maxSeqByVersion("n-idem", "v2"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackages = "com.novel.splitter.infrastructure.persistence.repository")
    @EntityScan(basePackages = "com.novel.splitter.infrastructure.persistence.entity")
    static class TestConfig {
    }
}
