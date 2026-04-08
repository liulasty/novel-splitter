package com.novel.splitter.embedding.tokenizer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分词器组件。
 * 负责将输入的纯文本字符串转换为模型可接收的词元 ID 序列及其对应的特征数组。
 */
@Component
@RequiredArgsConstructor
public class Tokenizer {

    /** 词汇表管理组件，提供词元与 ID 之间的映射服务 */
    private final Vocabulary vocabulary;
    
    /** 模型支持的最大词元序列长度 */
    private static final int MAX_LENGTH = 512;

    /**
     * 将输入文本进行分词处理，生成对应的特征数组对象。
     *
     * @param text 需要进行分词的原始文本
     * @return 分词后的特征封装对象 {@link TokenizedInput}，包含 Input IDs、Attention Mask 和 Token Type IDs
     */
    public TokenizedInput tokenize(String text) {
        // 简单的规范化处理：防止空指针异常
        if (text == null) text = "";
        
        // Simple normalization: just trim? 
        // BGE handles Chinese chars by putting spaces, but our vocab map likely has raw chars.
        // Let's iterate characters.
        // BGE 模型在处理中文字符时通常会加上空格，但这里的词表大概率包含原始中文字符，
        // 因此直接通过逐字符遍历进行分词。
        
        // 用于存放解析后的词元 ID 列表
        List<Long> ids = new ArrayList<>();
        // 在序列起始位置添加 [CLS] 标记
        ids.add(vocabulary.getClsId());
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String token = String.valueOf(c);
            
            // Try to find token in vocab
            // 尝试在词汇表中查找当前字符对应的词元 ID
            Long id = vocabulary.getId(token);
            if (id == null) {
                // Try lower case? BGE usually is uncased but config said lowercase: false
                // But let's try just in case for English
                // Actually, let's stick to simple lookup first.
                // If not found, use UNK
                // 如果在词表中未找到该字符，则使用 [UNK] (未知) 标记的 ID
                id = vocabulary.getUnkId();
            }
            
            ids.add(id);
            
            // Truncate if too long (reserve space for SEP)
            // 截断过长的文本，保留一个位置给末尾的 [SEP] 标记
            if (ids.size() >= MAX_LENGTH - 1) {
                break;
            }
        }
        
        // 在序列有效内容的末尾添加 [SEP] 标记
        ids.add(vocabulary.getSepId());
        
        // Padding
        // 执行填充操作，确保最终输出的序列长度符合 MAX_LENGTH 的要求
        int actualLength = ids.size();
        long[] inputIds = new long[MAX_LENGTH];
        long[] attentionMask = new long[MAX_LENGTH];
        long[] tokenTypeIds = new long[MAX_LENGTH]; // All zeros for sentence A (对于单句输入，全置为 0)
        
        for (int i = 0; i < MAX_LENGTH; i++) {
            if (i < actualLength) {
                // 有效文本部分，填充对应的词元 ID，并将 Attention Mask 置为 1
                inputIds[i] = ids.get(i);
                attentionMask[i] = 1;
            } else {
                // 填充部分，使用 [PAD] 标记，并将 Attention Mask 置为 0
                inputIds[i] = vocabulary.getPadId();
                attentionMask[i] = 0;
            }
            // 词元类型 ID 全局置为 0
            tokenTypeIds[i] = 0;
        }
        
        return new TokenizedInput(inputIds, attentionMask, tokenTypeIds);
    }
}