package com.novel.splitter.application.service.task;

import com.novel.splitter.domain.enums.TaskType;
import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.SplitTaskFilter;
import com.novel.splitter.domain.task.TaskProgressEvent;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.repository.SplitTaskRepository;
import com.novel.splitter.domain.repository.TaskEventRepository;
import com.novel.splitter.application.model.dto.JobStatSummaryDto;
import com.novel.splitter.application.model.dto.JobRecordDto;
import com.novel.splitter.application.model.dto.PollResponse;
import com.novel.splitter.application.port.out.TaskCachePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import static com.novel.splitter.domain.task.SplitTask.TaskStatus.FAILED;
import static com.novel.splitter.domain.task.SplitTask.TaskStatus.SUCCESS;

@Service
@Slf4j
public class TaskService {

    private static final List<SplitTask.TaskStatus> TERMINAL_STATUSES = List.of(SUCCESS, FAILED);
    
    private final SplitTaskRepository taskRepository;
    private final TaskEventRepository taskEventRepository;
    private final TaskCachePort taskCachePort;
    private final NovelRepository novelRepository;
    private final SceneRepository sceneRepository;

    public TaskService(
            SplitTaskRepository taskRepository,
            TaskEventRepository taskEventRepository,
            TaskCachePort taskCachePort,
            NovelRepository novelRepository,
            SceneRepository sceneRepository) {
        this.taskRepository = taskRepository;
        this.taskEventRepository = taskEventRepository;
        this.taskCachePort = taskCachePort;
        this.novelRepository = novelRepository;
        this.sceneRepository = sceneRepository;
    }

    @Transactional
    public SplitTask createTask(String taskId, TaskType taskType, String novelId, String fileName, int maxScenes, String version) {
        SplitTask task = new SplitTask(taskId, taskType, novelId, fileName, maxScenes, version);
        persistNewTask(task);
        return task;
    }

    /**
     * 在单事务内对小说行加锁并校验无进行中任务后创建任务，避免并发下的检查-提交竞态。
     */
    @Transactional(rollbackFor = Exception.class)
    public SplitTask createTaskWithNovelAdmission(String taskId, TaskType taskType, String novelId, int maxScenes, String version) {
        Novel novel = loadNovelLockedForTaskAdmission(novelId);
        throwIfHasActiveTasks(novel.getId(), "该小说存在进行中的任务，请结束后再试");
        SplitTask task = new SplitTask(taskId, taskType, novel.getId(), novel.getFilePath(), maxScenes, version);
        persistNewTask(task);
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public SplitTask createEmbedTaskWithNovelAdmission(String taskId, String novelId, String normalizedVersion) {
        return createEmbedTaskWithNovelAdmission(taskId, novelId, normalizedVersion, null, null);
    }

    /**
     * 与 {@link #createTaskWithNovelAdmission} 相同的事务语义，并校验场景数后创建 EMBED 任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public SplitTask createEmbedTaskWithNovelAdmission(
            String taskId, String novelId, String normalizedVersion, Integer chunkSize, Integer chunkOverlap) {
        Novel novel = loadNovelLockedForTaskAdmission(novelId);
        throwIfHasActiveTasks(novel.getId(), "该小说存在进行中的任务，请结束后再试");
        long sceneCount;
        if (chunkSize != null && chunkOverlap != null) {
            sceneCount = sceneRepository.countByProfile(novel.getId(), normalizedVersion, chunkSize, chunkOverlap);
        } else {
            sceneCount = sceneRepository.countAllByNovelIdAndVersion(novel.getId(), normalizedVersion);
        }
        if (sceneCount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "暂无场景数据，请先完成切分。novelId=" + novel.getId() + ", version=" + normalizedVersion);
        }
        SplitTask task = new SplitTask(taskId, TaskType.EMBED, novel.getId(), novel.getFilePath(), Integer.MAX_VALUE, normalizedVersion);
        persistNewTask(task);
        return task;
    }

    /**
     * 对小说行加锁并校验无进行中任务（用于删除等与创建任务互斥的操作）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void ensureNoActiveTasksForNovelLocked(String novelId, String conflictMessage) {
        if (novelId == null || novelId.isBlank()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        String nid = novelId.trim();
        novelRepository.findByIdForUpdate(nid)
                .orElseThrow(() -> new IllegalArgumentException("Novel not found: " + nid));
        throwIfHasActiveTasks(nid, conflictMessage);
    }

    private Novel loadNovelLockedForTaskAdmission(String novelId) {
        if (novelId == null || novelId.isBlank()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        String nid = novelId.trim();
        Novel novel = novelRepository.findByIdForUpdate(nid)
                .orElseThrow(() -> new IllegalArgumentException("Novel not found: " + nid));
        if (novel.isDeleted()) {
            throw new IllegalArgumentException("Novel is deleted: " + nid);
        }
        return novel;
    }

    private void throwIfHasActiveTasks(String novelId, String conflictMessage) {
        List<SplitTask> tasks = taskRepository.findRecentByNovelId(novelId, 50);
        boolean active = tasks.stream().anyMatch(t ->
                t != null && (t.getStatus() == SplitTask.TaskStatus.PENDING || t.getStatus() == SplitTask.TaskStatus.PROCESSING));
        if (active) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflictMessage);
        }
    }

    /**
     * 向量化编排开始时：绑定 embed run、总场景数、进入 PROCESSING。
     */
    @Transactional(rollbackFor = Exception.class)
    public void beginEmbedRun(String taskId, String embedRunId, int totalScenes, String message) {
        SplitTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        task.setCurrentEmbedRunId(embedRunId);
        task.setTotalScenes(totalScenes);
        task.getCompletedScenes().set(0);
        task.startProcessing(message);
        taskRepository.save(task);
        appendTaskEvent(task);
        taskCachePort.put(taskId, toPollResponse(task));
    }

    /**
     * 定时任务聚合向量化进度时更新任务行（不写终态）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateEmbedProcessingProgress(String taskId, long successCount, long failedCount, int totalScenes) {
        SplitTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null || task.getStatus() != SplitTask.TaskStatus.PROCESSING) {
            return;
        }
        task.getCompletedScenes().set((int) Math.min(successCount, totalScenes));
        int progress = totalScenes <= 0 ? 0 : (int) Math.min(99, (successCount * 100L) / totalScenes);
        String msg = String.format("向量化：%d/%d（失败 %d）", successCount, totalScenes, failedCount);
        task.updateProgress(progress, msg);
        taskRepository.save(task);
        appendTaskEvent(task);
        taskCachePort.put(taskId, toPollResponse(task));
    }

    private void persistNewTask(SplitTask task) {
        taskRepository.save(task);
        appendTaskEvent(task);
        String taskId = task.getTaskId();
        taskCachePort.put(taskId, PollResponse.builder()
                .taskId(taskId)
                .status(task.getStatus().name())
                .progress(task.getProgress())
                .message(task.getMessage())
                .updatedAt(task.getUpdatedAt())
                .serverTime(System.currentTimeMillis())
                .build());
    }

    @Transactional(readOnly = true)
    public SplitTask getTask(String taskId) {
        PollResponse cached = taskCachePort.get(taskId);
        if (cached != null) {
            SplitTask cachedTask = taskRepository.findById(taskId).orElse(null);
            if (cachedTask != null) {
                return cachedTask;
            }
        }
        SplitTask task = taskRepository.findById(taskId).orElse(null);
        if (task != null) {
            taskCachePort.put(taskId, toPollResponse(task));
        }
        return task;
    }

    @Transactional(readOnly = true)
    public List<SplitTask> getTasksByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return taskRepository.findByIds(ids);
    }

    @Transactional(readOnly = true)
    public List<SplitTask> getRecentTasksByNovelId(String novelId, int limit) {
        if (novelId == null || novelId.isBlank()) {
            return List.of();
        }
        int normalizedLimit = Math.max(1, Math.min(limit, 50));
        List<SplitTask> tasks = taskRepository.findRecentByNovelId(novelId, 50);
        long cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000;

        List<SplitTask> nonTerminal = tasks.stream()
                .filter(task -> task.getStatus() == SplitTask.TaskStatus.PENDING || task.getStatus() == SplitTask.TaskStatus.PROCESSING)
                .sorted(Comparator.comparing(SplitTask::getUpdatedAt).reversed())
                .toList();
        List<SplitTask> recentTerminal = tasks.stream()
                .filter(task -> task.getStatus() == SplitTask.TaskStatus.SUCCESS || task.getStatus() == SplitTask.TaskStatus.FAILED)
                .filter(task -> task.getUpdatedAt() >= cutoff)
                .sorted(Comparator.comparing(SplitTask::getUpdatedAt).reversed())
                .toList();

        return Stream.concat(nonTerminal.stream(), recentTerminal.stream())
                .limit(normalizedLimit)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean hasActiveTasksForNovelId(String novelId) {
        if (novelId == null || novelId.isBlank()) {
            return false;
        }
        List<SplitTask> tasks = taskRepository.findRecentByNovelId(novelId.trim(), 50);
        return tasks.stream().anyMatch(t ->
                t != null && (t.getStatus() == SplitTask.TaskStatus.PENDING || t.getStatus() == SplitTask.TaskStatus.PROCESSING)
        );
    }

    @Transactional(readOnly = true)
    public List<SplitTask> getAllTasks() {
        List<SplitTask> tasks = taskRepository.findAll();
        tasks.sort(Comparator.comparing(SplitTask::getCreatedAt).reversed());
        return tasks;
    }

    @Transactional(readOnly = true)
    public PagedResult<SplitTask> findTasksFiltered(SplitTaskFilter filter) {
        return taskRepository.findFiltered(filter);
    }

    @Transactional
    public void updateTaskStatus(String taskId, SplitTask.TaskStatus status, int progress, String message) {
        SplitTask task = taskRepository.findById(taskId).orElse(null);
        if (task != null) {
            switch (status) {
                case PENDING:
                    task.setStatus(status);
                    task.setProgress(progress);
                    if (message != null) task.setMessage(message);
                    break;
                case PROCESSING:
                    if (task.getStatus() == SplitTask.TaskStatus.PENDING) {
                        task.startProcessing(message);
                    } else {
                        task.updateProgress(progress, message);
                    }
                    break;
                case SUCCESS:
                    task.markAsSuccess(message);
                    break;
                case FAILED:
                    task.markAsFailed(message);
                    break;
            }
            taskRepository.save(task);
            appendTaskEvent(task);

            // 将任务进度写入缓存供轮询读取
            if (status == SplitTask.TaskStatus.SUCCESS || status == SplitTask.TaskStatus.FAILED) {
                taskCachePort.evict(taskId);
            } else {
                taskCachePort.put(taskId, toPollResponse(task));
            }
        }
    }

    @Transactional
    public void deleteTask(String taskId) {
        taskRepository.deleteById(taskId);
        taskCachePort.evict(taskId);
    }

    /**
     * 删除该小说的 {@link SplitTask.TaskStatus#SUCCESS} 与 {@link SplitTask.TaskStatus#FAILED} 行，
     * 以及对应的 {@code task_events}。绝不触碰 {@code PENDING} / {@code PROCESSING}。
     * 仅在外围操作要求「无进行中任务」不变式且已确认无活动任务时调用。
     */
    @Transactional
    public int purgeTerminalSplitTasksForNovel(String novelId) {
        if (novelId == null || novelId.isBlank()) {
            return 0;
        }
        List<String> ids = taskRepository.findTaskIdsByNovelIdAndStatuses(novelId.trim(), TERMINAL_STATUSES);
        return purgeSplitTasksByIds(ids);
    }

    /**
     * 类似 {@link #purgeTerminalSplitTasksForNovel(String)}，但只清理存储 {@code version} 匹配的任务。
     * {@link SplitTask} 上没有 chunk size/overlap 字段；凡 version 字符串相同的终态任务都会被删除。
     */
    @Transactional
    public int purgeTerminalSplitTasksForNovelAndVersion(String novelId, String version) {
        if (novelId == null || novelId.isBlank() || version == null || version.isBlank()) {
            return 0;
        }
        List<String> ids = taskRepository.findTaskIdsByNovelIdAndVersionAndStatuses(
                novelId.trim(), version.trim(), TERMINAL_STATUSES);
        return purgeSplitTasksByIds(ids);
    }

    private int purgeSplitTasksByIds(List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return 0;
        }
        taskEventRepository.deleteByTaskIds(taskIds);
        taskRepository.deleteAllByIds(taskIds);
        for (String id : taskIds) {
            taskCachePort.evict(id);
        }
        return taskIds.size();
    }

    @Transactional(readOnly = true)
    public List<TaskProgressEvent> getTaskEvents(String taskId, Long sinceTimestamp) {
        if (sinceTimestamp != null && sinceTimestamp > 0) {
            return taskEventRepository.findByTaskIdSince(taskId, sinceTimestamp);
        }
        return taskEventRepository.findByTaskId(taskId);
    }

    @Transactional(readOnly = true)
    public JobStatSummaryDto getJobStats() {
        long startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        List<SplitTask> allTasks = taskRepository.findAll();
        
        long running = allTasks.stream().filter(t -> t.getStatus() == SplitTask.TaskStatus.PROCESSING).count();
        long waiting = allTasks.stream().filter(t -> t.getStatus() == SplitTask.TaskStatus.PENDING).count();
        long completedToday = allTasks.stream().filter(t -> t.getStatus() == SplitTask.TaskStatus.SUCCESS && t.getUpdatedAt() >= startOfDay).count();
        long failedToday = allTasks.stream().filter(t -> t.getStatus() == SplitTask.TaskStatus.FAILED && t.getUpdatedAt() >= startOfDay).count();
        
        return JobStatSummaryDto.builder()
                .running(running)
                .waiting(waiting)
                .completedToday(completedToday)
                .failedToday(failedToday)
                .build();
    }

    @Transactional(readOnly = true)
    public List<JobRecordDto> getAllJobs() {
        return taskRepository.findAll().stream()
                .map(this::mapToJobRecordDto)
                .sorted(Comparator.comparing(JobRecordDto::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    private JobRecordDto mapToJobRecordDto(SplitTask task) {
        return JobRecordDto.builder()
                .id(task.getTaskId())
                .taskId(task.getTaskId())
                .taskType(task.getTaskType())
                .novelId(task.getNovelId())
                .fileName(task.getFileName())
                .maxScenes(task.getMaxScenes())
                .version(task.getVersion())
                .status(task.getStatus())
                .progress(task.getProgress())
                .message(task.getMessage())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .totalScenes(task.getTotalScenes())
                .completedScenes(task.getCompletedScenes().get())
                .build();
    }

    public void submitLoadTask(String novelId) {
        // 由 LoadWorker 或 RabbitMQ 发送实现，此处保留接口兼容
    }

    public void submitSplitTask(String novelId) {
        // 由 LoadWorker 或 RabbitMQ 发送实现，此处保留接口兼容
    }

    public void submitEmbedTask(String novelId) {
        // 由 LoadWorker 或 RabbitMQ 发送实现，此处保留接口兼容
    }

    public void submitCleanupTask(String novelId) {
        // 由 LoadWorker 或 RabbitMQ 发送实现，此处保留接口兼容
    }

    private PollResponse toPollResponse(SplitTask task) {
        Objects.requireNonNull(task, "task must not be null");
        return PollResponse.builder()
                .taskId(task.getTaskId())
                .status(task.getStatus() != null ? task.getStatus().name() : SplitTask.TaskStatus.PENDING.name())
                .progress(task.getProgress())
                .message(task.getMessage())
                .updatedAt(task.getUpdatedAt())
                .serverTime(System.currentTimeMillis())
                .build();
    }

    private void appendTaskEvent(SplitTask task) {
        if (task == null) {
            return;
        }
        try {
            taskEventRepository.save(new TaskProgressEvent(
                    task.getTaskId(),
                    task.getProgress(),
                    task.getMessage(),
                    task.getStatus() != null ? task.getStatus().name() : SplitTask.TaskStatus.PENDING.name(),
                    System.currentTimeMillis()
            ));
        } catch (RuntimeException ex) {
            log.warn("task_events 写入失败 taskId={} : {}", task.getTaskId(), ex.toString());
        }
    }
}
