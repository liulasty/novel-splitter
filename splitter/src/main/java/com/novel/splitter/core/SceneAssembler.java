package com.novel.splitter.core;

import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.domain.model.SemanticSegment;
import com.novel.splitter.rule.LengthRule;
import com.novel.splitter.rule.SplitRule;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 场景组装器
 * <p>
 * 将章节内的段落进一步切分为 Scene。
 * 升级版：支持 SemanticSegmentBuilder 和 Rule 体系。
 * </p>
 */
public class SceneAssembler {

    private final SemanticSegmentBuilder segmentBuilder;
    private final List<SplitRule> splitRules;
    // 目标场景长度（软限制）
    private static final int TARGET_SCENE_LENGTH = 1200;

    public SceneAssembler() {
        this.segmentBuilder = new SemanticSegmentBuilder();
        this.splitRules = new ArrayList<>();
        // 默认规则配置：目标 1200 字，最大 3000 字
        this.splitRules.add(new LengthRule(TARGET_SCENE_LENGTH, 3000));
    }

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
        
        // 1. 获取本章节的原始段落
        int start = chapter.getStartParagraphIndex();
        int end = chapter.getEndParagraphIndex();
        if (start > end || start >= allParagraphs.size()) {
            return chapterScenes;
        }
        end = Math.min(end, allParagraphs.size() - 1);
        List<RawParagraph> chapterParagraphs = allParagraphs.subList(start, end + 1);

        // 2. 构建语义段 (合并对话等)
        List<SemanticSegment> segments = segmentBuilder.build(chapterParagraphs);

        // 3. 基于规则切分
        List<SemanticSegment> buffer = new ArrayList<>();
        int currentLength = 0;
        int sceneStartParaIdx = start; // 记录当前 Scene 的起始段落索引

        for (SemanticSegment seg : segments) {
            // 评估是否需要切分
            boolean shouldSplit = false;
            
            // 遍历所有规则
            for (SplitRule rule : splitRules) {
                SplitRule.Decision decision = rule.evaluate(currentLength, seg);
                if (decision == SplitRule.Decision.MUST_SPLIT) {
                    shouldSplit = true;
                    break; // 只要有一个 MUST，就必须切
                } else if (decision == SplitRule.Decision.CAN_SPLIT) {
                    shouldSplit = true;
                    // CAN_SPLIT 可以被后续规则否决吗？暂时简单处理，认为 CAN 就是切
                }
            }

            // 如果决定切分，且 buffer 非空
            if (shouldSplit && !buffer.isEmpty()) {
                // 构建并添加 Scene
                Scene scene = buildSceneFromSegments(chapter, buffer, sceneStartParaIdx, novelName);
                chapterScenes.add(scene);
                
                // 更新下一个 Scene 的起始索引
                // 当前 Scene 的结束索引是 buffer 中最后一个 segment 的最后一个 paragraph index
                // 所以下一个 Scene 的起始索引是当前 seg 的第一个 paragraph index
                if (!seg.getParagraphs().isEmpty()) {
                    sceneStartParaIdx = seg.getParagraphs().get(0).getIndex();
                }

                // 重置缓冲区
                buffer.clear();
                currentLength = 0;
            }

            buffer.add(seg);
            currentLength += calculateLength(seg);
        }

        // 处理剩余部分
        if (!buffer.isEmpty()) {
            chapterScenes.add(buildSceneFromSegments(chapter, buffer, sceneStartParaIdx, novelName));
        }

        return chapterScenes;
    }

    private int calculateLength(SemanticSegment seg) {
        return seg.getParagraphs().stream().mapToInt(p -> p.getContent().length()).sum();
    }

    private Scene buildSceneFromSegments(Chapter chapter, List<SemanticSegment> segments, int startIdx, String novelName) {
        // 展平为 RawParagraph 列表
        List<RawParagraph> paragraphs = segments.stream()
                .flatMap(s -> s.getParagraphs().stream())
                .collect(Collectors.toList());
        
        int endIdx = paragraphs.isEmpty() ? startIdx : paragraphs.get(paragraphs.size() - 1).getIndex();
        
        return buildScene(chapter, paragraphs, startIdx, endIdx, novelName);
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
