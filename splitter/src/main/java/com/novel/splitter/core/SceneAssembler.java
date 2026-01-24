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
 * 当前实现为基础版：基于字数阈值进行硬切分，不考虑语义完整性（将在后续版本增强）。
 * </p>
 */
public class SceneAssembler {

    // 目标场景长度（软限制）
    private static final int TARGET_SCENE_LENGTH = 1200;

    /**
     * 组装所有章节的 Scene
     */
    public List<Scene> assemble(List<Chapter> chapters, List<RawParagraph> allParagraphs) {
        List<Scene> scenes = new ArrayList<>();

        for (Chapter chapter : chapters) {
            scenes.addAll(splitChapterToScenes(chapter, allParagraphs));
        }

        return scenes;
    }

    /**
     * 切分单个章节
     */
    private List<Scene> splitChapterToScenes(Chapter chapter, List<RawParagraph> allParagraphs) {
        List<Scene> chapterScenes = new ArrayList<>();
        List<RawParagraph> buffer = new ArrayList<>();
        int currentLength = 0;
        int startParaIndex = chapter.getStartParagraphIndex();

        // 遍历该章节的所有段落
        for (int i = chapter.getStartParagraphIndex(); i <= chapter.getEndParagraphIndex(); i++) {
            RawParagraph p = allParagraphs.get(i);
            
            // 即使是空行，在 Scene 内部也可以保留，或者选择忽略
            // 这里选择忽略空行不计入长度，但在文本中可以根据需求处理
            if (p.isEmpty()) {
                continue; 
            }

            buffer.add(p);
            currentLength += p.getContent().length();

            // 简单逻辑：超过目标长度就切分
            if (currentLength >= TARGET_SCENE_LENGTH) {
                chapterScenes.add(buildScene(chapter, buffer, startParaIndex, i));

                // 重置缓冲区
                buffer = new ArrayList<>();
                currentLength = 0;
                startParaIndex = i + 1;
            }
        }

        // 处理剩余部分
        if (!buffer.isEmpty()) {
            // 无论剩余多少，都作为一个 Scene（后续 Validation 层会告警过短的 Scene）
            chapterScenes.add(buildScene(chapter, buffer, startParaIndex, chapter.getEndParagraphIndex()));
        }

        return chapterScenes;
    }

    private Scene buildScene(Chapter chapter, List<RawParagraph> paragraphs, int startIdx, int endIdx) {
        StringBuilder text = new StringBuilder();
        for (RawParagraph p : paragraphs) {
            text.append(p.getContent()).append("\n");
        }

        return Scene.builder()
                .id(UUID.randomUUID().toString())
                .chapterTitle(chapter.getTitle())
                .chapterIndex(chapter.getIndex())
                .startParagraphIndex(startIdx)
                .endParagraphIndex(endIdx)
                .text(text.toString())
                .wordCount(text.length())
                .metadata(SceneMetadata.builder().build()) // 空元数据
                .build();
    }
}
