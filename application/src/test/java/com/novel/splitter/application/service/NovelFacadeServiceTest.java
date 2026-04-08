package com.novel.splitter.application.service;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.model.dto.TaskSubmitResponseDto;
import com.novel.splitter.application.service.novel.NovelFacadeServiceImpl;
import com.novel.splitter.application.service.novel.NovelStorageService;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.application.model.dto.IngestRequest;
import com.novel.splitter.domain.enums.TaskType;
import com.novel.splitter.domain.task.SplitTaskMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NovelFacadeServiceTest {

    @Mock
    private NovelStorageService novelStorageService;

    @Mock
    private TaskService taskService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private NovelFacadeServiceImpl novelFacadeService;

    @Test
    void shouldNormalizeDefaultsAndDispatchAsyncIngest() throws Exception {
        IngestRequest request = new IngestRequest();
        request.setFileName("demo.txt");

        when(novelStorageService.resolveExistingNovelPath("demo.txt")).thenReturn(Path.of("D:/novels/demo.txt"));

        TaskSubmitResponseDto result = novelFacadeService.ingest(request);

        ArgumentCaptor<String> taskIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> novelIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> maxScenesCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> versionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SplitTaskMessage> messageCaptor = ArgumentCaptor.forClass(SplitTaskMessage.class);

        verify(taskService).createTask(taskIdCaptor.capture(), org.mockito.Mockito.eq(TaskType.SPLIT), novelIdCaptor.capture(), org.mockito.Mockito.eq("demo.txt"),
                maxScenesCaptor.capture(), versionCaptor.capture());
        
        verify(rabbitTemplate).convertAndSend(org.mockito.Mockito.eq(RabbitConfig.EXCHANGE_NAME), org.mockito.Mockito.eq("load"), messageCaptor.capture());

        SplitTaskMessage message = messageCaptor.getValue();
        assertEquals(taskIdCaptor.getValue(), message.getTaskId());
        assertEquals(novelIdCaptor.getValue(), message.getNovelId());
        assertEquals(Integer.MAX_VALUE, message.getMaxScenes());
        assertEquals("v1", message.getVersion());

        assertTrue(result.getTaskId() != null && !result.getTaskId().isEmpty());
        assertEquals("入库任务已提交到队列", result.getMessage());
        assertEquals("demo", novelIdCaptor.getValue());
        assertEquals(Integer.MAX_VALUE, maxScenesCaptor.getValue());
        assertEquals("v1", versionCaptor.getValue());
    }
}
