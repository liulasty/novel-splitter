package com.novel.splitter.application.service.novel;

import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.repository.NovelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NovelServiceImpl implements NovelService {

    private final NovelRepository novelRepository;
    private final NovelStorageService novelStorageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createNovel(MultipartFile file, String title, String author, String description) throws IOException {
        String fileMd5 = DigestUtils.md5DigestAsHex(file.getInputStream());
        long fileSize = file.getSize();

        Optional<Novel> existingNovel = novelRepository.findByFileMd5(fileMd5);
        if (existingNovel.isPresent()) {
            log.info("File already exists with novelId: {}", existingNovel.get().getId());
            return existingNovel.get().getId();
        }

        String fileName = novelStorageService.saveNovel(file);
        
        String novelId = UUID.randomUUID().toString();
        
        Novel novel = Novel.builder()
                .id(novelId)
                .title(title != null ? title : fileName.replace(".txt", ""))
                .author(author)
                .description(description)
                .filePath(fileName)
                .fileMd5(fileMd5)
                .fileSize(fileSize)
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
