package com.novel.splitter.core;

import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 场景组装器
 * <p>
 * 将章节内的段落进一步切分为 Scene。
 * 升级版：支持填充 RAG 元数据，支持 canSplit 标记。
 * </p>
 */
public class SceneAssembler {

    // 目标场景长度（软限制）
    private static final int TARGET_SCENE_LENGTH = 1200;

    /**
     * 组装所有章节的 Scene
     *
     * @param chapters      章节列表
     * @param allParagraphs 所有段落
     * @param novelName     小说名称（用于填充元数据）
     * @return Scene 列表
     */
    public List<Scene> assemble(List<Chapter> chapters, List<RawParagraph> allParagraphs, String novelName) {
        List<Scene> scenes = new ArrayList<>();

        for (Chapter chapter : chapters) {
            scenes.addAll(splitChapterToScenes(chapter, allParagraphs, novelName));
        }

        return scenes;
    }

    /**
     * 切分单个章节
     */
    private List<Scene> splitChapterToScenes(Chapter chapter, List<RawParagraph> allParagraphs, String novelName) {
        List<Scene> chapterScenes = new ArrayList<>();
        List<RawParagraph> buffer = new ArrayList<>();
        int currentLength = 0;
        int startParaIndex = chapter.getStartParagraphIndex();

        // 遍历该章节的所有段落
        for (int i = chapter.getStartParagraphIndex(); i <= chapter.getEndParagraphIndex(); i++) {
            RawParagraph p = allParagraphs.get(i);
            
            // 忽略空行
            if (p.isEmpty()) {
                continue; 
            }

            buffer.add(p);
            currentLength += p.getContent().length();

            // 简单逻辑：超过目标长度就切分
            if (currentLength >= TARGET_SCENE_LENGTH) {
                chapterScenes.add(buildScene(chapter, buffer, startParaIndex, i, novelName));

                // 重置缓冲区
                buffer = new ArrayList<>();
                currentLength = 0;
                startParaIndex = i + 1;
            }
        }

        // 处理剩余部分
        if (!buffer.isEmpty()) {
            chapterScenes.add(buildScene(chapter, buffer, startParaIndex, chapter.getEndParagraphIndex(), novelName));
        }

        return chapterScenes;
    }

    private Scene buildScene(Chapter chapter, List<RawParagraph> paragraphs, int startIdx, int endIdx, String novelName) {
        StringBuilder text = new StringBuilder();
        for (RawParagraph p : paragraphs) {
            text.append(p.getContent()).append("\n");
        }
        int wordCount = text.length();

        // RAG 元数据填充
        SceneMetadata metadata = SceneMetadata.builder()
                .novel(novelName)
                .chapterTitle(chapter.getTitle())
                .chapterIndex(chapter.getIndex())
                .startParagraph(startIdx)
                .endParagraph(endIdx)
                .chunkType("scene")
                .role("narration") // 默认均为叙述，后续可通过 NLP 识别 Dialogue
                .build();

        // 策略：如果字数超过目标长度的 1.5 倍，建议可再切分
        boolean canSplit = wordCount > (TARGET_SCENE_LENGTH * 1.5);

        return Scene.builder()
                .id(UUID.randomUUID().toString())
                .chapterTitle(chapter.getTitle())
                .chapterIndex(chapter.getIndex())
                .startParagraphIndex(startIdx)
                .endParagraphIndex(endIdx)
                .text(text.toString())
                .wordCount(wordCount)
                .canSplit(canSplit)
                .metadata(metadata)
                .build();
    }
}
