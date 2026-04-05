package com.novel.splitter.application.service.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.domain.task.TaskProgressEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.novel.splitter.domain.task.SplitTask;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Service
public class TaskSseService {

    // Supports multiple connections for the same task
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private TaskService taskService;

    public SseEmitter connect(String taskId) {
        // Timeout 300000ms (5 mins)
        SseEmitter emitter = new SseEmitter(300000L);
        
        emittersMap.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable removeCallback = () -> {
            CopyOnWriteArrayList<SseEmitter> list = emittersMap.get(taskId);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    emittersMap.remove(taskId);
                }
            }
        };

        emitter.onCompletion(removeCallback);
        emitter.onTimeout(() -> {
            emitter.complete();
            removeCallback.run();
        });
        emitter.onError(e -> {
            emitter.completeWithError(e);
            removeCallback.run();
        });

        // Send initial progress snapshot immediately upon connection
        SplitTask task = taskService.getTask(taskId);
        if (task != null) {
            try {
                TaskProgressEvent event = new TaskProgressEvent(
                        taskId,
                        task.getProgress(),
                        task.getMessage(),
                        task.getStatus() != null ? task.getStatus().name() : "PENDING"
                );
                event.setTimestamp(System.currentTimeMillis());
                
                emitter.send(SseEmitter.event()
                        .name("progress")
                        .data(objectMapper.writeValueAsString(event)));
            } catch (Exception e) {
                log.warn("Failed to send initial SSE progress for taskId: {}", taskId, e);
            }
        }

        return emitter;
    }

    public void broadcast(TaskProgressEvent event) {
        String taskId = event.getTaskId();
        CopyOnWriteArrayList<SseEmitter> emitters = emittersMap.get(taskId);
        
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        String payload;
        try {
            event.setTimestamp(System.currentTimeMillis());
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize TaskProgressEvent", e);
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("progress")
                        .data(payload));
                
                if ("SUCCESS".equals(event.getStatus()) || "FAILED".equals(event.getStatus())) {
                    emitter.complete();
                }
            } catch (Exception e) {
                log.warn("Failed to send SSE progress to one of the clients for taskId: {}", taskId, e);
                emitter.completeWithError(e);
                emitters.remove(emitter);
            }
        }
        
        if ("SUCCESS".equals(event.getStatus()) || "FAILED".equals(event.getStatus())) {
            emittersMap.remove(taskId);
        }
    }
}
