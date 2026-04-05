package com.novel.splitter.application.service;

import com.novel.splitter.application.service.etl.NovelIngestionService;
import com.novel.splitter.application.service.novel.NovelFacadeServiceImpl;
import com.novel.splitter.application.service.novel.NovelStorageService;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.model.dto.IngestRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Map;

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
    private NovelIngestionService novelIngestionService;

    @InjectMocks
    private NovelFacadeServiceImpl novelFacadeService;

    @Test
    void shouldNormalizeDefaultsAndDispatchAsyncIngest() throws Exception {
        IngestRequest request = new IngestRequest();
        request.setFileName("demo.txt");

        when(novelStorageService.resolveExistingNovelPath("demo.txt")).thenReturn(Path.of("D:/novels/demo.txt"));

        Map<String, String> result = novelFacadeService.ingest(request);

        ArgumentCaptor<String> taskIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> novelIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> maxScenesCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> versionCaptor = ArgumentCaptor.forClass(String.class);

        verify(taskService).createTask(taskIdCaptor.capture(), novelIdCaptor.capture(), org.mockito.Mockito.eq("demo.txt"),
                maxScenesCaptor.capture(), versionCaptor.capture());
        verify(novelIngestionService).ingestAsync(org.mockito.Mockito.eq(taskIdCaptor.getValue()), org.mockito.Mockito.eq(novelIdCaptor.getValue()),
                org.mockito.Mockito.eq(Path.of("D:/novels/demo.txt").toAbsolutePath().toString()),
                org.mockito.Mockito.eq(Integer.MAX_VALUE), org.mockito.Mockito.eq("v1"));

        assertTrue(result.containsKey("taskId"));
        assertEquals("入库任务已提交到队列", result.get("message"));
        assertEquals("demo", novelIdCaptor.getValue());
        assertEquals(Integer.MAX_VALUE, maxScenesCaptor.getValue());
        assertEquals("v1", versionCaptor.getValue());
    }
}
