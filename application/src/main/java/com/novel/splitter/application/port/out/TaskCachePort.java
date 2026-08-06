package com.novel.splitter.application.port.out;

import com.novel.splitter.application.model.dto.PollResponse;

/**
 * 用于快速轮询的任务缓存端口。
 *
 * 约定：taskId 仅用于追踪，不得泄漏进业务存储。
 */
public interface TaskCachePort {
    PollResponse get(String taskId);
    void put(String taskId, PollResponse value);
    void evict(String taskId);
}
