package com.novel.splitter.retrieval.impl;

import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.retrieval.dto.RetrievalQuery;
import com.novel.splitter.domain.model.embedding.VectorRecord;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.domain.model.SceneSplitProfile;
import com.novel.splitter.domain.repository.NovelRepository;
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
    private static final String META_VERSION = "version";
    private static final String META_CHUNK_SIZE = "chunkSize";
    private static final String META_CHUNK_OVERLAP = "chunkOverlap";
    private static final String KEY_SEPARATOR = "::";

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final SceneRepository sceneRepository;
    private final NovelRepository novelRepository;

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

        log.info("处理检索查询: '{}' (topK={})", query.getQuestion(), query.getTopK());

        final String novelId = query.getNovelId();
        final String version = resolveVersion(novelId, query.getVersion());

        // 版本化检索：定位向量集合
        String collectionName = null;
        if (novelId != null && !novelId.isBlank() && version != null && !version.isBlank()) {
            collectionName = VectorStore.collectionNameFor(novelId, version);
            if (!vectorStore.collectionExists(collectionName)) {
                log.warn("向量集合 {} 不存在（novelId={} version={}），返回空结果",
                        collectionName, novelId, version);
                return Collections.emptyList();
            }
        }

        // 1. Embedding：调用嵌入服务，将用户的自然语言问题转化为稠密向量
        float[] queryVector = embeddingService.embedBatch(Collections.singletonList(query.getQuestion())).get(0);

        // 2. Vector Search：构建元数据过滤条件，在向量库中查找最相似的记录
        Map<String, Object> filter = new HashMap<>();
        if (novelId != null && !novelId.isBlank()) {
            filter.put(META_NOVEL_ID, novelId);
        }
        if (version != null && !version.isBlank()) {
            filter.put(META_VERSION, version);
        }

        Integer chunkSize = query.getChunkSize();
        Integer chunkOverlap = query.getChunkOverlap();
        if (novelId != null && !novelId.isBlank() && version != null && !version.isBlank()
                && (chunkSize == null || chunkOverlap == null)) {
            java.util.List<SceneSplitProfile> sameVersion = sceneRepository.listSplitProfilesByNovelId(novelId).stream()
                    .filter(p -> version.equals(p.version()))
                    .toList();
            java.util.List<SceneSplitProfile> withDims = sameVersion.stream()
                    .filter(p -> p.chunkSize() != null && p.chunkOverlap() != null)
                    .toList();
            if (withDims.size() > 1) {
                throw new IllegalArgumentException(
                        "Multiple split profiles for novelId=" + novelId + " version=" + version
                                + "; specify chunkSize and chunkOverlap.");
            }
            if (withDims.size() == 1) {
                chunkSize = withDims.get(0).chunkSize();
                chunkOverlap = withDims.get(0).chunkOverlap();
            }
        }
        if (chunkSize != null && chunkOverlap != null) {
            filter.put(META_CHUNK_SIZE, chunkSize);
            filter.put(META_CHUNK_OVERLAP, chunkOverlap);
        }

        log.info("执行向量搜索，过滤条件: {} 集合: {}", filter,
                collectionName != null ? collectionName : "(default)");

        // 执行向量搜索（降级：异常时返回空）
        List<VectorRecord> records;
        try {
            if (collectionName != null) {
                records = vectorStore.search(queryVector, query.getTopK(), filter, collectionName);
            } else {
                records = vectorStore.search(queryVector, query.getTopK(), filter);
            }
        } catch (Exception e) {
            log.warn("向量搜索失败，集合 {}: {}", collectionName, e.toString());
            return Collections.emptyList();
        }
        log.debug("找到 {} 条向量匹配结果", records.size());

        // 3. Hydrate (Vector -> Scene)：将向量搜索结果还原为包含完整文本的 Scene 实体
        // 按小说和版本对记录进行分组，以尽量减少磁盘或数据库 IO 操作
        Map<String, List<VectorRecord>> groupedRecords = new HashMap<>();
        // 记录处理的原始顺序，以保证最终返回结果的排序不受影响
        List<VectorRecord> processingOrder = new ArrayList<>();

        for (VectorRecord record : records) {
            Map<String, Object> meta = record.getMetadata();
            // 校验元数据完整性，缺少必要信息则跳过该记录
            if (meta == null || !meta.containsKey(META_NOVEL_ID) || !meta.containsKey(META_VERSION)) {
                log.warn("向量记录 {} 缺少元数据（novelId/version），跳过 Hydrate", record.getChunkId());
                continue;
            }
            String key = meta.get(META_NOVEL_ID) + KEY_SEPARATOR + meta.get(META_VERSION);
            groupedRecords.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
            processingOrder.add(record);
        }

        // 收集所有需要查询的目标 Scene ID，以便执行批量查询降低 IO 开销
        List<String> allTargetIds = processingOrder.stream()
                .map(VectorRecord::getChunkId)
                .distinct()
                .collect(Collectors.toList());

        // 批量加载 Scene 实体数据
        Map<String, Scene> allScenesMap = new HashMap<>();
        if (!allTargetIds.isEmpty()) {
            List<Scene> allScenes = sceneRepository.findBySceneIds(allTargetIds);
            allScenesMap = allScenes.stream()
                    .collect(Collectors.toMap(Scene::getId, s -> s, (v1, v2) -> v1));
        }

        // 装配 Scene 并跟踪失败：记录可能失败的分组
        Map<String, Scene> hydratedScenes = new HashMap<>();
        List<String> failedGroups = new ArrayList<>();

        // 遍历每个分组，装填相应的 Scene 数据
        for (Map.Entry<String, List<VectorRecord>> entry : groupedRecords.entrySet()) {
            String[] parts = entry.getKey().split(KEY_SEPARATOR, 2);
            String groupNovelId = parts[0];
            String groupVersion = parts.length > 1 ? parts[1] : "";

            try {
                for (VectorRecord r : entry.getValue()) {
                    String targetId = r.getChunkId();
                    Scene s = allScenesMap.get(targetId);
                    
                    if (s != null) {
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
                        log.warn("未找到 Scene {}（novelId={} version={}）", targetId, groupNovelId, groupVersion);
                    }
                }
            } catch (Exception e) {
                log.error("加载 Scene 失败（novelId={} version={}）", groupNovelId, groupVersion, e);
                failedGroups.add(groupNovelId + "/" + groupVersion);
            }
        }

        if (!failedGroups.isEmpty()) {
            log.warn("以下 novel/version 分组 Hydrate 失败: {}", String.join(", ", failedGroups));
        }

        // 根据向量搜索得到的原始分数排序，重建返回结果列表，并剔除重复的 Scene
        List<Scene> resultList = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (VectorRecord r : processingOrder) {
            String targetId = r.getChunkId();
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
     * 解析检索请求中的版本：无显式指定则从 Novel.activeVersionTag 获取。
     */
    private String resolveVersion(String novelId, String explicitVersion) {
        if (explicitVersion != null && !explicitVersion.isBlank()) {
            return explicitVersion;
        }
        if (novelId == null || novelId.isBlank()) {
            return null;
        }
        try {
            Novel novel = novelRepository.findById(novelId).orElse(null);
            if (novel != null && novel.getActiveVersionTag() != null && !novel.getActiveVersionTag().isBlank()) {
                log.debug("已解析活动版本 {}，小说: {}", novel.getActiveVersionTag(), novelId);
                return novel.getActiveVersionTag();
            }
        } catch (Exception e) {
            log.warn("解析小说 {} 的活动版本失败: {}", novelId, e.toString());
        }
        return null;
    }
}
