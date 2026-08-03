package com.novel.splitter.application.service.knowledge.impl;

import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.application.service.knowledge.CleanupTaskCreatedEvent;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.repository.CleanupTaskRepository;
import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.CleanupTask;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceDeleteAsyncTest {

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
    void deleteKnowledgeBaseById_publishesEventAndReturnsCleanupTaskId_withoutDirectMqSend() {
        when(novelRepository.findById("n1")).thenReturn(Optional.of(Novel.builder().id("n1").title("测试书").build()));
        when(novelVersionRepository.findByNovelId("n1")).thenReturn(List.of());
        CleanupTask task = CleanupTask.builder().id(99L).targetId("n1").targetType("NOVEL_ID").status("PENDING").build();
        when(cleanupTaskRepository.save(any(CleanupTask.class))).thenReturn(task);

        Long id = service.deleteKnowledgeBaseById("n1", false);

        assertEquals(99L, id);
        verify(sceneRepository).deleteNovelById("n1");
        verify(applicationEventPublisher).publishEvent(any(CleanupTaskCreatedEvent.class));
        // MQ 发送已移出事务方法（改由 AFTER_COMMIT 处理器发出），删除方法本身不再直接发
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void deleteVersion_publishesEventAndReturnsId() {
        when(novelRepository.findByTitle("测试书")).thenReturn(Optional.of(Novel.builder().id("n1").title("测试书").build()));
        CleanupTask task = CleanupTask.builder().id(7L).targetId("n1").targetType("VERSION").status("PENDING").build();
        when(cleanupTaskRepository.save(any(CleanupTask.class))).thenReturn(task);

        Long id = service.deleteVersion("测试书", "v2", 512, 64, false);

        assertEquals(7L, id);
        verify(sceneRepository).deleteByProfile("n1", "v2", 512, 64);
        verify(applicationEventPublisher).publishEvent(any(CleanupTaskCreatedEvent.class));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}
