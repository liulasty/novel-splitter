package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.model.dto.DlqRequeueResultDto;
import com.novel.splitter.application.model.dto.DlqStatDto;
import com.novel.splitter.application.service.task.DlqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "死信队列", description = "DLQ 积压监控与一键重投")
@RestController
@RequestMapping("/api/system/dlq")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class DlqController {

    private final DlqService dlqService;

    @Operation(summary = "各 DLQ 队列消息积压统计")
    @GetMapping("/stats")
    public List<DlqStatDto> stats() {
        return dlqService.stats();
    }

    @Operation(summary = "将指定 DLQ 中的消息重投回主任务交换机（单次最多 maxMessages 条，可多次调用直至清空）")
    @PostMapping("/{queueName:.+}/requeue")
    public DlqRequeueResultDto requeue(
            @PathVariable("queueName") String queueName,
            @RequestParam(value = "maxMessages", required = false, defaultValue = "10000") int maxMessages
    ) {
        return dlqService.requeue(queueName, maxMessages);
    }
}
