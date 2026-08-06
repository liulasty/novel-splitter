package com.novel.splitter.interfaces.infra;

import com.novel.splitter.application.port.out.FileStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
public class LocalFileStorageAdapter implements FileStoragePort {

    private final Path rootDir;

    public LocalFileStorageAdapter(@Value("${splitter.storage.root-path:data/novel-storage}") String rootPath) {
        this.rootDir = Paths.get(rootPath).toAbsolutePath().normalize();
    }

    @Override
    public void write(String relativePath, InputStream content, boolean replaceExisting) throws IOException {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath must not be blank");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        Path target = resolve(relativePath);
        Files.createDirectories(target.getParent());
        if (replaceExisting) {
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.copy(content, target);
        }
    }

    @Override
    public boolean exists(String relativePath) throws IOException {
        return Files.exists(resolve(relativePath));
    }

    @Override
    public Path toAbsolutePath(String relativePath) throws IOException {
        return resolve(relativePath);
    }

    @Override
    public String toRelativePath(String absoluteOrRelativePath) throws IOException {
        if (absoluteOrRelativePath == null || absoluteOrRelativePath.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        String p = absoluteOrRelativePath.trim();
        // 先尝试将其视为已是相对路径。
        try {
            resolve(p);
            return p.replace('\\', '/');
        } catch (RuntimeException ignored) {
            // 未命中则继续按绝对路径处理
        }

        Path abs = Paths.get(p).toAbsolutePath().normalize();
        if (!abs.startsWith(rootDir)) {
            throw new IllegalArgumentException("path is outside storage root: " + abs);
        }
        return rootDir.relativize(abs).toString().replace('\\', '/');
    }

    @Override
    public void deleteIfExists(String relativePath) throws IOException {
        Files.deleteIfExists(resolve(relativePath));
    }

    @Override
    public void deleteTreeIfExists(String relativeDir) throws IOException {
        Path dir = resolve(relativeDir);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("删除失败: {}", p, e);
                }
            });
        }
    }

    @Override
    public List<String> listFiles(String relativeDir) throws IOException {
        Path dir = resolve(relativeDir);
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> !Files.isDirectory(p))
                    .map(p -> p.getFileName().toString())
                    .toList();
        }
    }

    @Override
    public List<String> listDirectories(String relativeDir) throws IOException {
        Path dir = resolve(relativeDir);
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .toList();
        }
    }

    private Path resolve(String relativePath) {
        String normalized = relativePath == null ? "" : relativePath.replace('\\', '/');
        Path resolved = rootDir.resolve(normalized).normalize();
        if (!resolved.startsWith(rootDir)) {
            throw new IllegalArgumentException("path escapes storage root: " + relativePath);
        }
        return resolved;
    }
}

