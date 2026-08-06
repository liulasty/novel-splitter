package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.model.dto.SceneExtractionDto;
import com.novel.splitter.application.service.enrich.SceneSemanticExtractor;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.EnrichTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 语义抽取消费者：消费 novel.task.enrich，按章分组调用 LLM 抽取
 * characters/location/time/role 并写回 metadata_json。
 * 逐章降级：单章失败只记日志，不阻塞后续章、不失败整个任务。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EnrichWorker {

    private final SceneRepository sceneRepository;
    private final SceneSemanticExtractor extractor;

    @RabbitListener(queues = RabbitConfig.ENRICH_TASK_QUEUE)
    public void processEnrichTask(EnrichTaskMessage message) {
        if (message == null || message.getSceneIds() == null || message.getSceneIds().isEmpty()) {
            log.warn("Enrich 消息无场景 ID，忽略");
            return;
        }
        List<Scene> scenes = sceneRepository.findByIds(message.getSceneIds());
        if (scenes.isEmpty()) {
            log.warn("Enrich 未找到任何场景（{} 个 ID），novelId={} version={}",
                    message.getSceneIds().size(), message.getNovelId(), message.getVersion());
            return;
        }

        Map<Integer, List<Scene>> byChapter = new LinkedHashMap<>();
        for (Scene scene : scenes) {
            byChapter.computeIfAbsent(scene.getChapterIndex(), k -> new ArrayList<>()).add(scene);
        }

        List<Scene> updated = new ArrayList<>();
        for (Map.Entry<Integer, List<Scene>> entry : byChapter.entrySet()) {
            int chapterIndex = entry.getKey();
            List<Scene> chapterScenes = entry.getValue();
            try {
                List<SceneExtractionDto> extractions = extractor.extract(chapterScenes);
                Map<String, SceneExtractionDto> byId = new LinkedHashMap<>();
                for (SceneExtractionDto dto : extractions) {
                    byId.put(dto.getId(), dto);
                }
                for (Scene scene : chapterScenes) {
                    SceneExtractionDto dto = byId.get(scene.getId());
                    if (dto != null) {
                        apply(scene, dto);
                        updated.add(scene);
                    }
                }
                log.info("Enrich 章节 {} 完成：{}/{} 个场景抽取成功", chapterIndex, byId.size(), chapterScenes.size());
            } catch (Exception e) {
                log.warn("Enrich 章节 {} 失败，保留 null：{}", chapterIndex, e.toString());
            }
        }

        if (!updated.isEmpty()) {
            sceneRepository.updateScenesMetadata(updated);
            log.info("Enrich 已写回 {} 个场景的语义元数据（novelId={} version={}）",
                    updated.size(), message.getNovelId(), message.getVersion());
        }
    }

    private void apply(Scene scene, SceneExtractionDto dto) {
        SceneMetadata meta = scene.getMetadata();
        if (meta == null) {
            meta = new SceneMetadata();
            scene.setMetadata(meta);
        }
        if (dto.getCharacters() != null) {
            meta.setCharacters(dto.getCharacters());
        }
        if (dto.getLocation() != null) {
            meta.setLocation(dto.getLocation());
        }
        if (dto.getTime() != null) {
            meta.setTime(dto.getTime());
        }
        if (dto.getRole() != null) {
            meta.setRole(dto.getRole());
        }
    }
}
