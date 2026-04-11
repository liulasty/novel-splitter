package com.novel.splitter.domain.task;

import com.novel.splitter.domain.enums.TaskType;

/**
 * 任务列表筛选（分页 + 可选条件）。
 */
public record SplitTaskFilter(
        String novelId,
        TaskType taskType,
        SplitTask.TaskStatus status,
        Long updatedFromMillis,
        Long updatedToMillis,
        int page,
        int size
) {
    public static SplitTaskFilter normalized(
            String novelId,
            TaskType taskType,
            SplitTask.TaskStatus status,
            Long updatedFromMillis,
            Long updatedToMillis,
            int page,
            int size) {
        int p = Math.max(0, page);
        int s = Math.min(500, Math.max(1, size));
        return new SplitTaskFilter(novelId, taskType, status, updatedFromMillis, updatedToMillis, p, s);
    }
}
