package com.novel.splitter.application.service.task;

import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.application.model.dto.SplitTaskDto;
import com.novel.splitter.application.model.dto.SplitTaskPageDto;
import com.novel.splitter.application.service.novel.NovelService;
import com.novel.splitter.domain.enums.TaskType;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.SplitTaskFilter;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TaskQueryService {
    private static final String DELETED_OR_MISSING_NOVEL_TITLE = "已删除或不存在";

    private final TaskService taskService;
    private final DtoMapper dtoMapper;
    private final NovelService novelService;

    public TaskQueryService(TaskService taskService, DtoMapper dtoMapper, NovelService novelService) {
        this.taskService = taskService;
        this.dtoMapper = dtoMapper;
        this.novelService = novelService;
    }

    public List<SplitTaskDto> getAllTasksWithNovelTitle() {
        List<SplitTaskDto> dtos = dtoMapper.toSplitTaskDtos(taskService.getAllTasks());
        Map<String, String> titleCache = new HashMap<>();
        for (SplitTaskDto dto : dtos) {
            String novelId = dto.getNovelId();
            if (novelId == null || novelId.isBlank()) {
                continue;
            }
            String title = titleCache.computeIfAbsent(novelId, this::resolveNovelTitle);
            dto.setNovelTitle(title);
        }
        return dtos;
    }

    public SplitTaskDto getTaskWithNovelTitle(String taskId) {
        SplitTask task = taskService.getTask(taskId);
        if (task == null) {
            return null;
        }
        SplitTaskDto dto = dtoMapper.toSplitTaskDto(task);
        if (dto.getNovelId() != null && !dto.getNovelId().isBlank()) {
            dto.setNovelTitle(resolveNovelTitle(dto.getNovelId()));
        }
        return dto;
    }

    /**
     * 分页 + 可选筛选（novelId / taskType / status / 更新时间范围）。
     */
    public SplitTaskPageDto findTasksPage(
            String novelId,
            String taskTypeRaw,
            String statusRaw,
            Long updatedFromMillis,
            Long updatedToMillis,
            int page,
            int size) {
        TaskType taskType = parseTaskType(taskTypeRaw);
        SplitTask.TaskStatus status = parseStatus(statusRaw);
        SplitTaskFilter filter = SplitTaskFilter.normalized(
                novelId, taskType, status, updatedFromMillis, updatedToMillis, page, size);
        PagedResult<SplitTask> paged = taskService.findTasksFiltered(filter);
        List<SplitTaskDto> dtos = dtoMapper.toSplitTaskDtos(paged.getContent());
        Map<String, String> titleCache = new HashMap<>();
        for (SplitTaskDto dto : dtos) {
            String nid = dto.getNovelId();
            if (nid == null || nid.isBlank()) {
                continue;
            }
            String title = titleCache.computeIfAbsent(nid, this::resolveNovelTitle);
            dto.setNovelTitle(title);
        }
        return SplitTaskPageDto.builder()
                .content(dtos)
                .page(paged.getPage())
                .size(paged.getSize())
                .totalElements(paged.getTotalElements())
                .build();
    }

    private static TaskType parseTaskType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TaskType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static SplitTask.TaskStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return SplitTask.TaskStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String resolveNovelTitle(String novelId) {
        try {
            Novel novel = novelService.getNovelById(novelId);
            return novel != null ? novel.getTitle() : DELETED_OR_MISSING_NOVEL_TITLE;
        } catch (IllegalArgumentException ex) {
            return DELETED_OR_MISSING_NOVEL_TITLE;
        }
    }
}

