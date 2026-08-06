package com.novel.splitter.interfaces.infra;

import com.novel.splitter.application.model.dto.PollResponse;
import com.novel.splitter.application.port.out.TaskCachePort;
import org.springframework.stereotype.Component;

/**
 * 默认任务缓存适配器：禁用（no-op）。
 * 需要时可由真实缓存适配器（Caffeine/Redis）替换。
 */
@Component
public class NoOpTaskCacheAdapter implements TaskCachePort {
    @Override
    public PollResponse get(String taskId) {
        return null;
    }

    @Override
    public void put(String taskId, PollResponse value) {
        // 空操作
    }

    @Override
    public void evict(String taskId) {
        // 空操作
    }
}

