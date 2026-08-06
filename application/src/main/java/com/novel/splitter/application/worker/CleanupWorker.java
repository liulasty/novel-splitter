package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.domain.task.CleanupTask;
import com.novel.splitter.domain.task.CleanupTaskMessage;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.domain.repository.CleanupTaskRepository;
import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.domain.model.Novel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupWorker {

    private final VectorStore vectorStore;
    private final CleanupTaskRepository cleanupTaskRepository;
    private final NovelRepository novelRepository;
    private final com.novel.splitter.application.port.out.FileStoragePort fileStoragePort;

    @Value("${splitter.storage.root-path}")
    private String novelStoragePath;

    @Value("${splitter.storage.raw-dir-name:novel-raw}")
    private String rawDirName;

    @Value("${splitter.storage.parsed-dir-name:novel-parsed}")
    private String parsedDirName;

    @RabbitListener(queues = RabbitConfig.CLEANUP_TASK_QUEUE, concurrency = "1")
    public void handleCleanupTask(CleanupTaskMessage message) {
        log.info(
                "收到清理任务：type={}, targetId={}, novelId={}, novelName={}, version={}",
                message.getTargetType(),
                message.getTargetId(),
                message.getNovelId(),
                message.getNovelName(),
                message.getVersion()
        );
        
        Optional<CleanupTask> taskOpt = cleanupTaskRepository.findById(message.getCleanupTaskId());
        if (taskOpt.isEmpty()) {
            log.warn("清理任务 {} 在数据库中不存在，跳过", message.getCleanupTaskId());
            return;
        }
        
        CleanupTask task = taskOpt.get();
        if (!"PENDING".equals(task.getStatus()) && !"FAILED".equals(task.getStatus())) {
            log.info("清理任务 {} 已处于状态 {}，跳过", task.getId(), task.getStatus());
            return;
        }

        try {
            if ("VERSION".equals(message.getTargetType()) || "VERSION_BY_NOVEL_ID".equals(message.getTargetType())) {
                String novelId = firstNonBlank(message.getNovelId(), null);
                String novelName = firstNonBlank(message.getNovelName(), message.getTargetId());
                String version = message.getVersion();

                java.util.Map<String, Object> filterByNovelId = new java.util.HashMap<>();
                if (novelId != null) {
                    filterByNovelId.put("novelId", novelId);
                }
                if (version != null) {
                    filterByNovelId.put("version", version);
                }
                if (message.getChunkSize() != null && message.getChunkOverlap() != null) {
                    filterByNovelId.put("chunkSize", message.getChunkSize());
                    filterByNovelId.put("chunkOverlap", message.getChunkOverlap());
                }
                if (novelId != null && version != null) {
                    log.info("物理删除 novelId={} version={} filter={} 的 ChromaDB 向量", novelId, version, filterByNovelId);
                    vectorStore.delete(filterByNovelId);
                }
                if (novelName != null && version != null) {
                    java.util.Map<String, Object> filterByName = new java.util.HashMap<>();
                    filterByName.put("novel", novelName);
                    filterByName.put("version", version);
                    if (message.getChunkSize() != null && message.getChunkOverlap() != null) {
                        filterByName.put("chunkSize", message.getChunkSize());
                        filterByName.put("chunkOverlap", message.getChunkOverlap());
                    }
                    log.info("物理删除 novel='{}' version={} 的 ChromaDB 向量", novelName, version);
                    vectorStore.delete(filterByName);
                }
                // 按专属集合整删（多集合版本化后的主路径）
                if (novelId != null && version != null) {
                    String collectionName = VectorStore.collectionNameFor(novelId, version);
                    log.info("删除版本专属集合 '{}'（novelId={} version={}）", collectionName, novelId, version);
                    vectorStore.deleteByCollection(collectionName);
                }
            } else if ("NOVEL".equals(message.getTargetType())) {
                String novelName = firstNonBlank(message.getNovelName(), message.getTargetId());
                if (novelName == null) {
                    throw new IllegalArgumentException("novelName must not be blank for NOVEL cleanup");
                }
                log.info("物理删除 novel='{}' 的 ChromaDB 向量", novelName);
                vectorStore.delete(Map.of("novel", novelName));

                deleteCapturedVersionCollections(message);

                deleteRawFileByName(novelName);
            } else if ("NOVEL_ID".equals(message.getTargetType())) {
                String novelId = firstNonBlank(message.getNovelId(), message.getTargetId());
                if (novelId == null) {
                    throw new IllegalArgumentException("novelId must not be blank for NOVEL_ID cleanup");
                }

                // 按 novelId 删除向量（优先）
                log.info("物理删除 novelId={} 的 ChromaDB 向量", novelId);
                vectorStore.delete(Map.of("novelId", novelId));

                // 向后兼容：若已知旧版 novelName，也按 novel 删除
                String novelName = firstNonBlank(message.getNovelName(), null);
                if (novelName != null) {
                    log.info("物理删除 novel='{}' 的 ChromaDB 向量（兼容模式）", novelName);
                    vectorStore.delete(Map.of("novel", novelName));
                }

                // 整书删除：按删除时捕获的集合名整删（版本行已同步删除，无法再枚举）
                deleteCapturedVersionCollections(message);

                // 优先用 DB 中的 filePath 删除原始文件；否则回退为按名称删除。
                deleteRawFileByNovelId(novelId, novelName);
            } else {
                log.warn("未知 targetType {}，清理任务 {}", message.getTargetType(), task.getId());
            }

            task.setStatus("SUCCESS");
            cleanupTaskRepository.save(task);
            log.info("清理任务 {} 已完成", task.getId());

        } catch (Exception e) {
            log.error("处理清理任务 " + task.getId() + " 失败", e);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            cleanupTaskRepository.save(task);
            
            // 重新抛出以让 MQ 重试（依据重试策略）
            throw new RuntimeException("Failed to process cleanup task", e);
        }
    }

    private void deleteRawFileByNovelId(String novelId, String fallbackNovelName) {
        // 优先：按 novelId 绑定的目录清理（raw + parsed）
        try {
            fileStoragePort.deleteTreeIfExists(rawDirName + "/" + novelId);
            fileStoragePort.deleteTreeIfExists(parsedDirName + "/" + novelId);
        } catch (Exception e) {
            log.warn("删除 novelId={} 绑定的目录失败，err={}", novelId, e.getMessage());
        }

        try {
            Optional<Novel> novelOpt = novelRepository.findById(novelId);
            if (novelOpt.isPresent() && novelOpt.get().getFilePath() != null && !novelOpt.get().getFilePath().isBlank()) {
                String relativeOrAbsolute = novelOpt.get().getFilePath();
                // 优先按相对路径删除；若历史存储的是绝对路径，则回退为按名称删除。
                try {
                    String rel = fileStoragePort.toRelativePath(relativeOrAbsolute);
                    fileStoragePort.deleteIfExists(rel);
                    log.info("已按 filePath 删除原始文件：{}", rel);
                    return;
                } catch (Exception ignored) {
                    // 忽略，走下面的回退逻辑
                }
            }
        } catch (Exception e) {
            log.warn("按 novelId={} 删除原始文件失败，回退为按名称删除。err={}", novelId, e.getMessage());
        }

        if (fallbackNovelName != null) {
            deleteRawFileByName(fallbackNovelName);
        } else {
            log.warn("novelId={} 没有可用的回退 novelName", novelId);
        }
    }

    private void deleteRawFileByName(String novelName) {
        try {
            // 旧版按名称删除：raw/xxx.txt 或 storageRoot/xxx.txt
            boolean deleted = false;
            try {
                fileStoragePort.deleteIfExists("raw/" + novelName + ".txt");
                deleted = true;
            } catch (Exception ignored) {
                // 忽略
            }
            if (!deleted) {
                try {
                    fileStoragePort.deleteIfExists(novelName + ".txt");
                    deleted = true;
                } catch (Exception ignored) {
                    // 忽略
                }
            }
            
            if (deleted) {
                log.info("已删除 novel: {} 的原始文件", novelName);
            } else {
                log.warn("未找到待删除的原始文件：{}", novelName);
            }
        } catch (Exception e) {
            log.error("删除 novel: " + novelName + " 的原始文件失败", e);
        }
    }

    /**
     * 整书删除时按消息中快照的集合名整删各版本专属向量集合。
     * <p>版本行已在删除事务内同步删除，此处不能回查版本表，只能消费消息里的集合名。</p>
     */
    private void deleteCapturedVersionCollections(CleanupTaskMessage message) {
        List<String> collectionNames = message.getCollectionNames();
        if (collectionNames == null || collectionNames.isEmpty()) {
            return;
        }
        for (String col : collectionNames) {
            log.info("删除版本专属集合 '{}'（整书清理）", col);
            vectorStore.deleteByCollection(col);
        }
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}