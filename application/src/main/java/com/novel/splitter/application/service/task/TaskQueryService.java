package com.novel.splitter.application.service.task;

import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.application.model.dto.SplitTaskDto;
import com.novel.splitter.application.service.novel.NovelService;
import com.novel.splitter.domain.model.Novel;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        Map<String, Optional<String>> titleCache = new HashMap<>();
        for (SplitTaskDto dto : dtos) {
            String novelId = dto.getNovelId();
            if (novelId == null || novelId.isBlank()) {
                continue;
            }
            String title = titleCache.computeIfAbsent(novelId, id -> Optional.of(resolveNovelTitle(id))).orElse(null);
            dto.setNovelTitle(title);
        }
        return dtos;
    }

    public SplitTaskDto getTaskWithNovelTitle(String taskId) {
        SplitTaskDto dto = dtoMapper.toSplitTaskDto(taskService.getTask(taskId));
        if (dto != null && dto.getNovelId() != null && !dto.getNovelId().isBlank()) {
            dto.setNovelTitle(resolveNovelTitle(dto.getNovelId()));
        }
        return dto;
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

