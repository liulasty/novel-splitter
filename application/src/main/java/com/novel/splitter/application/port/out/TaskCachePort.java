package com.novel.splitter.application.port.out;

import com.novel.splitter.application.model.dto.PollResponse;

/**
 * Task cache port for fast polling.
 *
 * Contract: taskId is tracking-only and must not leak into business storage.
 */
public interface TaskCachePort {
    PollResponse get(String taskId);
    void put(String taskId, PollResponse value);
    void evict(String taskId);
}
