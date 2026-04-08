package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.port.out.TaskCachePort;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.enums.TaskType;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.interfaces.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TaskPollControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TaskCachePort taskCachePort;

    @Mock
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        TaskPollController controller = new TaskPollController(taskCachePort, taskService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void pollTasks_should_accept_ids_param() throws Exception {
        SplitTask task = new SplitTask("task-1", TaskType.SPLIT, "novel-1", "a.txt", 0, "v1");
        task.startProcessing("running");

        when(taskCachePort.get(anyString())).thenReturn(null);
        when(taskService.getTasksByIds(List.of("task-1"))).thenReturn(List.of(task));

        mockMvc.perform(get("/api/tasks/poll").param("ids", "task-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].taskId").value("task-1"))
                .andExpect(jsonPath("$[0].status").value("PROCESSING"))
                .andExpect(jsonPath("$[0].serverTime").isNumber());
    }

    @Test
    void pollTasks_should_accept_taskIds_param_for_backward_compatibility() throws Exception {
        SplitTask task = new SplitTask("task-2", TaskType.EMBED, "novel-2", "b.txt", 0, "v1");
        task.startProcessing("running");

        when(taskCachePort.get(anyString())).thenReturn(null);
        when(taskService.getTasksByIds(List.of("task-2"))).thenReturn(List.of(task));

        mockMvc.perform(get("/api/tasks/poll").param("taskIds", "task-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskId").value("task-2"));
        verify(taskService).getTasksByIds(List.of("task-2"));
    }

    @Test
    void pollTasks_should_return_bad_request_when_missing_query_params() throws Exception {
        mockMvc.perform(get("/api/tasks/poll"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pollTasks_should_return_bad_request_when_more_than_20_ids() throws Exception {
        var requestBuilder = get("/api/tasks/poll");
        for (int i = 0; i < 21; i++) {
            requestBuilder = requestBuilder.param("ids", "task-" + i);
        }
        mockMvc.perform(requestBuilder)
                .andExpect(status().isBadRequest());
    }
}
