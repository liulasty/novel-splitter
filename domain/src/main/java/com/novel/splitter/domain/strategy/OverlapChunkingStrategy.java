package com.novel.splitter.domain.strategy;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * 重叠切分策略 (Sliding Window)
 * 按照固定字数（chunkSize）切分，相邻子块保留重叠（overlap）字数，以提升向量召回精度。
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
            chunks.add(createOverlappedChunk(source, text, 0));
            return chunks;
        }

        int index = 0;
        int partIdx = 0;
        while (index < text.length()) {
            int maxEnd = Math.min(index + chunkSize, text.length());
            int end = findBestSplitPoint(text, index, maxEnd);

            if (end <= index) {
                end = maxEnd;
            }

            String chunkText = text.substring(index, end);
            chunks.add(createOverlappedChunk(source, chunkText, partIdx++));

            if (end == text.length()) {
                break;
            }
            index = Math.max(index + 1, end - overlap);
        }
        return chunks;
    }

    private int findBestSplitPoint(String text, int start, int maxEnd) {
        if (maxEnd >= text.length()) {
            return text.length();
        }
        
        // Try to find a good break point within the last 40% of the chunk
        int lookbackLimit = Math.max(start + chunkSize / 2, maxEnd - (chunkSize * 4 / 10));
        
        // 1. Look for double newline
        int doubleNewline = text.lastIndexOf("\n\n", maxEnd);
        if (doubleNewline >= lookbackLimit) return doubleNewline + 2;
        
        // 2. Look for single newline
        int singleNewline = text.lastIndexOf("\n", maxEnd);
        if (singleNewline >= lookbackLimit) return singleNewline + 1;
        
        // 3. Look for major punctuation marks
        for (int i = maxEnd - 1; i >= lookbackLimit; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '!' || c == '?') {
                // include the closing quote if it's there
                if (i + 1 < text.length() && (text.charAt(i + 1) == '”' || text.charAt(i + 1) == '"' || text.charAt(i + 1) == '’')) {
                    return i + 2;
                }
                return i + 1;
            }
        }
        
        // Fallback to maxEnd
        return maxEnd;
    }

    private Scene createOverlappedChunk(Scene source, String text, int partIndex) {
        Scene chunk = new Scene();
        chunk.setId(UUID.randomUUID().toString());
        chunk.setText(text);
        chunk.setWordCount(text.length());
        chunk.setChapterIndex(source.getChapterIndex());
        chunk.setChapterTitle(source.getChapterTitle());
        chunk.setStartParagraphIndex(source.getStartParagraphIndex());
        chunk.setEndParagraphIndex(source.getEndParagraphIndex());
        chunk.setCanSplit(false);
        if (partIndex == 0) {
            chunk.setPrefixContext(source.getPrefixContext());
        }

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
