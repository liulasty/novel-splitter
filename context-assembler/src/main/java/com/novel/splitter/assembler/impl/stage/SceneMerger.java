package com.novel.splitter.assembler.impl.stage;

import com.novel.splitter.assembler.config.AssemblerConfig;
import com.novel.splitter.assembler.support.TokenCounter;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Stage 3: 邻接合并 (Adjacent Merge)
 * <p>
 * 当检索到的 Scene 在原文中连续时，合并为更完整的剧情块，增强连贯性。
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SceneMerger {

    private final TokenCounter tokenCounter;

    public List<Scene> merge(List<Scene> scenes, AssemblerConfig config) {
        if (!config.isEnableMerge() || scenes == null || scenes.isEmpty()) {
            return scenes;
        }

        // 1. 按位置排序 (ChapterIndex -> StartParagraphIndex)
        List<Scene> sorted = new ArrayList<>(scenes);
        sorted.sort(Comparator.comparingInt(Scene::getChapterIndex)
                .thenComparingInt(Scene::getStartParagraphIndex));

        List<Scene> result = new ArrayList<>();
        Scene currentMerge = null;

        for (Scene next : sorted) {
            if (currentMerge == null) {
                currentMerge = next;
                continue;
            }

            if (isAdjacent(currentMerge, next)) {
                // 尝试合并
                String mergedText = currentMerge.getText() + "\n\n---\n\n" + next.getText();
                int mergedTokens = tokenCounter.count(mergedText);

                // 检查是否超过单块限制 (防止无限合并)
                // 注意：这里检查的是单块最大长度，不是总 Context 长度
                if (mergedTokens <= config.getMaxChunkLength()) {
                    // 执行合并
                    currentMerge = createMergedScene(currentMerge, next, mergedText);
                } else {
                    // 超长，不合并，提交 current，next 成为新的 current
                    result.add(currentMerge);
                    currentMerge = next;
                }
            } else {
                // 不邻接，提交 current
                result.add(currentMerge);
                currentMerge = next;
            }
        }

        if (currentMerge != null) {
            result.add(currentMerge);
        }

        return result;
    }

    private boolean isAdjacent(Scene s1, Scene s2) {
        // 必须同章节 (跨章节暂不合并，简化逻辑)
        if (s1.getChapterIndex() != s2.getChapterIndex()) {
            return false;
        }
        
        // 允许少许重叠或间隔 (例如差值 <= 1)
        // s1.end = 10, s2.start = 11 -> adjacent
        // s1.end = 10, s2.start = 12 -> gap = 1 (ok)
        int gap = s2.getStartParagraphIndex() - s1.getEndParagraphIndex();
        return gap <= 2 && gap >= -5; // 允许少量重叠或间隔
    }

    private Scene createMergedScene(Scene s1, Scene s2, String mergedText) {
        // 创建新 Scene，保留 s1 的 ID 作为主 ID，或者生成组合 ID
        Scene merged = new Scene();
        merged.setId(s1.getId()); // 保持主 ID，或 s1.getId() + "+" + s2.getId()
        merged.setChapterTitle(s1.getChapterTitle());
        merged.setChapterIndex(s1.getChapterIndex());
        merged.setStartParagraphIndex(s1.getStartParagraphIndex());
        merged.setEndParagraphIndex(s2.getEndParagraphIndex());
        merged.setText(mergedText);
        merged.setWordCount(s1.getWordCount() + s2.getWordCount());
        
        // 合并分数 (取最大值或平均值)
        double score1 = s1.getScore() != null ? s1.getScore() : 0.0;
        double score2 = s2.getScore() != null ? s2.getScore() : 0.0;
        merged.setScore(Math.max(score1, score2));

        // 合并 Metadata
        SceneMetadata meta = s1.getMetadata(); // 浅拷贝
        if (meta == null) meta = new SceneMetadata();
        else {
             // Deep copy ideally, but simple builder copy here
             meta = SceneMetadata.builder()
                     .novel(meta.getNovel())
                     .version(meta.getVersion())
                     .chapterTitle(meta.getChapterTitle())
                     .chapterIndex(meta.getChapterIndex())
                     .startParagraph(s1.getStartParagraphIndex())
                     .endParagraph(s2.getEndParagraphIndex())
                     .chunkType("merged_scene")
                     .build();
        }
        
        // 记录原始 IDs
        List<String> ids = new ArrayList<>();
        addIds(ids, s1);
        addIds(ids, s2);
        
        Map<String, Object> extra = meta.getExtra();
        if (extra == null) extra = new HashMap<>();
        extra.put("mergedSceneIds", ids);
        meta.setExtra(extra);
        
        merged.setMetadata(meta);
        return merged;
    }
    
    @SuppressWarnings("unchecked")
    private void addIds(List<String> collector, Scene s) {
        if (s.getMetadata() != null && s.getMetadata().getExtra() != null && s.getMetadata().getExtra().containsKey("mergedSceneIds")) {
            collector.addAll((List<String>) s.getMetadata().getExtra().get("mergedSceneIds"));
        } else {
            collector.add(s.getId());
        }
    }
}
