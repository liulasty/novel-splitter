package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.domain.repository.SplitTaskRepository;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.SplitTaskFilter;
import com.novel.splitter.infrastructure.persistence.entity.JpaSplitTaskEntity;
import com.novel.splitter.infrastructure.persistence.mapper.SplitTaskMapper;
import com.novel.splitter.infrastructure.persistence.repository.JpaSplitTaskRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SplitTaskRepositoryJpaImpl implements SplitTaskRepository {

    private final JpaSplitTaskRepository jpaSplitTaskRepository;
    private final SplitTaskMapper mapper;

    @Override
    public void save(SplitTask task) {
        JpaSplitTaskEntity entity = mapper.toEntity(task);
        jpaSplitTaskRepository.save(Objects.requireNonNull(entity, "entity must not be null"));
    }

    @Override
    public Optional<SplitTask> findById(String taskId) {
        return jpaSplitTaskRepository.findById(Objects.requireNonNull(taskId, "taskId must not be null"))
                .map(entity -> {
                    SplitTask task = mapper.toDomain(entity);
                    if (entity.getCompletedScenes() > 0) {
                        task.getCompletedScenes().set(entity.getCompletedScenes());
                    }
                    return task;
                });
    }

    @Override
    public List<SplitTask> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jpaSplitTaskRepository.findByTaskIdIn(ids).stream()
                .map(entity -> {
                    SplitTask task = mapper.toDomain(entity);
                    if (entity.getCompletedScenes() > 0) {
                        task.getCompletedScenes().set(entity.getCompletedScenes());
                    }
                    return task;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<SplitTask> findRecentByNovelId(String novelId, int limit) {
        if (novelId == null || novelId.isBlank()) {
            return List.of();
        }
        // Spring Data 方法固定查询 top50；调用方传入的 limit 需 <= 50。
        return jpaSplitTaskRepository.findTop50ByNovelIdOrderByUpdatedAtDesc(novelId).stream()
                .limit(Math.max(0, limit))
                .map(entity -> {
                    SplitTask task = mapper.toDomain(entity);
                    if (entity.getCompletedScenes() > 0) {
                        task.getCompletedScenes().set(entity.getCompletedScenes());
                    }
                    return task;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<SplitTask> findAll() {
        return jpaSplitTaskRepository.findAll().stream()
                .map(entity -> {
                    SplitTask task = mapper.toDomain(entity);
                    if (entity.getCompletedScenes() > 0) {
                        task.getCompletedScenes().set(entity.getCompletedScenes());
                    }
                    return task;
                })
                .collect(Collectors.toList());
    }

    @Override
    public PagedResult<SplitTask> findFiltered(SplitTaskFilter filter) {
        Objects.requireNonNull(filter, "filter");
        Specification<JpaSplitTaskEntity> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (filter.novelId() != null && !filter.novelId().isBlank()) {
                ps.add(cb.equal(root.get("novelId"), filter.novelId().trim()));
            }
            if (filter.taskType() != null) {
                ps.add(cb.equal(root.get("taskType"), filter.taskType()));
            }
            if (filter.status() != null) {
                ps.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.updatedFromMillis() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("updatedAt"), filter.updatedFromMillis()));
            }
            if (filter.updatedToMillis() != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("updatedAt"), filter.updatedToMillis()));
            }
            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(new Predicate[0]));
        };
        Page<JpaSplitTaskEntity> page = jpaSplitTaskRepository.findAll(
                spec,
                PageRequest.of(filter.page(), filter.size(), Sort.by(Sort.Direction.DESC, "updatedAt"))
        );
        List<SplitTask> content = page.getContent().stream()
                .map(entity -> {
                    SplitTask task = mapper.toDomain(entity);
                    if (entity.getCompletedScenes() > 0) {
                        task.getCompletedScenes().set(entity.getCompletedScenes());
                    }
                    return task;
                })
                .collect(Collectors.toList());
        return PagedResult.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Override
    public void deleteById(String taskId) {
        jpaSplitTaskRepository.deleteById(Objects.requireNonNull(taskId, "taskId must not be null"));
    }

    @Override
    public List<String> findTaskIdsByNovelIdAndStatuses(String novelId, List<SplitTask.TaskStatus> statuses) {
        if (novelId == null || novelId.isBlank() || statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        return jpaSplitTaskRepository
                .findByNovelIdAndStatusIn(novelId.trim(), statuses)
                .stream()
                .map(JpaSplitTaskEntity::getTaskId)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<String> findTaskIdsByNovelIdAndVersionAndStatuses(String novelId, String version, List<SplitTask.TaskStatus> statuses) {
        if (novelId == null || novelId.isBlank() || version == null || version.isBlank() || statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        return jpaSplitTaskRepository
                .findByNovelIdAndVersionAndStatusIn(novelId.trim(), version.trim(), statuses)
                .stream()
                .map(JpaSplitTaskEntity::getTaskId)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public void deleteAllByIds(Collection<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }
        jpaSplitTaskRepository.deleteAllByIdInBatch(taskIds);
    }
}