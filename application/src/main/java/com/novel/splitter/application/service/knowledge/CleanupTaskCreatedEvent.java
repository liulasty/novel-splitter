package com.novel.splitter.application.service.knowledge;

import com.novel.splitter.domain.task.CleanupTaskMessage;

/**
 * 删除事务内发布；由 {@code @TransactionalEventListener(AFTER_COMMIT)} 消费，在事务提交后发送 MQ，
 * 避免 worker 在任务记录提交前消费而查不到（"Cleanup task not found" 竞态）。
 */
public class CleanupTaskCreatedEvent {

    private final CleanupTaskMessage message;

    public CleanupTaskCreatedEvent(CleanupTaskMessage message) {
        this.message = message;
    }

    public CleanupTaskMessage getMessage() {
        return message;
    }
}
