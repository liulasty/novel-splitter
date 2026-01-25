package com.novel.splitter.embedding.tokenizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Slf4j
@Component
public class Vocabulary {

    private final Map<String, Long> tokenToId = new HashMap<>();
    private final Map<Long, String> idToToken = new HashMap<>();
    
    private static final String VOCAB_PATH = "embedding/vocab.txt"; // It's actually a JSON file

    public Vocabulary() {
        loadVocabulary();
    }

    private void loadVocabulary() {
        try {
            ClassPathResource resource = new ClassPathResource(VOCAB_PATH);
            try (InputStream is = resource.getInputStream()) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(is);
                JsonNode vocabNode = root.get("model").get("vocab");
                
                Iterator<Map.Entry<String, JsonNode>> fields = vocabNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    String token = field.getKey();
                    long id = field.getValue().asLong();
                    tokenToId.put(token, id);
                    idToToken.put(id, token);
                }
                log.info("Loaded vocabulary with {} tokens", tokenToId.size());
            }
        } catch (Exception e) {
            log.error("Failed to load vocabulary from {}", VOCAB_PATH, e);
            throw new RuntimeException("Failed to load vocabulary", e);
        }
    }

    public Long getId(String token) {
        return tokenToId.get(token);
    }

    public String getToken(Long id) {
        return idToToken.get(id);
    }
    
    public long getUnkId() {
        return tokenToId.getOrDefault("[UNK]", 100L);
    }

    public long getClsId() {
        return tokenToId.getOrDefault("[CLS]", 101L);
    }

    public long getSepId() {
        return tokenToId.getOrDefault("[SEP]", 102L);
    }
    
    public long getPadId() {
        return tokenToId.getOrDefault("[PAD]", 0L);
    }
}
