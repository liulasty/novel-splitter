package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.domain.task.CleanupTask;
import com.novel.splitter.domain.task.CleanupTaskMessage;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.domain.repository.CleanupTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupWorker {

    private final VectorStore vectorStore;
    private final CleanupTaskRepository cleanupTaskRepository;

    @Value("${splitter.storage.root-path}")
    private String novelStoragePath;

    @RabbitListener(queues = RabbitConfig.CLEANUP_TASK_QUEUE, concurrency = "1")
    public void handleCleanupTask(CleanupTaskMessage message) {
        log.info("Received cleanup task for: {} {}", message.getTargetType(), message.getTargetId());
        
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
                log.info("Physically deleting ChromaDB vectors for {}/{}", message.getTargetId(), message.getVersion());
                vectorStore.delete(Map.of("novel", message.getTargetId(), "version", message.getVersion()));
            } else if ("NOVEL".equals(message.getTargetType())) {
                log.info("Physically deleting ChromaDB vectors for novel {}", message.getTargetId());
                vectorStore.delete(Map.of("novel", message.getTargetId()));
                
                // Delete raw file
                deleteRawFile(message.getTargetId());
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

    private void deleteRawFile(String novelName) {
        try {
            java.nio.file.Path rootDir = Paths.get(novelStoragePath);
            java.nio.file.Path rawDir = rootDir.resolve("raw");
            
            boolean deleted = deleteFileIfExists(rawDir, novelName);
            if (!deleted) {
                deleted = deleteFileIfExists(rootDir, novelName);
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

    private boolean deleteFileIfExists(java.nio.file.Path dir, String novelName) throws java.io.IOException {
        java.nio.file.Path path = dir.resolve(novelName + ".txt");
        if (Files.exists(path)) {
            Files.delete(path);
            log.info("Deleted raw file: {}", path);
            return true;
        }
        
        path = dir.resolve(novelName);
        if (Files.exists(path)) {
            Files.delete(path);
            log.info("Deleted raw file: {}", path);
            return true;
        }
        
        return false;
    }
}