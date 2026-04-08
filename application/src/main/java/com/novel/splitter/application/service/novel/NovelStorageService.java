package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.config.AppConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class NovelStorageService {

    private static final String DEFAULT_UNKNOWN_FILE_PREFIX = "unknown";

    private final AppConfig appConfig;

    public List<String> listNovels() throws IOException {
        Path storagePath = getStoragePath();
        try (Stream<Path> stream = Files.list(storagePath)) {
            return stream
                    .filter(file -> !Files.isDirectory(file))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(".txt"))
                    .collect(Collectors.toList());
        }
    }

    public String saveNovel(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件为空");
        }

        String originalFilename = file.getOriginalFilename();
        String newFilename = generateUniqueFilename(originalFilename);
        Path destination = getStoragePath().resolve(newFilename);
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        return newFilename;
    }

    public Path resolveExistingNovelPath(String fileName) throws IOException {
        Path novelPath = getStoragePath().resolve(fileName);
        if (!Files.exists(novelPath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "找不到文件: " + fileName);
        }
        return novelPath;
    }

    public void deleteNovelIfExists(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            Path novelPath = getStoragePath().resolve(fileName);
            Files.deleteIfExists(novelPath);
        } catch (IOException ignored) {
            // Best-effort compensation only.
        }
    }

    private Path getStoragePath() throws IOException {
        Path path = Paths.get(appConfig.getStorage().getRootPath());
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        return path;
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
