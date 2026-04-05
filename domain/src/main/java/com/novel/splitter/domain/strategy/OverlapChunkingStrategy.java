package com.novel.splitter.domain.strategy;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;

import java.util.ArrayList;
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
    public List<Scene> split(Scene parent) {
        List<Scene> children = new ArrayList<>();
        String text = parent.getText();

        if (text == null || text.trim().isEmpty()) {
            return children;
        }

        if (text.length() <= chunkSize) {
            children.add(createChildScene(parent, text, 0));
            return children;
        }

        int index = 0;
        int childIdx = 0;
        while (index < text.length()) {
            int end = Math.min(index + chunkSize, text.length());
            String chunkText = text.substring(index, end);
            children.add(createChildScene(parent, chunkText, childIdx++));

            if (end == text.length()) {
                break;
            }
            index = end - overlap;
        }
        return children;
    }

    private Scene createChildScene(Scene parent, String text, int childIndex) {
        Scene child = new Scene();
        child.setId(UUID.randomUUID().toString());
        child.setText(text);
        child.setWordCount(text.length());
        child.setChapterIndex(parent.getChapterIndex());
        child.setChapterTitle(parent.getChapterTitle());
        child.setStartParagraphIndex(parent.getStartParagraphIndex());
        child.setEndParagraphIndex(parent.getEndParagraphIndex());
        child.setCanSplit(false);

        SceneMetadata parentMeta = parent.getMetadata();
        SceneMetadata childMeta = new SceneMetadata();
        if (parentMeta != null) {
            childMeta.setNovel(parentMeta.getNovel());
            childMeta.setVersion(parentMeta.getVersion());
        }
        childMeta.setParentSceneId(parent.getId());
        childMeta.setChunkType("child_chunk");
        child.setMetadata(childMeta);

        return child;
    }
}
