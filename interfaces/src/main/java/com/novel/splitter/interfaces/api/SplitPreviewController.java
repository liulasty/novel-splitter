package com.novel.splitter.interfaces.api;

import com.novel.splitter.core.ContextAwareSegmentBuilder;
import com.novel.splitter.core.MarkdownParagraphSplitter;
import com.novel.splitter.core.SceneAssembler;
import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SemanticSegment;
import com.novel.splitter.application.model.dto.ChunkPreviewDto;
import com.novel.splitter.application.model.dto.SplitPreviewRequestDto;
import com.novel.splitter.domain.strategy.OverlapChunkingStrategy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Tag(name = "Split Preview", description = "Preview split chunks in memory")
@RestController
@RequestMapping("/api/split")
public class SplitPreviewController {

    @Operation(summary = "Preview split chunks")
    @PostMapping("/preview")
    public List<ChunkPreviewDto> preview(@Valid @RequestBody SplitPreviewRequestDto request) {
        String sourceText = request.getSourceText();
        String strategy = request.getStrategy() != null ? request.getStrategy().toLowerCase() : "scene";
        Integer maxTokens = request.getMaxTokens() != null ? request.getMaxTokens() : 1200;
        Integer overlapTokens = request.getOverlapTokens() != null ? request.getOverlapTokens() : 0;

        List<String> lines = Arrays.asList(sourceText.split("\n"));
        MarkdownParagraphSplitter splitter = new MarkdownParagraphSplitter();
        List<RawParagraph> paragraphs = splitter.split(lines);

        List<ChunkPreviewDto> result = new ArrayList<>();

        if ("segment".equals(strategy) || "semantic".equals(strategy)) {
            ContextAwareSegmentBuilder segmentBuilder = new ContextAwareSegmentBuilder();
            List<SemanticSegment> segments = segmentBuilder.build(paragraphs);
            for (int i = 0; i < segments.size(); i++) {
                SemanticSegment segment = segments.get(i);
                StringBuilder sb = new StringBuilder();
                for (RawParagraph p : segment.getParagraphs()) {
                    sb.append(p.getContent()).append("\n");
                }
                String text = sb.toString().trim();
                result.add(ChunkPreviewDto.builder()
                        .index(i)
                        .text(text)
                        .length(text.length())
                        .type(segment.getType() != null ? segment.getType() : "TEXT")
                        .build());
            }
        } else {
            // Default to SceneAssembler
            SceneAssembler sceneAssembler = new SceneAssembler();
            Chapter dummyChapter = Chapter.builder()
                    .title("Preview Chapter")
                    .index(1)
                    .startParagraphIndex(0)
                    .endParagraphIndex(paragraphs.isEmpty() ? 0 : paragraphs.size() - 1)
                    .build();

            List<Scene> scenes = sceneAssembler.assembleChapter(dummyChapter, paragraphs, "Preview Novel");

            // Apply overlap chunking if requested
            if (maxTokens > 0 && ("overlap".equals(strategy) || maxTokens < 10000)) {
                if (overlapTokens >= maxTokens) {
                    overlapTokens = Math.max(0, maxTokens - 1);
                }
                OverlapChunkingStrategy chunkingStrategy = new OverlapChunkingStrategy(maxTokens, overlapTokens);
                List<Scene> allChunks = new ArrayList<>();
                for (Scene scene : scenes) {
                    if (scene.getText().length() > maxTokens) {
                        allChunks.addAll(chunkingStrategy.split(scene));
                    } else {
                        allChunks.add(scene);
                    }
                }
                scenes = allChunks;
            }

            for (int i = 0; i < scenes.size(); i++) {
                Scene scene = scenes.get(i);
                String text = scene.getText().trim();
                result.add(ChunkPreviewDto.builder()
                        .index(i)
                        .text(text)
                        .length(text.length())
                        .type("SCENE")
                        .build());
            }
        }

        return result;
    }
}
