package com.novel.splitter.application.service.task;

import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.application.model.dto.SplitTaskDto;
import com.novel.splitter.application.service.novel.NovelService;
import com.novel.splitter.domain.model.Novel;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TaskQueryService {

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
            String title = titleCache.computeIfAbsent(novelId, id -> {
                Novel novel = novelService.getNovelById(id);
                return novel != null ? novel.getTitle() : null;
            });
            dto.setNovelTitle(title);
        }
        return dtos;
    }

    public SplitTaskDto getTaskWithNovelTitle(String taskId) {
        SplitTaskDto dto = dtoMapper.toSplitTaskDto(taskService.getTask(taskId));
        if (dto != null && dto.getNovelId() != null && !dto.getNovelId().isBlank()) {
            Novel novel = novelService.getNovelById(dto.getNovelId());
            dto.setNovelTitle(novel != null ? novel.getTitle() : null);
        }
        return dto;
    }
}

