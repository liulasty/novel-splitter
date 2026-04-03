package com.novel.splitter.core;

import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.domain.model.SemanticSegment;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.rule.DynamicWindowRule;
import com.novel.splitter.rule.SplitRule;
import com.novel.splitter.infrastructure.progress.IngestProgress;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
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
    private final SemanticDensityAnalyzer densityAnalyzer;
    // 目标场景长度（软限制）- 这里的常量仅作为 fallback 或 reference
    private static final int TARGET_SCENE_LENGTH = 1200;

    public SceneAssembler() {
        this(null);
    }

    public SceneAssembler(EmbeddingService embeddingService) {
        // 使用 Phase 2 的 ContextAwareSegmentBuilder
        this.segmentBuilder = new ContextAwareSegmentBuilder(embeddingService);
        this.splitRules = new ArrayList<>();
        // 使用 Phase 3 的 DynamicWindowRule
        this.splitRules.add(new DynamicWindowRule());
        this.densityAnalyzer = new SemanticDensityAnalyzer();
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
        return assemble(chapters, allParagraphs, novelName, null);
    }

    /**
     * 组装所有章节的 Scene (支持进度回调)
     */
    public List<Scene> assemble(List<Chapter> chapters, List<RawParagraph> allParagraphs, String novelName, BiConsumer<Integer, String> progressCallback) {
        List<Scene> scenes = new ArrayList<>();
        int totalChapters = chapters.size();

        // 子阶段 A：ChapterRecognizer 识别章节（15% → 30%）
        if (progressCallback != null) {
            for (int i = 0; i < totalChapters; i++) {
                int progress = IngestProgress.calc(IngestProgress.CHAPTER_START, IngestProgress.CHAPTER_END, i, totalChapters);
                progressCallback.accept(progress, String.format("正在识别章节：第 %d/%d 章", i + 1, totalChapters));
            }
        }

        // 子阶段 B：MarkdownParagraphSplitter 段落切分（30% → 45%）
        if (progressCallback != null) {
            for (int i = 0; i < totalChapters; i++) {
                int progress = IngestProgress.calc(IngestProgress.PARAGRAPH_START, IngestProgress.PARAGRAPH_END, i, totalChapters);
                progressCallback.accept(progress, String.format("正在切分段落：第 %d/%d 章", i + 1, totalChapters));
            }
        }

        // 子阶段 C：Scene 组装（45% → 55%）
        int estimatedTotalScenes = Math.max(1, allParagraphs.size() / 15);
        int scenesCount = 0;

        for (Chapter chapter : chapters) {
            List<Scene> chapterScenes = splitChapterToScenes(chapter, allParagraphs, novelName);
            scenes.addAll(chapterScenes);
            
            scenesCount += chapterScenes.size();
            if (progressCallback != null && scenesCount % 50 == 0) {
                int progress = IngestProgress.calc(IngestProgress.SCENE_START, IngestProgress.SCENE_END, Math.min(scenesCount, estimatedTotalScenes), estimatedTotalScenes);
                progressCallback.accept(progress, String.format("正在组装场景：已完成 %d 个", scenesCount));
            }
        }
        
        // 确保最后一个上报
        if (progressCallback != null) {
            progressCallback.accept(IngestProgress.SCENE_END, String.format("正在组装场景：已完成 %d 个", scenesCount));
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
        double densityScore = densityAnalyzer.calculateDensityScore(segments);

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
