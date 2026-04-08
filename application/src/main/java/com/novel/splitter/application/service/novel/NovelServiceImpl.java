package com.novel.splitter.application.service.novel;

import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.repository.NovelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

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

    @Override
    public String createNovel(MultipartFile file, String title, String author, String description) throws IOException {
        String fileName = novelStorageService.saveNovel(file);
        try {
            String novelId = transactionTemplate.execute(status -> saveNovelRecord(fileName, title, author, description));
            if (novelId == null) {
                throw new IllegalStateException("Failed to save novel record");
            }
            return novelId;
        } catch (RuntimeException ex) {
            novelStorageService.deleteNovelIfExists(fileName);
            throw ex;
        }
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

        return novelId;
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
        return novelRepository.findById(novelId)
                .orElseThrow(() -> new IllegalArgumentException("Novel not found: " + novelId));
    }

    @Override
    public List<Novel> listNovels() {
        return novelRepository.findAll();
    }
}
