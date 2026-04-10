package com.novel.splitter.interfaces.infra;

import com.novel.splitter.application.config.AppConfig;
import com.novel.splitter.application.port.out.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class LocalFileStorageAdapter implements FileStoragePort {

    private final AppConfig appConfig;

    @Override
    public List<String> listTxtFiles() {
        try {
            Path storageRoot = getStorageRoot();
            if (!Files.exists(storageRoot)) {
                return Collections.emptyList();
            }
            try (Stream<Path> stream = Files.list(storageRoot)) {
                return stream
                        .filter(file -> !Files.isDirectory(file))
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .filter(name -> name.endsWith(".txt"))
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to list txt files in storage root", e);
        }
    }

    @Override
    public void saveFile(String relativePath, InputStream inputStream) {
        try {
            Path targetPath = getStorageRoot().resolve(relativePath);
            Path parent = targetPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file: " + relativePath, e);
        }
    }

    @Override
    public boolean exists(String relativePath) {
        return Files.exists(getStorageRoot().resolve(relativePath));
    }

    @Override
    public void deleteIfExists(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(getStorageRoot().resolve(relativePath));
        } catch (IOException ignored) {
            // Best-effort compensation
        }
    }

    @Override
    public String toAbsolutePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Relative path cannot be empty");
        }
        return getStorageRoot().resolve(relativePath).toAbsolutePath().normalize().toString();
    }

    @Override
    public String toRelativePath(String absolutePathStr) {
        if (absolutePathStr == null || absolutePathStr.isBlank()) {
            throw new IllegalArgumentException("Absolute path cannot be empty");
        }
        Path storageRoot = getStorageRoot().toAbsolutePath().normalize();
        Path absolutePath = Paths.get(absolutePathStr).toAbsolutePath().normalize();
        
        if (!absolutePath.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Path is outside storage root: " + absolutePath);
        }
        return storageRoot.relativize(absolutePath).toString().replace('\\', '/');
    }

    private Path getStorageRoot() {
        return Paths.get(appConfig.getStorage().getRootPath()).toAbsolutePath().normalize();
    }
}