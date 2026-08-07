package com.novel.splitter.application.service.enrich;

import com.novel.splitter.application.port.out.TaskQueuePort;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.task.EnrichTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 按章节切分投递 enrich 消息。
 * <p>
 * RabbitMQ 默认 consumer_timeout 为 30 分钟：单消息承载整本书时，EnrichWorker 逐章串行调用 LLM，
 * 消息处理时长可能远超 30 分钟而无法及时 ack，触发 PRECONDITION_FAILED 通道关闭、消息反复重入队。
 * 每章一条消息后，单消息仅一次 LLM 调用（约 1 分钟内），天然规避该超时，且章节间失败互不影响。
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EnrichPublisher {

    private final TaskQueuePort taskQueuePort;

    /**
     * 将场景按 chapterIndex 分组，逐章投递一条 {@link EnrichTaskMessage}。
     * 无有效场景则不投递。
     *
     * @param parentTaskId 关联的切分任务 ID（手动 re-enrich 时为 null）
     */
    public void publishByChapter(String parentTaskId, String novelId, String version, List<Scene> scenes) {
        if (scenes == null || scenes.isEmpty()) {
            return;
        }
        Map<Integer, List<Long>> byChapter = scenes.stream()
                .filter(s -> s.getPersistenceId() != null)
                .collect(Collectors.groupingBy(
                        Scene::getChapterIndex,
                        Collectors.mapping(Scene::getPersistenceId, Collectors.toList())));
        if (byChapter.isEmpty()) {
            return;
        }
        byChapter.forEach((chapter, ids) ->
                taskQueuePort.sendEnrich(new EnrichTaskMessage(parentTaskId, novelId, version, ids)));
        log.info("enrich 已按章投递 {} 条消息：novelId={} version={} scenes={}",
                byChapter.size(), novelId, version, scenes.size());
    }
}
