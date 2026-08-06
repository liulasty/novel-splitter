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

/**
 * 词汇表管理组件。
 * 负责加载分词器所需的词表文件（JSON 格式），并提供词元(Token)与对应 ID 之间的双向映射。
 */
@Slf4j
@Component
public class Vocabulary {

    /** 词元到 ID 的映射字典 */
    private final Map<String, Long> tokenToId = new HashMap<>();
    /** ID 到词元的映射字典 */
    private final Map<Long, String> idToToken = new HashMap<>();
    
    /** 外部词表文件的路径配置，如果未配置则使用内置词表 */
    @org.springframework.beans.factory.annotation.Value("${embedding.onnx.vocab-path:}")
    private String externalVocabPath;

    /** 内置词表资源路径（实际上是一个 JSON 格式文件） */
    private static final String VOCAB_PATH = "embedding/vocab.txt"; // 该文件实际上是一个 JSON 文件

    /**
     * 默认构造函数
     */
    public Vocabulary() {
    }

    /**
     * 初始化方法，在 Bean 创建后自动调用。
     * 负责从外部路径或类路径中加载词汇表配置，并解析其中的映射关系。
     */
    @jakarta.annotation.PostConstruct
    private void loadVocabulary() {
        try {
            InputStream is = null;
            // 判断是否配置了外部词表路径
            if (externalVocabPath != null && !externalVocabPath.isBlank()) {
                log.info("使用外部词表：{}", externalVocabPath);
                java.io.File vocabFile = new java.io.File(externalVocabPath);
                if (vocabFile.exists()) {
                    is = new java.io.FileInputStream(vocabFile);
                } else {
                    log.error("未找到外部词表文件：{}", externalVocabPath);
                    throw new java.io.FileNotFoundException("External vocabulary file not found: " + externalVocabPath);
                }
            } else {
                // 使用类路径下的内置词表
                log.info("使用类路径下的内置词表");
                ClassPathResource resource = new ClassPathResource(VOCAB_PATH);
                if (resource.exists()) {
                    is = resource.getInputStream();
                } else {
                    log.error("未找到内置词表资源：{}", VOCAB_PATH);
                    throw new java.io.FileNotFoundException("Bundled vocabulary resource not found: " + VOCAB_PATH);
                }
            }

            try (InputStream inputStream = is) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(inputStream);
                
                // 兼容解析简单的 Map 格式以及 HuggingFace tokenizer.json 格式的词表
                JsonNode vocabNode = null;
                if (root.has("model") && root.get("model").has("vocab")) {
                    vocabNode = root.get("model").get("vocab");
                } else if (root.has("vocab")) {
                     vocabNode = root.get("vocab"); // 一些简单的格式
                } else {
                    // 如果都没有，可能是一个扁平的字典格式
                    vocabNode = root;
                }

                if (vocabNode == null || !vocabNode.isObject()) {
                     throw new RuntimeException("Invalid vocabulary format");
                }
                
                // 遍历 JSON 节点并将其存入双向映射字典
                Iterator<Map.Entry<String, JsonNode>> fields = vocabNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    String token = field.getKey();
                    long id = field.getValue().asLong();
                    tokenToId.put(token, id);
                    idToToken.put(id, token);
                }
                log.info("已加载词表，共 {} 个词元", tokenToId.size());
            }
        } catch (Exception e) {
            log.error("词表加载失败", e);
            throw new RuntimeException("Failed to load vocabulary", e);
        }
    }

    /**
     * 根据词元(Token)获取对应的 ID。
     *
     * @param token 需要查询的词元字符串
     * @return 对应的 ID，如果词表中不存在该词元则返回 null
     */
    public Long getId(String token) {
        return tokenToId.get(token);
    }

    /**
     * 根据 ID 获取对应的词元(Token)。
     *
     * @param id 需要查询的词元 ID
     * @return 对应的词元字符串，如果映射中不存在该 ID 则返回 null
     */
    public String getToken(Long id) {
        return idToToken.get(id);
    }
    
    /**
     * 获取未知词元 [UNK] 的 ID。
     *
     * @return [UNK] 的 ID，默认返回 100L
     */
    public long getUnkId() {
        return tokenToId.getOrDefault("[UNK]", 100L);
    }

    /**
     * 获取起始词元 [CLS] 的 ID。
     *
     * @return [CLS] 的 ID，默认返回 101L
     */
    public long getClsId() {
        return tokenToId.getOrDefault("[CLS]", 101L);
    }

    /**
     * 获取分隔词元 [SEP] 的 ID。
     *
     * @return [SEP] 的 ID，默认返回 102L
     */
    public long getSepId() {
        return tokenToId.getOrDefault("[SEP]", 102L);
    }
    
    /**
     * 获取填充词元 [PAD] 的 ID。
     *
     * @return [PAD] 的 ID，默认返回 0L
     */
    public long getPadId() {
        return tokenToId.getOrDefault("[PAD]", 0L);
    }
}