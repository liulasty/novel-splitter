package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.model.command.UploadNovelCommand;
import com.novel.splitter.application.config.AppConfig;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.repository.NovelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NovelServiceImpl implements NovelService {

    private final NovelRepository novelRepository;
    private final NovelStorageService novelStorageService;
    private final TransactionTemplate transactionTemplate;
    private final AppConfig appConfig;

    @Override
    public String createNovel(UploadNovelCommand command) throws IOException {
        String novelId = UUID.randomUUID().toString();

        String rawDirName = appConfig.getStorage().getRawDirName();
        String rawFilename = appConfig.getStorage().getRawFilename();
        String storedRelativePath = rawDirName + "/" + novelId + "/" + rawFilename;

        try {
            transactionTemplate.execute(status -> {
                saveNovelRecordWithId(novelId, storedRelativePath, command.getTitle(), command.getAuthor(), command.getDescription());
                return null;
            });
        } catch (RuntimeException ex) {
            throw ex;
        }

        try {
            String actualStored = novelStorageService.saveNovelAsRawByNovelId(novelId, command);
            if (!storedRelativePath.equals(actualStored)) {
                transactionTemplate.execute(status -> {
                    Novel n = novelRepository.findById(novelId).orElseThrow();
                    n.setFilePath(actualStored);
                    n.setUpdatedAt(System.currentTimeMillis());
                    novelRepository.save(n);
                    return null;
                });
            }
        } catch (RuntimeException ex) {
            transactionTemplate.execute(status -> {
                novelRepository.findById(novelId).ifPresent(n -> {
                    n.setDeleted(true);
                    n.setUpdatedAt(System.currentTimeMillis());
                    novelRepository.save(n);
                });
                return null;
            });
            novelStorageService.deleteNovelIfExists(storedRelativePath);
            throw ex;
        }

        return novelId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createNovelFromStoredFile(String storedRelativePath, String title, String author, String description) {
        if (storedRelativePath == null || storedRelativePath.isBlank()) {
            throw new IllegalArgumentException("storedRelativePath must not be blank");
        }
        return saveNovelRecord(storedRelativePath, title, author, description);
    }

    private String saveNovelRecord(String fileName, String title, String author, String description) {
        String novelId = UUID.randomUUID().toString();
        saveNovelRecordWithId(novelId, fileName, title, author, description);
        return novelId;
    }

    private void saveNovelRecordWithId(String novelId, String fileName, String title, String author, String description) {
        Novel novel = Novel.builder()
                .id(novelId)
                .title(title != null ? title : fileName.replace(".txt", ""))
                .author(author)
                .description(description)
                .filePath(fileName)
                .status(NovelStatus.PENDING)
                .createdAt(System.currentTimeMillis())
                .updatedAt(System.currentTimeMillis())
                .isDeleted(false)
                .build();

        novelRepository.save(novel);
        log.info("Saved novel entity to database, novelId: {}", novelId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNovelStatus(String novelId, NovelStatus status) {
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new IllegalArgumentException("Novel not found: " + novelId));
                
        novel.updateStatus(status);
        novelRepository.save(novel);
        log.info("Updated novel {} status to {}", novelId, status);
    }

    @Override
    public Novel getNovelById(String novelId) {
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new IllegalArgumentException("Novel not found: " + novelId));
        if (novel.isDeleted()) {
            throw new IllegalArgumentException("Novel is deleted: " + novelId);
        }
        return novel;
    }

    @Override
    public List<Novel> listNovels() {
        return novelRepository.findAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeleteNovel(String novelId) {
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new IllegalArgumentException("Novel not found: " + novelId));
        if (novel.isDeleted()) {
            return;
        }
        novel.setDeleted(true);
        novel.setUpdatedAt(System.currentTimeMillis());
        novelRepository.save(novel);
        log.info("Soft deleted novel {}", novelId);
    }
}
