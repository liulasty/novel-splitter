package com.novel.splitter.interfaces.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "统计大盘", description = "提供系统状态、问答大盘、健康检查相关统计信息")
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class StatsController {

    @Operation(summary = "获取 Dashboard 统计信息")
    @GetMapping("/stats/dashboard")
    public Map<String, Object> getDashboardStats() {
        // 由于没有设计 qa 历史表，返回模拟数据以满足 P1 阶段前端对接需求
        return Map.of(
                "qaCount", 1250,
                "todayQaCount", 42,
                "avgRetrievalTimeMs", 215,
                "retrievalTimeTrend", "↓ 18%"
        );
    }

    @Operation(summary = "获取模型健康状态")
    @GetMapping("/system/health/models")
    public Map<String, Boolean> getModelsHealth() {
        // 简单返回 true，代表服务正常，可在此处扩展真实的 ping 逻辑
        return Map.of(
                "embeddingModelLoaded", true,
                "llmBackendReachable", true
        );
    }
}
