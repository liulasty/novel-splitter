package com.novel.splitter.retrieval.impl;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.retrieval.dto.RetrievalQuery;
import com.novel.splitter.domain.model.embedding.VectorRecord;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.retrieval.api.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于向量的检索服务实现
 * <p>
 * 负责将用户自然语言问题转换为向量，在向量数据库中进行相似度搜索，
 * 并通过本地仓储将检索到的向量记录还原（Hydrate）为完整的业务领域模型（Scene）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorRetrievalService implements RetrievalService {

    /** 与 {@link com.novel.splitter.embedding.store.ChromaVectorStore} 写入的 metadata 键一致 */
    private static final String META_NOVEL_ID = "novelId";
    // 元数据键名：小说版本
    private static final String META_VERSION = "version";
    // 元数据键名：父级场景ID（用于子块追溯）
    private static final String META_PARENT_SCENE_ID = "parent_scene_id";
    // 分隔符，用于构建唯一键
    private static final String KEY_SEPARATOR = "::";

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final SceneRepository sceneRepository;

    /**
     * 根据检索查询对象执行向量检索，并返回相关的场景列表
     *
     * @param query 结构化的检索查询请求，包含问题文本及过滤条件（如小说名、版本号等）
     * @return 检索到的、按相关度降序排列的 Scene（场景）列表
     * @throws IllegalArgumentException 当问题文本为空或 TopK 值小于1时抛出
     */
    @Override
    public List<Scene> retrieve(RetrievalQuery query) {
        // 参数基本校验
        if (query == null || query.getQuestion() == null || query.getQuestion().isBlank()) {
            throw new IllegalArgumentException("Question cannot be null or blank");
        }
        if (query.getTopK() < 1) {
            throw new IllegalArgumentException("TopK must be greater than or equal to 1");
        }

        log.info("Processing retrieval query: '{}' (topK={})", query.getQuestion(), query.getTopK());

        // 1. Embedding：调用嵌入服务，将用户的自然语言问题转化为稠密向量
        float[] queryVector = embeddingService.embedBatch(Collections.singletonList(query.getQuestion())).get(0);

        // 2. Vector Search：构建元数据过滤条件，在向量库中查找最相似的记录
        Map<String, Object> filter = new HashMap<>();
        if (query.getNovelId() != null && !query.getNovelId().isBlank()) {
            filter.put(META_NOVEL_ID, query.getNovelId());
        }
        if (query.getVersion() != null && !query.getVersion().isBlank()) {
            filter.put(META_VERSION, query.getVersion());
        }

        log.info("Executing vector search with filter: {}", filter);

        // 执行向量搜索
        List<VectorRecord> records = vectorStore.search(queryVector, query.getTopK(), filter);
        log.debug("Found {} vector matches", records.size());

        // 3. Hydrate (Vector -> Scene)：将向量搜索结果还原为包含完整文本的 Scene 实体
        // 按小说和版本对记录进行分组，以尽量减少磁盘或数据库 IO 操作
        Map<String, List<VectorRecord>> groupedRecords = new HashMap<>();
        // 记录处理的原始顺序，以保证最终返回结果的排序不受影响
        List<VectorRecord> processingOrder = new ArrayList<>();

        for (VectorRecord record : records) {
            Map<String, Object> meta = record.getMetadata();
            // 校验元数据完整性，缺少必要信息则跳过该记录
            if (meta == null || !meta.containsKey(META_NOVEL_ID) || !meta.containsKey(META_VERSION)) {
                log.warn("Vector record {} missing metadata (novelId/version), skipping hydration", record.getChunkId());
                continue;
            }
            String key = meta.get(META_NOVEL_ID) + KEY_SEPARATOR + meta.get(META_VERSION);
            groupedRecords.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
            processingOrder.add(record);
        }

        // 收集所有需要查询的目标 Scene ID，以便执行批量查询降低 IO 开销
        List<String> allTargetIds = processingOrder.stream()
                .map(this::resolveTargetId)
                .distinct()
                .collect(Collectors.toList());

        // 批量加载 Scene 实体数据
        Map<String, Scene> allScenesMap = new HashMap<>();
        if (!allTargetIds.isEmpty()) {
            List<Scene> allScenes = sceneRepository.findBySceneIds(allTargetIds);
            allScenesMap = allScenes.stream()
                    .collect(Collectors.toMap(Scene::getId, s -> s, (v1, v2) -> v1));
        }

        // Hydrate scenes and track failures：装配 Scene 并记录可能失败的分组
        Map<String, Scene> hydratedScenes = new HashMap<>();
        List<String> failedGroups = new ArrayList<>();

        // 遍历每个分组，装填相应的 Scene 数据
        for (Map.Entry<String, List<VectorRecord>> entry : groupedRecords.entrySet()) {
            String[] parts = entry.getKey().split(KEY_SEPARATOR, 2);
            String novelId = parts[0];
            String version = parts.length > 1 ? parts[1] : "";

            try {
                for (VectorRecord r : entry.getValue()) {
                    String targetId = resolveTargetId(r);
                    Scene s = allScenesMap.get(targetId);
                    
                    if (s != null) {
                        // 去重逻辑：如果多个子块（Chunk）指向同一个父级 Scene，
                        // 则保留相关度得分最高的分数作为该 Scene 的代表得分
                        if (hydratedScenes.containsKey(targetId)) {
                            Scene existing = hydratedScenes.get(targetId);
                            if (r.getScore() > existing.getScore()) {
                                existing.setScore(r.getScore());
                            }
                        } else {
                            s.setScore(r.getScore());
                            hydratedScenes.put(targetId, s);
                        }
                    } else {
                        log.warn("Scene {} not found for novelId={} version={}", targetId, novelId, version);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to load scenes for novelId={} version={}", novelId, version, e);
                failedGroups.add(novelId + "/" + version);
            }
        }

        if (!failedGroups.isEmpty()) {
            log.warn("Hydration failed for the following novel/version groups: {}", String.join(", ", failedGroups));
        }

        // 根据向量搜索得到的原始分数排序，重建返回结果列表，并剔除重复的 Scene
        List<Scene> resultList = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (VectorRecord r : processingOrder) {
            String targetId = resolveTargetId(r);
            if (!seenIds.contains(targetId)) {
                Scene s = hydratedScenes.get(targetId);
                if (s != null) {
                    resultList.add(s);
                    seenIds.add(targetId);
                }
            }
        }
        
        return resultList;
    }

    /**
     * 从 VectorRecord 元数据中解析 targetId。
     * <p>
     * 如果在切分阶段子块（Chunk）保留了其所属的父级场景 ID（parent_scene_id），
     * 则优先返回父级场景 ID，以保证提供给 LLM 更加完整的上下文；否则直接返回当前子块 ID。
     * </p>
     *
     * @param record 向量记录，包含由向量数据库返回的元数据信息
     * @return 目标 Scene 的唯一标识符（ID）
     */
    private String resolveTargetId(VectorRecord record) {
        if (record.getMetadata() != null && record.getMetadata().containsKey(META_PARENT_SCENE_ID)) {
            return (String) record.getMetadata().get(META_PARENT_SCENE_ID);
        }
        return record.getChunkId();
    }
}
