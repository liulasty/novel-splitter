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
                "Received cleanup task for: type={}, targetId={}, novelId={}, novelName={}, version={}",
                message.getTargetType(),
                message.getTargetId(),
                message.getNovelId(),
                message.getNovelName(),
                message.getVersion()
        );
        
        Optional<CleanupTask> taskOpt = cleanupTaskRepository.findById(message.getCleanupTaskId());
        if (taskOpt.isEmpty()) {
            log.warn("Cleanup task {} not found in database, skipping", message.getCleanupTaskId());
            return;
        }
        
        CleanupTask task = taskOpt.get();
        if (!"PENDING".equals(task.getStatus()) && !"FAILED".equals(task.getStatus())) {
            log.info("Cleanup task {} is already in status {}, skipping", task.getId(), task.getStatus());
            return;
        }

        try {
            if ("VERSION".equals(message.getTargetType())) {
                String novelId = firstNonBlank(message.getNovelId(), null);
                String novelName = firstNonBlank(message.getNovelName(), message.getTargetId());
                String version = message.getVersion();

                if (novelId != null) {
                    log.info("Physically deleting ChromaDB vectors for novelId={} version={}", novelId, version);
                    vectorStore.delete(Map.of("novelId", novelId, "version", version));
                }
                if (novelName != null) {
                    log.info("Physically deleting ChromaDB vectors for novel='{}' version={}", novelName, version);
                    vectorStore.delete(Map.of("novel", novelName, "version", version));
                }
            } else if ("NOVEL".equals(message.getTargetType())) {
                String novelName = firstNonBlank(message.getNovelName(), message.getTargetId());
                if (novelName == null) {
                    throw new IllegalArgumentException("novelName must not be blank for NOVEL cleanup");
                }
                log.info("Physically deleting ChromaDB vectors for novel='{}'", novelName);
                vectorStore.delete(Map.of("novel", novelName));

                deleteRawFileByName(novelName);
            } else if ("NOVEL_ID".equals(message.getTargetType())) {
                String novelId = firstNonBlank(message.getNovelId(), message.getTargetId());
                if (novelId == null) {
                    throw new IllegalArgumentException("novelId must not be blank for NOVEL_ID cleanup");
                }

                // Delete vectors by novelId (preferred)
                log.info("Physically deleting ChromaDB vectors for novelId={}", novelId);
                vectorStore.delete(Map.of("novelId", novelId));

                // Backward compatibility: if we know a legacy novelName, also delete by novel
                String novelName = firstNonBlank(message.getNovelName(), null);
                if (novelName != null) {
                    log.info("Physically deleting ChromaDB vectors (compat) for novel='{}'", novelName);
                    vectorStore.delete(Map.of("novel", novelName));
                }

                // Delete raw file using DB filePath if available; fallback to name-based deletion.
                deleteRawFileByNovelId(novelId, novelName);
            } else {
                log.warn("Unknown targetType {} for cleanup task {}", message.getTargetType(), task.getId());
            }

            task.setStatus("SUCCESS");
            cleanupTaskRepository.save(task);
            log.info("Successfully completed cleanup task {}", task.getId());

        } catch (Exception e) {
            log.error("Failed to process cleanup task " + task.getId(), e);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            cleanupTaskRepository.save(task);
            
            // Re-throw to allow MQ to retry (based on retry policy)
            throw new RuntimeException("Failed to process cleanup task", e);
        }
    }

    private void deleteRawFileByNovelId(String novelId, String fallbackNovelName) {
        // Preferred: novelId-bound directory cleanup (raw + parsed)
        try {
            fileStoragePort.deleteTreeIfExists(rawDirName + "/" + novelId);
            fileStoragePort.deleteTreeIfExists(parsedDirName + "/" + novelId);
        } catch (Exception e) {
            log.warn("Failed to delete novelId-bound directories for novelId={}, err={}", novelId, e.getMessage());
        }

        try {
            Optional<Novel> novelOpt = novelRepository.findById(novelId);
            if (novelOpt.isPresent() && novelOpt.get().getFilePath() != null && !novelOpt.get().getFilePath().isBlank()) {
                String relativeOrAbsolute = novelOpt.get().getFilePath();
                // Prefer relative-path deletion; if absolute-path was stored historically, fall back to name-based deletion.
                try {
                    String rel = fileStoragePort.toRelativePath(relativeOrAbsolute);
                    fileStoragePort.deleteIfExists(rel);
                    log.info("Deleted raw file by filePath: {}", rel);
                    return;
                } catch (Exception ignored) {
                    // ignored, fallback below
                }
            }
        } catch (Exception e) {
            log.warn("Failed to delete raw file by novelId={}, falling back to name-based deletion. err={}", novelId, e.getMessage());
        }

        if (fallbackNovelName != null) {
            deleteRawFileByName(fallbackNovelName);
        } else {
            log.warn("No fallback novelName available for novelId={}", novelId);
        }
    }

    private void deleteRawFileByName(String novelName) {
        try {
            // Legacy name-based deletion: raw/xxx.txt or storageRoot/xxx.txt
            boolean deleted = false;
            try {
                fileStoragePort.deleteIfExists("raw/" + novelName + ".txt");
                deleted = true;
            } catch (Exception ignored) {
                // ignore
            }
            if (!deleted) {
                try {
                    fileStoragePort.deleteIfExists(novelName + ".txt");
                    deleted = true;
                } catch (Exception ignored) {
                    // ignore
                }
            }
            
            if (deleted) {
                log.info("Successfully deleted raw file for novel: {}", novelName);
            } else {
                log.warn("Raw file not found for deletion: {}", novelName);
            }
        } catch (Exception e) {
            log.error("Failed to delete raw file for novel: " + novelName, e);
        }
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}