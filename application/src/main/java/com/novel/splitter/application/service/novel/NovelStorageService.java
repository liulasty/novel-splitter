package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.config.AppConfig;
import com.novel.splitter.application.model.command.UploadNovelCommand;
import com.novel.splitter.application.port.out.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NovelStorageService {

    private static final String DEFAULT_UNKNOWN_FILE_PREFIX = "unknown";

    private final AppConfig appConfig;
    private final FileStoragePort fileStoragePort;

    public List<String> listNovels() {
        return fileStoragePort.listTxtFiles();
    }

    public String saveNovel(UploadNovelCommand command) {
        if (command == null || command.getSize() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件为空");
        }

        String originalFilename = command.getOriginalFilename();
        String newFilename = generateUniqueFilename(originalFilename);
        
        fileStoragePort.saveFile(newFilename, command.getInputStream());
        return newFilename;
    }

    /**
     * Save novel raw text as {rawDirName}/{novelId}/{rawFilename} under storage root.
     *
     * @return stored relative path
     */
    public String saveNovelAsRawByNovelId(String novelId, UploadNovelCommand command) {
        if (novelId == null || novelId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novelId 为空");
        }
        if (command == null || command.getSize() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件为空");
        }
        
        String rawDirName = appConfig.getStorage().getRawDirName();
        String rawFilename = appConfig.getStorage().getRawFilename();
        
        String relativePath = rawDirName + "/" + novelId.trim() + "/" + rawFilename;
        fileStoragePort.saveFile(relativePath, command.getInputStream());
        return relativePath;
    }

    public Path resolveExistingNovelPath(String fileName) {
        if (!fileStoragePort.exists(fileName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "找不到文件: " + fileName);
        }
        return Paths.get(fileStoragePort.toAbsolutePath(fileName));
    }

    public void deleteNovelIfExists(String fileName) {
        fileStoragePort.deleteIfExists(fileName);
    }

    private String generateUniqueFilename(String originalFilename) {
        String sourceFilename = originalFilename;
        if (sourceFilename == null || sourceFilename.isBlank()) {
            sourceFilename = DEFAULT_UNKNOWN_FILE_PREFIX + ".txt";
        }

        String name = sourceFilename;
        String ext = "";
        int dotIndex = sourceFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            name = sourceFilename.substring(0, dotIndex);
            ext = sourceFilename.substring(dotIndex);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        return name + "_" + timestamp + ext;
    }
}