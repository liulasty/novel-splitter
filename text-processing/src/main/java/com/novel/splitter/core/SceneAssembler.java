package com.novel.splitter.core;

import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.domain.model.SemanticSegment;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.rule.DynamicWindowRule;
import com.novel.splitter.rule.SplitRule;
import com.novel.splitter.domain.task.IngestProgress;
import com.novel.splitter.validation.core.SemanticSegmentBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * 场景组装器
 * <p>
 * 该类是 NLP/RAG 系统的核心组件之一，负责将小说章节内的物理段落进一步聚合和切分为具有完整语义的场景 (Scene)。
 * 升级版设计：引入了基于上下文感知的 SemanticSegmentBuilder 以及基于动态窗口等策略的 Rule 体系，
 * 旨在生成质量更高、更适合 RAG 检索与生成的文本块。
 * </p>
 */
public class SceneAssembler {

    /**
     * 语义段构建器，用于将零散的段落组合成初步的语义单元
     */
    private final SemanticSegmentBuilder segmentBuilder;
    /**
     * 切分规则列表，用于评估是否需要在当前位置进行场景切分
     */
    private final List<SplitRule> splitRules;
    /**
     * 语义密度分析器，用于评估场景的文本特征密度（如对话比例等）
     */
    private final SemanticDensityAnalyzer densityAnalyzer;
    /**
     * 目标场景长度（软限制）- 这里的常量仅作为回退 (fallback) 或参考 (reference) 标准
     */
    private static final int TARGET_SCENE_LENGTH = 1200;

    /**
     * 默认构造函数
     * 初始化时不提供 EmbeddingService，可能导致部分依赖向量计算的功能受限。
     */
    public SceneAssembler() {
        this(null);
    }

    /**
     * 带有 Embedding 服务的构造函数
     *
     * @param embeddingService 文本向量化服务，用于上下文感知的语义段构建
     */
    public SceneAssembler(EmbeddingService embeddingService) {
        // 使用 Phase 2 引入的 ContextAwareSegmentBuilder，支持基于向量的上下文感知
        this.segmentBuilder = new ContextAwareSegmentBuilder(embeddingService);
        this.splitRules = new ArrayList<>();
        // 使用 Phase 3 引入的动态窗口规则，根据文本特征动态调整切分边界
        this.splitRules.add(new DynamicWindowRule());
        this.densityAnalyzer = new SemanticDensityAnalyzer();
    }

    /**
     * 组装所有章节的 Scene，不带进度回调。
     *
     * @param chapters      已识别的章节列表
     * @param allParagraphs 全文的所有物理段落列表
     * @param novelName     小说名称（用于填充场景的元数据）
     * @return 组装完成的全部 Scene 列表
     */
    public List<Scene> assemble(List<Chapter> chapters, List<RawParagraph> allParagraphs, String novelName) {
        return assemble(chapters, allParagraphs, novelName, null);
    }

    /**
     * 组装所有章节的 Scene，支持进度回调以便在 UI 或日志中展示处理进度。
     *
     * @param chapters         已识别的章节列表
     * @param allParagraphs    全文的所有物理段落列表
     * @param novelName        小说名称（用于填充场景的元数据）
     * @param progressCallback 进度回调函数，接收进度百分比和状态描述信息
     * @return 组装完成的全部 Scene 列表
     */
    public List<Scene> assemble(List<Chapter> chapters, List<RawParagraph> allParagraphs, String novelName, BiConsumer<Integer, String> progressCallback) {
        List<Scene> scenes = new ArrayList<>();
        int totalChapters = chapters.size();

        // 子阶段 A：模拟或通知章节识别阶段的进度（进度区间 15% → 30%）
        if (progressCallback != null) {
            for (int i = 0; i < totalChapters; i++) {
                int progress = IngestProgress.calc(IngestProgress.CHAPTER_START, IngestProgress.CHAPTER_END, i, totalChapters);
                progressCallback.accept(progress, String.format("正在识别章节：第 %d/%d 章", i + 1, totalChapters));
            }
        }

        // 子阶段 B：模拟或通知段落切分阶段的进度（进度区间 30% → 45%）
        if (progressCallback != null) {
            for (int i = 0; i < totalChapters; i++) {
                int progress = IngestProgress.calc(IngestProgress.PARAGRAPH_START, IngestProgress.PARAGRAPH_END, i, totalChapters);
                progressCallback.accept(progress, String.format("正在切分段落：第 %d/%d 章", i + 1, totalChapters));
            }
        }

        // 子阶段 C：执行实际的 Scene 组装逻辑，并上报进度（进度区间 45% → 55%）
        // 估算总场景数，用于进度条计算（假设平均每 15 个段落构成一个场景）
        int estimatedTotalScenes = Math.max(1, allParagraphs.size() / 15);
        int scenesCount = 0;

        for (Chapter chapter : chapters) {
            // 对单个章节内的段落进行场景切分
            List<Scene> chapterScenes = splitChapterToScenes(chapter, allParagraphs, novelName);
            scenes.addAll(chapterScenes);
            
            scenesCount += chapterScenes.size();
            // 每处理完 50 个场景，触发一次进度回调，避免过度频繁的 UI 刷新
            if (progressCallback != null && scenesCount % 50 == 0) {
                int progress = IngestProgress.calc(IngestProgress.SCENE_START, IngestProgress.SCENE_END, Math.min(scenesCount, estimatedTotalScenes), estimatedTotalScenes);
                progressCallback.accept(progress, String.format("正在组装场景：已完成 %d 个", scenesCount));
            }
        }
        
        // 确保在所有章节处理完毕后，发送最后一个完成状态的回调
        if (progressCallback != null) {
            progressCallback.accept(IngestProgress.SCENE_END, String.format("正在组装场景：已完成 %d 个", scenesCount));
        }

        return scenes;
    }

    /**
     * 根据章节信息，从全局段落列表中提取属于该章节的段落，并进行场景切分。
     *
     * @param chapter       当前处理的章节对象
     * @param allParagraphs 全局段落列表
     * @param novelName     小说名称
     * @return 该章节内切分出的 Scene 列表
     */
    private List<Scene> splitChapterToScenes(Chapter chapter, List<RawParagraph> allParagraphs, String novelName) {
        int start = chapter.getStartParagraphIndex();
        int end = chapter.getEndParagraphIndex();
        // 边界保护：检查索引是否合法
        if (start > end || start >= allParagraphs.size()) {
            return new ArrayList<>();
        }
        // 防止结束索引越界
        end = Math.min(end, allParagraphs.size() - 1);
        // 提取当前章节对应的段落子列表
        List<RawParagraph> chapterParagraphs = allParagraphs.subList(start, end + 1);
        // 调用核心逻辑进行切分
        return splitChapterParagraphsToScenes(chapter, chapterParagraphs, novelName);
    }

    /**
     * 组装单个章节的 Scene，支持流式处理的对外接口。
     *
     * @param chapter           当前处理的章节对象
     * @param chapterParagraphs 该章节包含的原始段落列表
     * @param novelName         小说名称
     * @return 该章节内切分出的 Scene 列表
     */
    public List<Scene> assembleChapter(Chapter chapter, List<RawParagraph> chapterParagraphs, String novelName) {
        return splitChapterParagraphsToScenes(chapter, chapterParagraphs, novelName);
    }

    /**
     * 核心逻辑：将章节内的原始段落列表转换为具有上下文和元数据的 Scene 列表。
     *
     * @param chapter           章节对象
     * @param chapterParagraphs 章节内的原始段落列表
     * @param novelName         小说名称
     * @return 组装完成的 Scene 列表
     */
    private List<Scene> splitChapterParagraphsToScenes(Chapter chapter, List<RawParagraph> chapterParagraphs, String novelName) {
        List<Scene> chapterScenes = new ArrayList<>();
        
        // 检查段落列表是否为空
        if (chapterParagraphs == null || chapterParagraphs.isEmpty()) {
            return chapterScenes;
        }

        // 步骤 1 & 2：利用构建器将原始段落合并为语义段 (SemanticSegment)
        // 这一步通常会将对话及其相关的叙述动作合并，形成不可分割的最小语义单元
        List<SemanticSegment> segments = segmentBuilder.build(chapterParagraphs);

        // 步骤 3：基于规则引擎对语义段进行切分，组装成最终的 Scene
        List<SemanticSegment> buffer = new ArrayList<>(); // 用于暂存当前正在构建的场景的语义段
        int currentLength = 0; // 当前缓冲区的文本总长度
        int start = chapterParagraphs.get(0).getIndex();
        int sceneStartParaIdx = start; // 记录当前正在构建的 Scene 的起始段落索引
        String previousContext = ""; // 记录上一个 Scene 的上下文尾部，用于 RAG 上下文重叠 (Phase 3 需求)

        for (SemanticSegment seg : segments) {
            // 评估在当前语义段之后是否需要进行场景切分
            boolean shouldSplit = false;
            
            // 遍历所有注册的切分规则
            for (SplitRule rule : splitRules) {
                // Phase 3 特性：将当前缓冲区传递给规则引擎，以支持动态密度分析等高级规则
                SplitRule.Decision decision = rule.evaluate(currentLength, buffer, seg);
                if (decision == SplitRule.Decision.MUST_SPLIT) {
                    shouldSplit = true;
                    break; // 如果有强制切分规则命中，直接中断后续规则评估
                } else if (decision == SplitRule.Decision.CAN_SPLIT) {
                    shouldSplit = true; // 标记可切分，但继续评估其他规则
                }
            }

            // 如果规则引擎决定在此处切分，且缓冲区内已有内容
            if (shouldSplit && !buffer.isEmpty()) {
                // 构建并保存当前完成的 Scene
                Scene scene = buildSceneFromSegments(chapter, buffer, sceneStartParaIdx, novelName, previousContext);
                chapterScenes.add(scene);
                
                // Phase 3 增强：上下文重叠 (Context Overlap)
                // 需求：保留上一个 Scene 的最后 100-200 字，作为当前 Scene 的前置上下文 (prefix_context)
                // 1. 从当前生成的 Scene 文本中提取末尾部分
                String sceneText = scene.getText();
                int contextLength = Math.min(sceneText.length(), 200);
                // 注意：getText() 可能包含末尾换行符，提取后需进行 trim 处理
                previousContext = sceneText.substring(Math.max(0, sceneText.length() - contextLength)).trim();

                // 清空缓冲区，准备构建下一个 Scene
                buffer.clear();
                
                // 重置当前长度计数器
                currentLength = 0;

                // 更新下一个 Scene 的起始段落索引
                if (!seg.getParagraphs().isEmpty()) {
                    sceneStartParaIdx = seg.getParagraphs().get(0).getIndex();
                }
            }

            // 将当前语义段加入缓冲区，并累加长度
            buffer.add(seg);
            currentLength += calculateLength(seg);
        }

        // 处理循环结束后缓冲区中剩余的语义段，构建为最后一个 Scene
        if (!buffer.isEmpty()) {
            chapterScenes.add(buildSceneFromSegments(chapter, buffer, sceneStartParaIdx, novelName, previousContext));
        }

        return chapterScenes;
    }

    /**
     * 计算单个语义段包含的总字符数。
     *
     * @param seg 语义段对象
     * @return 字符长度
     */
    private int calculateLength(SemanticSegment seg) {
        return seg.getParagraphs().stream().mapToInt(p -> p.getContent().length()).sum();
    }

    /**
     * 根据缓冲的语义段列表构建一个 Scene 对象（包装方法）。
     *
     * @param chapter       当前章节
     * @param segments      构成该场景的语义段列表
     * @param startIdx      场景起始段落索引
     * @param novelName     小说名称
     * @param prefixContext 继承自上一场景的前置上下文文本
     * @return 构建完成的 Scene 对象
     */
    private Scene buildSceneFromSegments(Chapter chapter, List<SemanticSegment> segments, int startIdx, String novelName, String prefixContext) {
        // 将结构化的语义段展平为一维的 RawParagraph 列表
        // 注意：同时传递原始 segments 以便后续计算场景级别的元数据（如语义密度）
        List<RawParagraph> paragraphs = segments.stream()
                .flatMap(s -> s.getParagraphs().stream())
                .collect(Collectors.toList());
        
        // 确定该场景的结束段落索引
        int endIdx = paragraphs.isEmpty() ? startIdx : paragraphs.get(paragraphs.size() - 1).getIndex();
        
        return buildScene(chapter, paragraphs, segments, startIdx, endIdx, novelName, prefixContext);
    }

    /**
     * 实际构建 Scene 对象的内部方法。
     *
     * @param chapter       当前章节
     * @param paragraphs    构成该场景的原始段落列表
     * @param segments      构成该场景的语义段列表（用于分析）
     * @param startIdx      场景起始段落索引
     * @param endIdx        场景结束段落索引
     * @param novelName     小说名称
     * @param prefixContext 继承自上一场景的前置上下文文本
     * @return 构建并填充好元数据的 Scene 对象
     */
    private Scene buildScene(Chapter chapter, List<RawParagraph> paragraphs, List<SemanticSegment> segments, int startIdx, int endIdx, String novelName, String prefixContext) {
        // 拼接所有段落的文本内容
        StringBuilder text = new StringBuilder();
        for (RawParagraph p : paragraphs) {
            text.append(p.getContent()).append("\n");
        }
        int wordCount = text.length();

        // Phase 4 特性：Evolution (自我进化) - 引入启发式 (Heuristic) 反馈机制
        // 通过密度分析器计算对话比例，作为该场景语义密度的参考指标
        double densityScore = densityAnalyzer.calculateDensityScore(segments);

        // 计算质量得分 (简单的 PPL [困惑度] 模拟评估)
        // 检查场景的文本结尾是否完整（是否以标点符号结束）
        double qualityScore = 1.0;
        if (text.length() >= 2) {
            char lastChar = text.charAt(text.length() - 2); // 取倒数第二个字符（排除最后的换行符）
            // 如果结尾不是常见的结束标点，认为句子可能被截断，降低质量得分
            if (lastChar != '。' && lastChar != '”' && lastChar != '！' && lastChar != '？' && lastChar != '.' && lastChar != '}') {
                qualityScore = 0.7; // 结尾不完整，实施降权惩罚
            }
        }

        // 构建适用于 RAG 检索的元数据 (Metadata)
        SceneMetadata metadata = SceneMetadata.builder()
                .novel(novelName)
                .chapterTitle(chapter.getTitle())
                .chapterIndex(chapter.getIndex())
                .startParagraph(startIdx)
                .endParagraph(endIdx)
                .chunkType("scene")      // 标记 chunk 的层级为 scene
                .role("narration")       // 默认角色标记为叙述
                .densityScore(densityScore) // 记录语义密度得分
                .qualityScore(qualityScore) // 记录内容质量得分
                .build();

        // 标记该场景是否过长，提示后续流程可能需要进行二次拆分（软限制判断）
        boolean canSplit = wordCount > (TARGET_SCENE_LENGTH * 1.5);

        // 构建并返回完整的 Scene 实体
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
                .prefixContext(prefixContext) // 注入用于上下文连贯性的重叠文本
                .build();
    }
}
