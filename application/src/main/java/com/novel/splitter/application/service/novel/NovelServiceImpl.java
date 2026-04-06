package com.novel.splitter.application.service.novel;

import com.novel.splitter.domain.entity.JpaNovelEntity;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.repository.api.JpaNovelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NovelServiceImpl implements NovelService {

    private final JpaNovelRepository jpaNovelRepository;
    private final NovelStorageService novelStorageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createNovel(MultipartFile file, String title, String author, String description) throws IOException {
        String fileName = novelStorageService.saveNovel(file);
        
        // Here novelId could be a UUID or the filename without extension. 
        // For consistency with earlier code, we can use a UUID.
        String novelId = UUID.randomUUID().toString();
        
        JpaNovelEntity entity = JpaNovelEntity.builder()
                .id(novelId)
                .title(title != null ? title : fileName.replace(".txt", ""))
                .author(author)
                .description(description)
                .filePath(fileName) // Store the filename/path in storage
                .status(NovelStatus.PENDING)
                .createdAt(System.currentTimeMillis())
                .updatedAt(System.currentTimeMillis())
                .isDeleted(false)
                .build();
                
        jpaNovelRepository.save(entity);
        log.info("Saved novel entity to database, novelId: {}", novelId);
        
        return novelId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNovelStatus(String novelId, NovelStatus status) {
        JpaNovelEntity novel = jpaNovelRepository.findById(novelId)
                .orElseThrow(() -> new IllegalArgumentException("Novel not found: " + novelId));
                
        // Can add state machine validation here if necessary
        novel.setStatus(status);
        novel.setUpdatedAt(System.currentTimeMillis());
        jpaNovelRepository.save(novel);
        log.info("Updated novel {} status to {}", novelId, status);
    }

    @Override
    public JpaNovelEntity getNovelById(String novelId) {
        return jpaNovelRepository.findById(novelId)
                .orElseThrow(() -> new IllegalArgumentException("Novel not found: " + novelId));
    }

    @Override
    public List<JpaNovelEntity> listNovels() {
        return jpaNovelRepository.findAll();
    }
}
