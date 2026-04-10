package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.config.AppConfig;
import com.novel.splitter.application.port.out.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class NovelStorageService {

    private static final String DEFAULT_UNKNOWN_FILE_PREFIX = "unknown";

    private final AppConfig appConfig;
    private final FileStoragePort fileStoragePort;

    public List<String> listNovels() throws IOException {
        // Prefer configured rawDirName/{novelId}/{rawFilename}. Keep legacy root listing for compatibility.
        List<String> legacyRootTxt = fileStoragePort.listFiles("")
                .stream()
                .filter(name -> name.endsWith(".txt"))
                .toList();

        List<String> rawNovelIds = fileStoragePort.listDirectories(appConfig.getStorage().getRawDirName());
        List<String> configuredRawPaths = rawNovelIds.stream().map(this::rawRelativePath).toList();

        // Return configured paths first to nudge callers off legacy root fileName usage.
        return Stream.concat(configuredRawPaths.stream(), legacyRootTxt.stream()).distinct().toList();
    }

    public String saveNovel(String originalFilename, InputStream content) throws IOException {
        if (content == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件为空");
        }
        String newFilename = generateUniqueFilename(originalFilename);
        fileStoragePort.write(newFilename, content, true);
        return newFilename;
    }

    /**
     * Raw original relative path under storage root.
     * Format: {rawDirName}/{novelId}/{rawFilename}
     */
    public String rawRelativePath(String novelId) {
        if (novelId == null || novelId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "novelId 为空");
        }
        String rawDirName = appConfig.getStorage().getRawDirName();
        String rawFilename = appConfig.getStorage().getRawFilename();
        return rawDirName + "/" + novelId.trim() + "/" + rawFilename;
    }

    /**
     * Save novel raw text as {rawDirName}/{novelId}/{rawFilename} under storage root.
     *
     * @return stored relative path (e.g. novel-raw/{novelId}/original.txt)
     */
    public String saveNovelAsRawByNovelId(String novelId, InputStream content) throws IOException {
        if (content == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件为空");
        }
        String relativePath = rawRelativePath(novelId);
        fileStoragePort.write(relativePath, content, true);
        return relativePath;
    }

    public java.nio.file.Path resolveExistingNovelPath(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileName 为空");
        }
        if (!fileStoragePort.exists(fileName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "找不到文件: " + fileName);
        }
        return fileStoragePort.toAbsolutePath(fileName);
    }

    public void deleteNovelIfExists(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            fileStoragePort.deleteIfExists(fileName);
        } catch (IOException ignored) {
            // Best-effort compensation only.
        }
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
