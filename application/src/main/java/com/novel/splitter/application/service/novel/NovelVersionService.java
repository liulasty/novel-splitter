package com.novel.splitter.application.service.novel;

import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.embedding.api.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 版本原子激活服务。
 * <p>
 * 在 EMBED_DONE 版本就绪后，单事务完成：
 * <ol>
 *   <li>校验向量集合已创建</li>
 *   <li>降级同小说已有 ACTIVE 版本</li>
 *   <li>target.activate() 并设 collectionName</li>
 *   <li>更新 Novel.activeVersionTag 指针</li>
 * </ol>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NovelVersionService {

    private final NovelVersionRepository novelVersionRepository;
    private final NovelRepository novelRepository;
    private final SceneRepository sceneRepository;
    private final VectorStore vectorStore;

    /**
     * 激活指定版本，使其成为检索所用的活跃版本。
     *
     * @param novelId    小说 ID
     * @param versionTag 版本标签
     * @throws IllegalArgumentException 版本不存在
     * @throws IllegalStateException    状态不是 EMBED_DONE，或向量集合缺失
     */
    @Transactional(rollbackFor = Exception.class)
    public void activate(String novelId, String versionTag) {
        NovelVersion target = novelVersionRepository.findById(novelId, versionTag)
                .orElseThrow(() -> new IllegalArgumentException("版本不存在: " + versionTag));

        if (target.getStatus() != VersionStatus.EMBED_DONE) {
            throw new IllegalStateException("只有 EMBED_DONE 版本才能激活: " + target.getStatus());
        }

        String collectionName = VectorStore.collectionNameFor(novelId, versionTag);

        if (!vectorStore.collectionExists(collectionName)) {
            throw new IllegalStateException("向量集合未就绪: " + collectionName);
        }

        // 设置（或覆盖）collectionName
        target.setCollectionName(collectionName);

        // 旧 ACTIVE 降级为 EMBED_DONE
        novelVersionRepository.findByNovelId(novelId).stream()
                .filter(v -> v.getStatus() == VersionStatus.ACTIVE)
                .forEach(old -> {
                    old.setStatus(VersionStatus.EMBED_DONE);
                    novelVersionRepository.save(old);
                    log.info("Downgraded old ACTIVE version {}/{} -> EMBED_DONE", novelId, old.getVersionTag());
                });

        target.activate();
        novelVersionRepository.save(target);

        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new IllegalArgumentException("小说不存在: " + novelId));
        novel.setActiveVersionTag(versionTag);
        novelRepository.save(novel);

        log.info("Activated version {}/{} -> ACTIVE (collection={})", novelId, versionTag, collectionName);
    }
}
