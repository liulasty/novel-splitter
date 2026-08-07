package com.novel.splitter.application.service.enrich;

import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对已有小说触发 re-enrich：收集指定版本（缺省用活动版本）的全部场景，
 * 交给 {@link EnrichPublisher} 按章投递（避免单消息承载整本书触发 MQ ack 超时）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReEnrichService {

    private final SceneRepository sceneRepository;
    private final NovelRepository novelRepository;
    private final EnrichPublisher enrichPublisher;

    public void reEnrich(String novelId, String version) {
        String id = novelId != null ? novelId.trim() : null;
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("novelId 不能为空");
        }
        // 始终校验小说存在（含软删过滤），避免显式 version 时 typo 的 novelId 静默返回 200
        Novel novel = novelRepository.findById(id).orElse(null);
        if (novel == null) {
            throw new IllegalArgumentException("小说不存在: " + id);
        }
        String resolved = version != null ? version.trim() : null;
        if (resolved == null || resolved.isBlank()) {
            if (novel.getActiveVersionTag() != null && !novel.getActiveVersionTag().isBlank()) {
                resolved = novel.getActiveVersionTag();
            }
        }
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalArgumentException("未指定 version 且小说无活动版本，无法 re-enrich");
        }
        List<Scene> scenes = sceneRepository.findAllByNovelIdAndVersion(id, resolved);
        if (scenes.isEmpty()) {
            log.warn("re-enrich：novelId={} version={} 无场景，跳过", id, resolved);
            return;
        }
        enrichPublisher.publishByChapter(null, id, resolved, scenes);
        log.info("re-enrich 已投递 {} 个场景：novelId={} version={}", scenes.size(), id, resolved);
    }
}
