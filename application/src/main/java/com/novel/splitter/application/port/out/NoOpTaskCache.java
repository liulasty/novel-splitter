package com.novel.splitter.application.port.out;

import com.novel.splitter.application.model.dto.PollResponse;
import org.springframework.stereotype.Component;

@Component
public class NoOpTaskCache implements TaskCachePort {

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
