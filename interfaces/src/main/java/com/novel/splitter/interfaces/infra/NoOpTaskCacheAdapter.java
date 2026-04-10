package com.novel.splitter.interfaces.infra;

import com.novel.splitter.application.model.dto.PollResponse;
import com.novel.splitter.application.port.out.TaskCachePort;
import org.springframework.stereotype.Component;

@Component
public class NoOpTaskCacheAdapter implements TaskCachePort {

    @Override
    public PollResponse get(String taskId) {
        return null;
    }

    @Override
    public void put(String taskId, PollResponse pollResponse) {
        // No-op
    }

    @Override
    public void evict(String taskId) {
        // No-op
    }
}