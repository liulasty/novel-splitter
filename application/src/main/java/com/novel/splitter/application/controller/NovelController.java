package com.novel.splitter.application.controller;

import com.novel.splitter.application.service.etl.NovelIngestionService;
import com.novel.splitter.domain.model.dto.IngestRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/novels")
@RequiredArgsConstructor
@Slf4j
public class NovelController {

    private final NovelIngestionService ingestionService;
    private static final String NOVEL_STORAGE_PATH = "d:\\soft\\novel-splitter\\data\\novel-storage";

    @GetMapping
    public ResponseEntity<List<String>> listNovels() {
        try (Stream<Path> stream = Files.list(Paths.get(NOVEL_STORAGE_PATH))) {
            List<String> files = stream
                    .filter(file -> !Files.isDirectory(file))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(".txt"))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            log.error("Failed to list novels", e);
            return ResponseEntity.internalServerError().body(Collections.emptyList());
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadNovel(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }
        try {
            Path destination = Paths.get(NOVEL_STORAGE_PATH, file.getOriginalFilename());
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            return ResponseEntity.ok(Map.of("message", "File uploaded successfully: " + file.getOriginalFilename()));
        } catch (IOException e) {
            log.error("Failed to upload file", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, String>> ingest(@RequestBody IngestRequest request) {
        log.info("Received ingest request: {}", request);
        try {
            Path novelPath = Paths.get(NOVEL_STORAGE_PATH, request.getFileName());
            if (!Files.exists(novelPath)) {
                return ResponseEntity.badRequest().body(Map.of("error", "File not found"));
            }
            
            // Run in a separate thread to avoid blocking (simple async)
            new Thread(() -> {
                try {
                    ingestionService.ingest(novelPath, request.getMaxScenes() > 0 ? request.getMaxScenes() : Integer.MAX_VALUE, request.getVersion());
                } catch (Exception e) {
                    log.error("Ingestion failed", e);
                }
            }).start();

            return ResponseEntity.ok(Map.of("message", "Ingestion started successfully"));
        } catch (Exception e) {
            log.error("Failed to start ingestion", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
