package com.novel.splitter.core;

import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.SemanticSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义段构建状态
 * <p>
 * 封装缓冲区的状态管理，使逻辑更清晰。
 * </p>
 */
public class SegmentState {
    private final List<RawParagraph> buffer;
    private String currentType = null;
    private int currentLength = 0;

    public SegmentState(int initialCapacity) {
        this.buffer = new ArrayList<>(initialCapacity);
    }

    public void add(RawParagraph p, String type, int length) {
        if (buffer.isEmpty()) {
            this.currentType = type;
        }
        buffer.add(p);
        currentLength += length;
    }

    public void clear() {
        buffer.clear();
        currentType = null;
        currentLength = 0;
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    public List<RawParagraph> getBuffer() {
        return buffer;
    }

    public String getCurrentType() {
        return currentType;
    }

    public int getCurrentLength() {
        return currentLength;
    }

    public SemanticSegment toSegment() {
        if (buffer.isEmpty()) {
            return null;
        }
        return SemanticSegment.builder()
                .paragraphs(new ArrayList<>(buffer))
                .type(currentType)
                .build();
    }
}
