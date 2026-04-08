package com.novel.splitter.application.port.out;

import com.novel.splitter.application.model.dto.PollResponse;

public interface TaskCachePort {
    PollResponse get(String taskId);
    void put(String taskId, PollResponse pollResponse);
    void evict(String taskId);
}
