package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.model.dto.ChunkPreviewDto;
import com.novel.splitter.application.model.dto.SplitPreviewRequestDto;
import com.novel.splitter.application.service.split.SplitPreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Split Preview", description = "Preview split chunks in memory")
@RestController
@RequestMapping("/api/split")
@RequiredArgsConstructor
public class SplitPreviewController {

    private final SplitPreviewService splitPreviewService;

    @Operation(summary = "Preview split chunks")
    @PostMapping("/preview")
    public List<ChunkPreviewDto> preview(@Valid @RequestBody SplitPreviewRequestDto request) {
        return splitPreviewService.preview(request);
    }
}
