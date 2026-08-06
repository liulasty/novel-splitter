package com.novel.splitter.application.service.rag;

import com.novel.splitter.assembler.api.ContextAssembler;
import com.novel.splitter.assembler.config.AssemblerConfig;
import com.novel.splitter.llm.client.robust.RobustLlmClient;
import com.novel.splitter.retrieval.api.RagRetrievalService;
import com.novel.splitter.retrieval.config.RagProperties;
import com.novel.splitter.retrieval.dto.RagRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagOrchestrationServiceTest {

    @Test
    void buildAssemblerConfig_copiesPhase1ConfigFields() {
        AssemblerConfig defaults = new AssemblerConfig();
        defaults.setQualityScoreWeight(0.3);
        defaults.setExpandRadius(-1);      // 回滚开关：-1 关闭相邻扩展
        defaults.setExpandAcrossChapters(true);

        RagOrchestrationService svc = new RagOrchestrationService(
                Mockito.mock(RagRetrievalService.class),
                Mockito.mock(RobustLlmClient.class),
                Mockito.mock(ContextAssembler.class),
                Mockito.mock(RagProperties.class),
                defaults);

        AssemblerConfig built = svc.buildAssemblerConfig(new RagRequest());

        assertEquals(0.3, built.getQualityScoreWeight(), 1e-9);
        assertEquals(-1, built.getExpandRadius());
        assertTrue(built.isExpandAcrossChapters());
    }
}
