package com.novel.splitter.embedding.tokenizer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 表示文本分词处理后的模型输入数据封装类。
 * 包含了能够直接输入给 ONNX 模型（如 BERT 结构模型）的各种特征数组。
 */
@Getter
@RequiredArgsConstructor
public class TokenizedInput {
    
    /** 
     * 词元的 ID 数组 (Input IDs)，对应文本中每个词在词汇表里的索引。
     */
    private final long[] inputIds;
    
    /** 
     * 注意力掩码数组 (Attention Mask)，用于区分真实词元和填充词元。
     * 值为 1 表示真实的输入词元，值为 0 表示填充(Padding)的词元。
     */
    private final long[] attentionMask;
    
    /** 
     * 词元类型 ID 数组 (Token Type IDs)，也称作 Segment IDs。
     * ONNX 运行时中的 BERT 类模型通常需要此数组来区分不同的句子（如句 A 和句 B）。
     */
    private final long[] tokenTypeIds; // ONNX Runtime often expects this too for BERT

    /**
     * 将当前的分词输入对象转换为易读的字符串表示形式。
     *
     * @return 包含 inputIds 和 attentionMask 数组内容的字符串
     */
    @Override
    public String toString() {
        return "TokenizedInput{" +
                "inputIds=" + Arrays.toString(inputIds) +
                ", attentionMask=" + Arrays.toString(attentionMask) +
                '}';
    }
}