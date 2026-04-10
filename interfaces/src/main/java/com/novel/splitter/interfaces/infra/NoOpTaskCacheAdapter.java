package com.novel.splitter.interfaces.infra;

import com.novel.splitter.application.model.dto.PollResponse;
import com.novel.splitter.application.port.out.TaskCachePort;
import org.springframework.stereotype.Component;

/**
 * Default task cache adapter: disabled (no-op).
 * Can be replaced by a real cache adapter (Caffeine/Redis) when needed.
 */
@Component
public class NoOpTaskCacheAdapter implements TaskCachePort {
    @Override
    public PollResponse get(String taskId) {
        return null;
    }

    @Override
    public void put(String taskId, PollResponse value) {
        // no-op
    }

    @Override
    public void evict(String taskId) {
        // no-op
    }
}

