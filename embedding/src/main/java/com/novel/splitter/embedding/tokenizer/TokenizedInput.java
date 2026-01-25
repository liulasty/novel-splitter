package com.novel.splitter.embedding.tokenizer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public class TokenizedInput {
    private final long[] inputIds;
    private final long[] attentionMask;
    private final long[] tokenTypeIds; // ONNX Runtime often expects this too for BERT

    @Override
    public String toString() {
        return "TokenizedInput{" +
                "inputIds=" + Arrays.toString(inputIds) +
                ", attentionMask=" + Arrays.toString(attentionMask) +
                '}';
    }
}
