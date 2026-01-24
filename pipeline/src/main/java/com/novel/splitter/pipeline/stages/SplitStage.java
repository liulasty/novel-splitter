package com.novel.splitter.pipeline.stages;

import com.novel.splitter.core.ChapterRecognizer;
import com.novel.splitter.core.ParagraphSplitter;
import com.novel.splitter.core.SceneAssembler;
import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.pipeline.api.Stage;
import com.novel.splitter.pipeline.context.PipelineContext;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 核心切分阶段
 * 编排 Splitter 层的原子能力
 */
@Slf4j
public class SplitStage implements Stage {
    private final ParagraphSplitter paragraphSplitter = new ParagraphSplitter();
    private final ChapterRecognizer chapterRecognizer = new ChapterRecognizer();
    private final SceneAssembler sceneAssembler = new SceneAssembler();

    @Override
    public void process(PipelineContext context) {
        // 1. 物理切分
        List<RawParagraph> paragraphs = paragraphSplitter.split(context.getRawLines());
        context.setParagraphs(paragraphs);
        log.info("Split into {} paragraphs", paragraphs.size());

        // 2. 章节识别
        List<Chapter> chapters = chapterRecognizer.recognize(paragraphs);
        context.setChapters(chapters);
        log.info("Recognized {} chapters", chapters.size());

        // 3. Scene 组装 (传入 novelName 用于元数据填充)
        List<Scene> scenes = sceneAssembler.assemble(chapters, paragraphs, context.getNovelName());
        context.setScenes(scenes);
        log.info("Assembled {} scenes", scenes.size());
    }
}
