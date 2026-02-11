package com.novel.splitter.core;

import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.domain.model.SemanticSegment;
import com.novel.splitter.rule.DynamicWindowRule;
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
    // 目标场景长度（软限制）- 这里的常量仅作为 fallback 或 reference
    private static final int TARGET_SCENE_LENGTH = 1200;

    public SceneAssembler() {
        // 使用 Phase 2 的 ContextAwareSegmentBuilder
        this.segmentBuilder = new ContextAwareSegmentBuilder();
        this.splitRules = new ArrayList<>();
        // 使用 Phase 3 的 DynamicWindowRule
        this.splitRules.add(new DynamicWindowRule());
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
        String previousContext = ""; // 记录上一个 Scene 的上下文 (Phase 3 Requirement)

        for (SemanticSegment seg : segments) {
            // 评估是否需要切分
            boolean shouldSplit = false;
            
            // 遍历所有规则
            for (SplitRule rule : splitRules) {
                // Phase 3: 传递 buffer 以支持动态密度分析
                SplitRule.Decision decision = rule.evaluate(currentLength, buffer, seg);
                if (decision == SplitRule.Decision.MUST_SPLIT) {
                    shouldSplit = true;
                    break; 
                } else if (decision == SplitRule.Decision.CAN_SPLIT) {
                    shouldSplit = true;
                }
            }

            // 如果决定切分，且 buffer 非空
            if (shouldSplit && !buffer.isEmpty()) {
                // 构建并添加 Scene
                Scene scene = buildSceneFromSegments(chapter, buffer, sceneStartParaIdx, novelName, previousContext);
                chapterScenes.add(scene);
                
                // Phase 3: 上下文重叠 (Context Overlap)
                // 需求：保留上一个Scene的最后100-200字作为 prefix_context 字段
                // 1. 从当前生成的 Scene 文本中提取
                String sceneText = scene.getText();
                int contextLength = Math.min(sceneText.length(), 200);
                // 注意：getText() 可能包含末尾换行符
                previousContext = sceneText.substring(Math.max(0, sceneText.length() - contextLength)).trim();

                // 重置缓冲区
                buffer.clear();
                
                // 重新计算 currentLength
                currentLength = 0;

                // 更新下一个 Scene 的起始索引
                if (!seg.getParagraphs().isEmpty()) {
                    sceneStartParaIdx = seg.getParagraphs().get(0).getIndex();
                }
            }

            buffer.add(seg);
            currentLength += calculateLength(seg);
        }

        // 处理剩余部分
        if (!buffer.isEmpty()) {
            chapterScenes.add(buildSceneFromSegments(chapter, buffer, sceneStartParaIdx, novelName, previousContext));
        }

        return chapterScenes;
    }

    private int calculateLength(SemanticSegment seg) {
        return seg.getParagraphs().stream().mapToInt(p -> p.getContent().length()).sum();
    }

    private Scene buildSceneFromSegments(Chapter chapter, List<SemanticSegment> segments, int startIdx, String novelName, String prefixContext) {
        // 展平为 RawParagraph 列表，但同时也传递原始 segments 以便计算元数据
        List<RawParagraph> paragraphs = segments.stream()
                .flatMap(s -> s.getParagraphs().stream())
                .collect(Collectors.toList());
        
        int endIdx = paragraphs.isEmpty() ? startIdx : paragraphs.get(paragraphs.size() - 1).getIndex();
        
        return buildScene(chapter, paragraphs, segments, startIdx, endIdx, novelName, prefixContext);
    }

    private Scene buildScene(Chapter chapter, List<RawParagraph> paragraphs, List<SemanticSegment> segments, int startIdx, int endIdx, String novelName, String prefixContext) {
        StringBuilder text = new StringBuilder();
        for (RawParagraph p : paragraphs) {
            text.append(p.getContent()).append("\n");
        }
        int wordCount = text.length();

        // Phase 4: Evolution (自我进化) - 反馈机制 (Heuristic)
        // 计算对话比例作为密度参考
        long dialogueCount = segments.stream().filter(s -> "DIALOGUE".equals(s.getType())).count();
        double densityScore = segments.isEmpty() ? 0.0 : (1.0 - (double) dialogueCount / segments.size());

        // 计算质量得分 (简单的 PPL 模拟：结尾是否完整)
        double qualityScore = 1.0;
        if (text.length() > 0) {
            char lastChar = text.charAt(text.length() - 2); // 倒数第二个字符（排除换行符）
            if (lastChar != '。' && lastChar != '”' && lastChar != '！' && lastChar != '？' && lastChar != '.' && lastChar != '}') {
                qualityScore = 0.7; // 结尾不完整，降权
            }
        }

        // RAG 元数据填充
        SceneMetadata metadata = SceneMetadata.builder()
                .novel(novelName)
                .chapterTitle(chapter.getTitle())
                .chapterIndex(chapter.getIndex())
                .startParagraph(startIdx)
                .endParagraph(endIdx)
                .chunkType("scene")
                .role("narration")
                .densityScore(densityScore)
                .qualityScore(qualityScore)
                .build();

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
                .prefixContext(prefixContext)
                .build();
    }
}
