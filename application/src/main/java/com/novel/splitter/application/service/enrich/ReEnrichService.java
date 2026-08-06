package com.novel.splitter.application.service.enrich;

import com.novel.splitter.application.port.out.TaskQueuePort;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.EnrichTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 对已有小说触发 re-enrich：收集指定版本（缺省用活动版本）的全部场景 ID，
 * 投递 EnrichTaskMessage 到 novel.task.enrich 队列。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReEnrichService {

    private final SceneRepository sceneRepository;
    private final NovelRepository novelRepository;
    private final TaskQueuePort taskQueuePort;

    public void reEnrich(String novelId, String version) {
        String resolved = version;
        if (resolved == null || resolved.isBlank()) {
            Novel novel = novelRepository.findById(novelId).orElse(null);
            if (novel != null && novel.getActiveVersionTag() != null && !novel.getActiveVersionTag().isBlank()) {
                resolved = novel.getActiveVersionTag();
            }
        }
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalArgumentException("未指定 version 且小说无活动版本，无法 re-enrich");
        }
        List<Scene> scenes = sceneRepository.findAllByNovelIdAndVersion(novelId, resolved);
        List<Long> sceneIds = scenes.stream().map(Scene::getPersistenceId).collect(Collectors.toList());
        if (sceneIds.isEmpty()) {
            log.warn("re-enrich：novelId={} version={} 无场景，跳过", novelId, resolved);
            return;
        }
        taskQueuePort.sendEnrich(new EnrichTaskMessage(null, novelId, resolved, sceneIds));
        log.info("re-enrich 已投递 {} 个场景：novelId={} version={}", sceneIds.size(), novelId, resolved);
    }
}
