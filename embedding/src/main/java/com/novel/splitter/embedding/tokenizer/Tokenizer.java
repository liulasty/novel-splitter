package com.novel.splitter.embedding.tokenizer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class Tokenizer {

    private final Vocabulary vocabulary;
    private static final int MAX_LENGTH = 512;

    public TokenizedInput tokenize(String text) {
        if (text == null) text = "";
        
        // Simple normalization: just trim? 
        // BGE handles Chinese chars by putting spaces, but our vocab map likely has raw chars.
        // Let's iterate characters.
        
        List<Long> ids = new ArrayList<>();
        ids.add(vocabulary.getClsId());
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String token = String.valueOf(c);
            
            // Try to find token in vocab
            Long id = vocabulary.getId(token);
            if (id == null) {
                // Try lower case? BGE usually is uncased but config said lowercase: false
                // But let's try just in case for English
                // Actually, let's stick to simple lookup first.
                // If not found, use UNK
                id = vocabulary.getUnkId();
            }
            
            ids.add(id);
            
            // Truncate if too long (reserve space for SEP)
            if (ids.size() >= MAX_LENGTH - 1) {
                break;
            }
        }
        
        ids.add(vocabulary.getSepId());
        
        // Padding
        int actualLength = ids.size();
        long[] inputIds = new long[MAX_LENGTH];
        long[] attentionMask = new long[MAX_LENGTH];
        long[] tokenTypeIds = new long[MAX_LENGTH]; // All zeros for sentence A
        
        for (int i = 0; i < MAX_LENGTH; i++) {
            if (i < actualLength) {
                inputIds[i] = ids.get(i);
                attentionMask[i] = 1;
            } else {
                inputIds[i] = vocabulary.getPadId();
                attentionMask[i] = 0;
            }
            tokenTypeIds[i] = 0;
        }
        
        return new TokenizedInput(inputIds, attentionMask, tokenTypeIds);
    }
}
