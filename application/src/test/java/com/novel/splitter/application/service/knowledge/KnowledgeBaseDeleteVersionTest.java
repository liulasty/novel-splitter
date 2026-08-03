package com.novel.splitter.application.service.knowledge;

import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.application.service.knowledge.impl.KnowledgeBaseServiceImpl;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.domain.repository.CleanupTaskRepository;
import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.CleanupTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 级联删除契约测试：删除版本/整书时，novel_version 行与专属向量集合随 scenes 一并覆盖。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseDeleteVersionTest {

    @Mock private SceneRepository sceneRepository;
    @Mock private NovelRepository novelRepository;
    @Mock private NovelVersionRepository novelVersionRepository;
    @Mock private CleanupTaskRepository cleanupTaskRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private DtoMapper dtoMapper;
    @Mock private TaskService taskService;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private KnowledgeBaseServiceImpl service;

    @Test
    void deleteVersion_removesVersionRowAndScenes_andQueuesCleanup_leavesOtherVersionsUntouched() {
        when(novelRepository.findByTitle("测试书")).thenReturn(Optional.of(Novel.builder().id("n1").title("测试书").build()));
        CleanupTask task = CleanupTask.builder().id(7L).targetId("n1").targetType("VERSION").status("PENDING").build();
        when(cleanupTaskRepository.save(any(CleanupTask.class))).thenReturn(task);

        Long id = service.deleteVersion("测试书", "v1", 512, 64, false);

        assertEquals(7L, id);
        // 该版本 scenes 同步软删
        verify(sceneRepository).deleteByProfile("n1", "v1", 512, 64);
        // 追加：删除该版本 novel_version 行
        verify(novelVersionRepository).delete("n1", "v1");
        // 其它版本不受影响：不得触发整书版本删除
        verify(novelVersionRepository, never()).deleteByNovelId(anyString());
        // 异步清理照旧落库 + 事务后发事件
        verify(cleanupTaskRepository).save(any(CleanupTask.class));
        verify(applicationEventPublisher).publishEvent(any(CleanupTaskCreatedEvent.class));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void deleteSplitProfileByNovelId_removesVersionRowAndScenes_andQueuesCleanup() {
        CleanupTask task = CleanupTask.builder().id(8L).targetId("n1").targetType("VERSION_BY_NOVEL_ID").status("PENDING").build();
        when(cleanupTaskRepository.save(any(CleanupTask.class))).thenReturn(task);
        when(novelRepository.findById("n1")).thenReturn(Optional.of(Novel.builder().id("n1").title("测试书").build()));

        Long id = service.deleteSplitProfileByNovelId("n1", "v2", 350, 65, false);

        assertEquals(8L, id);
        verify(sceneRepository).deleteByProfile("n1", "v2", 350, 65);
        verify(novelVersionRepository).delete("n1", "v2");
        verify(novelVersionRepository, never()).deleteByNovelId(anyString());
        verify(cleanupTaskRepository).save(any(CleanupTask.class));
        verify(applicationEventPublisher).publishEvent(any(CleanupTaskCreatedEvent.class));
    }

    @Test
    void deleteKnowledgeBaseById_removesAllVersionRowsAndScenes_andQueuesCleanup() {
        when(novelRepository.findById("n1")).thenReturn(Optional.of(Novel.builder().id("n1").title("测试书").build()));
        when(novelVersionRepository.findByNovelId("n1")).thenReturn(List.of(
                NovelVersion.builder().novelId("n1").versionTag("v1").status(VersionStatus.ACTIVE).build(),
                NovelVersion.builder().novelId("n1").versionTag("v2").status(VersionStatus.EMBED_DONE).build()));
        CleanupTask task = CleanupTask.builder().id(99L).targetId("n1").targetType("NOVEL_ID").status("PENDING").build();
        when(cleanupTaskRepository.save(any(CleanupTask.class))).thenReturn(task);

        Long id = service.deleteKnowledgeBaseById("n1", false);

        assertEquals(99L, id);
        verify(sceneRepository).deleteNovelById("n1");
        // 追加：删除该小说全部版本行
        verify(novelVersionRepository).deleteByNovelId("n1");
        verify(cleanupTaskRepository).save(any(CleanupTask.class));
        verify(applicationEventPublisher).publishEvent(any(CleanupTaskCreatedEvent.class));
    }
}
