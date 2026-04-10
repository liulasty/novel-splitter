package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.model.dto.PollResponse;
import com.novel.splitter.application.service.task.TaskPollService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks/poll")
@Tag(name = "任务轮询", description = "智能轮询任务状态接口")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class TaskPollController {

    private final TaskPollService taskPollService;

    @GetMapping
    @Operation(
            summary = "轮询任务状态",
            description = "支持 ids/taskIds（最多20个）或 novelId 兜底查询。novelId 路径返回非终态优先，其次近24小时终态。"
    )
    public List<PollResponse> pollTasks(
            @RequestParam(required = false, name = "ids") List<String> ids,
            @RequestParam(required = false, name = "taskIds") List<String> taskIds,
            @RequestParam(required = false, name = "novelId") String novelId) {
        return taskPollService.pollTasks(ids, taskIds, novelId);
    }
}
