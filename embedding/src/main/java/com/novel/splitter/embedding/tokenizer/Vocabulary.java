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
    
    @org.springframework.beans.factory.annotation.Value("${embedding.onnx.vocab-path:}")
    private String externalVocabPath;

    private static final String VOCAB_PATH = "embedding/vocab.txt"; // It's actually a JSON file

    public Vocabulary() {
    }

    @jakarta.annotation.PostConstruct
    private void loadVocabulary() {
        try {
            InputStream is = null;
            if (externalVocabPath != null && !externalVocabPath.isBlank()) {
                log.info("Using external vocabulary from: {}", externalVocabPath);
                java.io.File vocabFile = new java.io.File(externalVocabPath);
                if (vocabFile.exists()) {
                    is = new java.io.FileInputStream(vocabFile);
                } else {
                    log.error("External vocabulary file not found: {}", externalVocabPath);
                    throw new java.io.FileNotFoundException("External vocabulary file not found: " + externalVocabPath);
                }
            } else {
                log.info("Using bundled vocabulary from classpath");
                ClassPathResource resource = new ClassPathResource(VOCAB_PATH);
                if (resource.exists()) {
                    is = resource.getInputStream();
                } else {
                    log.error("Bundled vocabulary resource not found: {}", VOCAB_PATH);
                    throw new java.io.FileNotFoundException("Bundled vocabulary resource not found: " + VOCAB_PATH);
                }
            }

            try (InputStream inputStream = is) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(inputStream);
                // Handle both simple map format and HuggingFace tokenizer.json format
                JsonNode vocabNode = null;
                if (root.has("model") && root.get("model").has("vocab")) {
                    vocabNode = root.get("model").get("vocab");
                } else if (root.has("vocab")) {
                     vocabNode = root.get("vocab"); // Some simple formats
                } else {
                    // Maybe it's a flat map?
                    vocabNode = root;
                }

                if (vocabNode == null || !vocabNode.isObject()) {
                     throw new RuntimeException("Invalid vocabulary format");
                }
                
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
            log.error("Failed to load vocabulary", e);
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
