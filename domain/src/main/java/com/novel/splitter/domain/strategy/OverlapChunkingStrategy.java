package com.novel.splitter.domain.strategy;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * 重叠切分策略 (Sliding Window)
 * 按照固定字数（chunkSize）切分；相邻子块之间「重叠」部分写入下一块的 {@link Scene#getPrefixContext()}，
 * 不重复进入 {@link Scene#getText()}，便于向量检索时正文语义独立。
 */
public class OverlapChunkingStrategy implements ChunkingStrategy {

    private final int chunkSize;
    private final int overlap;

    public OverlapChunkingStrategy(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("Overlap must be non-negative and less than chunk size");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    public List<Scene> split(Scene source) {
        List<Scene> chunks = new ArrayList<>();
        String text = source.getText();

        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        if (text.length() <= chunkSize) {
            chunks.add(createOverlappedChunk(source, text, 0, normalizePrefix(source.getPrefixContext())));
            return chunks;
        }

        int windowStart = 0;
        int previousEnd = 0;
        int partIdx = 0;
        while (windowStart < text.length()) {
            int maxEnd = Math.min(windowStart + chunkSize, text.length());
            int end = findBestSplitPoint(text, windowStart, maxEnd);

            if (end <= windowStart) {
                end = maxEnd;
            }

            String chunkText;
            String prefixContext;
            if (partIdx == 0) {
                chunkText = text.substring(windowStart, end);
                prefixContext = normalizePrefix(source.getPrefixContext());
            } else {
                int prefixStart = Math.max(0, previousEnd - overlap);
                prefixContext = normalizePrefix(text.substring(prefixStart, previousEnd));
                chunkText = text.substring(previousEnd, end);
            }

            chunks.add(createOverlappedChunk(source, chunkText, partIdx++, prefixContext));

            if (end == text.length()) {
                break;
            }
            previousEnd = end;
            windowStart = Math.max(windowStart + 1, end - overlap);
        }
        return chunks;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return null;
        }
        return prefix;
    }

    private int findBestSplitPoint(String text, int start, int maxEnd) {
        if (maxEnd >= text.length()) {
            return text.length();
        }
        
        // 尝试在块末尾 40% 范围内寻找更合理的断点
        int lookbackLimit = Math.max(start + chunkSize / 2, maxEnd - (chunkSize * 4 / 10));
        
        // 1. 优先寻找连续空行
        int doubleNewline = text.lastIndexOf("\n\n", maxEnd);
        if (doubleNewline >= lookbackLimit) return doubleNewline + 2;
        
        // 2. 其次寻找单个换行
        int singleNewline = text.lastIndexOf("\n", maxEnd);
        if (singleNewline >= lookbackLimit) return singleNewline + 1;
        
        // 3. 最后寻找主要标点符号
        for (int i = maxEnd - 1; i >= lookbackLimit; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '!' || c == '?') {
                // 若存在结束引号则一并纳入
                if (i + 1 < text.length() && (text.charAt(i + 1) == '”' || text.charAt(i + 1) == '"' || text.charAt(i + 1) == '’')) {
                    return i + 2;
                }
                return i + 1;
            }
        }
        
        // 兜底：直接使用 maxEnd
        return maxEnd;
    }

    private Scene createOverlappedChunk(Scene source, String text, int partIndex, String prefixContext) {
        Scene chunk = new Scene();
        chunk.setId(UUID.randomUUID().toString());
        chunk.setText(text);
        chunk.setWordCount(text.length());
        chunk.setChapterIndex(source.getChapterIndex());
        chunk.setChapterTitle(source.getChapterTitle());
        chunk.setStartParagraphIndex(source.getStartParagraphIndex());
        chunk.setEndParagraphIndex(source.getEndParagraphIndex());
        chunk.setCanSplit(false);
        chunk.setPrefixContext(prefixContext);

        SceneMetadata meta = copyMetadataFromSource(source.getMetadata());
        meta.setSequenceNum(partIndex);
        chunk.setMetadata(meta);

        return chunk;
    }

    private static SceneMetadata copyMetadataFromSource(SceneMetadata parentMeta) {
        SceneMetadata childMeta = new SceneMetadata();
        if (parentMeta == null) {
            return childMeta;
        }
        childMeta.setNovel(parentMeta.getNovel());
        childMeta.setVersion(parentMeta.getVersion());
        childMeta.setChapterTitle(parentMeta.getChapterTitle());
        childMeta.setChapterIndex(parentMeta.getChapterIndex());
        childMeta.setStartParagraph(parentMeta.getStartParagraph());
        childMeta.setEndParagraph(parentMeta.getEndParagraph());
        childMeta.setDensityScore(parentMeta.getDensityScore());
        childMeta.setQualityScore(parentMeta.getQualityScore());
        childMeta.setRole(parentMeta.getRole());
        childMeta.setCharacters(parentMeta.getCharacters());
        childMeta.setLocation(parentMeta.getLocation());
        childMeta.setTime(parentMeta.getTime());
        if (parentMeta.getExtra() != null && !parentMeta.getExtra().isEmpty()) {
            childMeta.setExtra(new HashMap<>(parentMeta.getExtra()));
        }
        return childMeta;
    }
}
