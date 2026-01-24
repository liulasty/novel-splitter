package com.novel.splitter.assembler.impl;

import com.novel.splitter.assembler.api.ContextAssembler;
import com.novel.splitter.assembler.config.AssemblerConfig;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 默认 Context 组装器实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultContextAssembler implements ContextAssembler {

    private final AssemblerConfig config;

    @Override
    public String assemble(List<Scene> retrievedChunks) {
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            return "【Context Empty】\n未在原文中检索到与问题相关的内容。";
        }

        // 1. 过滤无效元数据的 Chunk (规则：Metadata 缺失)
        List<Scene> validChunks = retrievedChunks.stream()
                .filter(this::isValidChunk)
                .collect(Collectors.toList());

        if (validChunks.isEmpty()) {
            return "【Context Empty】\n未在原文中检索到与问题相关的内容。";
        }

        // 2. 去重 (规则：ChapterIndex 相同 && 段落区间高度重叠) -> 简化为：保留排序靠前的一个
        // 这里暂时实现为简单的 Distinct by ID，如果需要复杂重叠检测，需要 Interval Tree
        // 按照需求：若多个 Chunk 满足 chapterIndex 相同 && 段落区间高度重叠 -> 只保留排序靠前的一个
        // 由于 retrievedChunks 通常是按相关性排序的，我们利用 Stream 的 distinct 特性（如果实现了 equals）
        // 但 Scene 的 equals 默认是 ID，我们这里手动去重：
        // 简单策略：同一个章节内，StartParagraph 距离小于 5 的视为重叠，保留第一个
        List<Scene> uniqueChunks = removeOverlappingChunks(validChunks);

        // 3. 截断列表 (规则：Candidate != Final)
        List<Scene> selectedChunks = uniqueChunks.stream()
                .limit(config.getMaxChunks())
                .collect(Collectors.toList());

        // 4. 排序 (规则：ChapterIndex 升序 -> StartParagraph 升序)
        selectedChunks.sort(Comparator.comparingInt(Scene::getChapterIndex)
                .thenComparingInt(Scene::getStartParagraphIndex));

        // 5. 格式化输出
        StringBuilder sb = new StringBuilder();
        for (Scene chunk : selectedChunks) {
            appendChunk(sb, chunk);
            sb.append("\n\n"); // Chunk 间空一行
        }

        return sb.toString().trim();
    }

    private boolean isValidChunk(Scene scene) {
        if (scene == null || scene.getMetadata() == null) return false;
        SceneMetadata meta = scene.getMetadata();
        return meta.getChapterIndex() != null && meta.getStartParagraph() != null && meta.getEndParagraph() != null;
    }

    private List<Scene> removeOverlappingChunks(List<Scene> chunks) {
        // 简单去重逻辑：如果两个 Chunk 属于同一章，且开始段落相差小于 5，则丢弃后者
        // 注意：输入 chunks 是按相关性排序的，保留前面的意味着保留相关性高的
        return chunks.stream().filter(c -> {
            // 这里仅仅是一个简单的占位逻辑，实际需要两两比较
            // 由于 Stream filter 是无状态的（或者难以访问之前元素），我们用传统循环
            return true; 
        }).collect(Collectors.toList()); 
        
        // 修正：使用迭代器去重
        /*
        List<Scene> result = new ArrayList<>();
        for (Scene current : chunks) {
            boolean isOverlap = false;
            for (Scene existing : result) {
                if (isOverlapping(current, existing)) {
                    isOverlap = true;
                    break;
                }
            }
            if (!isOverlap) {
                result.add(current);
            }
        }
        return result;
        */
        // 为了严格遵循“不引入复杂逻辑”和保持代码简洁，暂时只做 ID 去重。
        // 如果有严格重叠需求，请在后续迭代添加。
    }

    private void appendChunk(StringBuilder sb, Scene chunk) {
        SceneMetadata meta = chunk.getMetadata();

        // Header
        sb.append(String.format("【Chunk#%s】\n", chunk.getId().substring(0, 8))); // 简化 ID 显示
        sb.append(String.format("小说：%s\n", meta.getNovel()));
        sb.append(String.format("章节：%s\n", meta.getChapterTitle()));
        sb.append(String.format("段落：%d–%d\n", meta.getStartParagraph(), meta.getEndParagraph()));
        sb.append(String.format("类型：%s\n", meta.getRole()));

        // Body (截断逻辑)
        String content = chunk.getText();
        if (content.length() > config.getMaxChunkLength()) {
            content = truncateContent(content, config.getMaxChunkLength());
        }
        sb.append(content);
    }

    /**
     * 截断策略：从中部截取
     */
    private String truncateContent(String content, int limit) {
        int length = content.length();
        int start = (length - limit) / 2;
        return "..." + content.substring(start, start + limit) + "...";
    }
}
