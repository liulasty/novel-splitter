package com.novel.splitter.application.service.enrich;

import com.novel.splitter.domain.exception.BusinessErrorCode;
import com.novel.splitter.domain.exception.BusinessException;
import com.novel.splitter.domain.repository.SceneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 版本 enrich 一致性门控：仅 0%（从未分析）或 100%（全部分析）可向量化。
 * 中间状态（1%-99%）视为脏数据，一律拒绝。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnrichConsistencyService {

    private final SceneRepository sceneRepository;

    /** 完成度 0-100；无场景视为 0。 */
    public int progress(String novelId, String version) {
        long total = sceneRepository.countActiveByNovelIdAndVersion(novelId, version);
        if (total <= 0) {
            return 0;
        }
        long enriched = sceneRepository.countEnrichedByNovelIdAndVersion(novelId, version);
        return (int) Math.round(enriched * 100.0 / total);
    }

    /** 校验 0% 或 100%；中间状态抛 BusinessException(VERSION_ENRICH_INCONSISTENT, 含进度)。 */
    public void ensureEmbeddable(String novelId, String version) {
        int p = progress(novelId, version);
        if (p > 0 && p < 100) {
            throw new BusinessException(BusinessErrorCode.VERSION_ENRICH_INCONSISTENT,
                    "语义分析不一致（当前 " + p + "%），需达到 0% 或 100% 后才能向量化");
        }
    }
}
