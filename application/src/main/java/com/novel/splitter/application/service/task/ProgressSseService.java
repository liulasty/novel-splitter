package com.novel.splitter.application.service.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.domain.task.SplitTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ProgressSseService {

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private TaskService taskService;

    public SseEmitter connect(String taskId) {
        // 超时 300000ms (5分钟)
        SseEmitter emitter = new SseEmitter(300000L);
        emitters.put(taskId, emitter);

        emitter.onCompletion(() -> emitters.remove(taskId));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(taskId);
        });
        emitter.onError(e -> {
            emitter.completeWithError(e);
            emitters.remove(taskId);
        });

        // Send initial progress snapshot immediately upon connection
        SplitTask task = taskService.getTask(taskId);
        if (task != null) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("taskId", taskId);
                payload.put("progress", task.getProgress());
                payload.put("message", task.getMessage());
                payload.put("status", task.getStatus() != null ? task.getStatus().name() : "PENDING");
                payload.put("timestamp", System.currentTimeMillis());
                
                emitter.send(SseEmitter.event()
                        .name("progress")
                        .data(objectMapper.writeValueAsString(payload)));
            } catch (Exception e) {
                log.warn("Failed to send initial SSE progress for taskId: {}", taskId, e);
            }
        }

        return emitter;
    }

    public void send(String taskId, int progress, String message, String status) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter == null) {
            // 如果 taskId 无对应连接则静默跳过（不抛异常）
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("taskId", taskId);
            payload.put("progress", progress);
            payload.put("message", message);
            payload.put("status", status);
            payload.put("timestamp", System.currentTimeMillis());

            emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(objectMapper.writeValueAsString(payload)));
        } catch (Exception e) {
            log.warn("Failed to send SSE progress for taskId: {}", taskId, e);
            emitter.completeWithError(e);
            emitters.remove(taskId);
        }
    }

    public void complete(String taskId) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("Failed to complete SSE for taskId: {}", taskId, e);
            } finally {
                emitters.remove(taskId);
            }
        }
    }
}
