package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.model.dto.PollResponse;
import com.novel.splitter.application.service.task.TaskPollService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TaskPollControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TaskPollService taskPollService;

    @BeforeEach
    void setUp() {
        TaskPollController controller = new TaskPollController(taskPollService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void pollTasks_should_accept_ids_param() throws Exception {
        PollResponse task = PollResponse.builder()
                .taskId("task-1")
                .status("PROCESSING")
                .serverTime(System.currentTimeMillis())
                .build();
        when(taskPollService.pollTasks(List.of("task-1"), null, null)).thenReturn(List.of(task));

        mockMvc.perform(get("/api/tasks/poll").param("ids", "task-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].taskId").value("task-1"))
                .andExpect(jsonPath("$[0].status").value("PROCESSING"))
                .andExpect(jsonPath("$[0].serverTime").isNumber());
    }

    @Test
    void pollTasks_should_accept_taskIds_param_for_backward_compatibility() throws Exception {
        PollResponse task = PollResponse.builder()
                .taskId("task-2")
                .status("PROCESSING")
                .serverTime(System.currentTimeMillis())
                .build();
        when(taskPollService.pollTasks(null, List.of("task-2"), null)).thenReturn(List.of(task));

        mockMvc.perform(get("/api/tasks/poll").param("taskIds", "task-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskId").value("task-2"));
        verify(taskPollService).pollTasks(null, List.of("task-2"), null);
    }

    @Test
    void pollTasks_should_return_bad_request_when_missing_query_params() throws Exception {
        when(taskPollService.pollTasks(null, null, null))
                .thenThrow(new IllegalArgumentException("Either ids/taskIds or novelId must be provided"));
        mockMvc.perform(get("/api/tasks/poll"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pollTasks_should_return_bad_request_when_more_than_20_ids() throws Exception {
        var requestBuilder = get("/api/tasks/poll");
        for (int i = 0; i < 21; i++) {
            requestBuilder = requestBuilder.param("ids", "task-" + i);
        }
        when(taskPollService.pollTasks(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenThrow(new IllegalArgumentException("Maximum 20 task IDs are allowed"));
        mockMvc.perform(requestBuilder)
                .andExpect(status().isBadRequest());
    }
}
