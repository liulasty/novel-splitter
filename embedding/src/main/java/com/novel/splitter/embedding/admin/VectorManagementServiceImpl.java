package com.novel.splitter.embedding.admin;

import com.novel.splitter.domain.model.dto.VectorSearchRequest;
import com.novel.splitter.domain.model.embedding.VectorRecord;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VectorManagementServiceImpl implements VectorManagementService {

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;

    @Override
    public Map<String, Object> getStats() {
        return Map.of(
                "count", vectorStore.count(),
                "type", vectorStore.getClass().getSimpleName()
        );
    }

    @Override
    public List<VectorRecord> search(VectorSearchRequest request) {
        float[] embedding = embeddingService.embedBatch(Collections.singletonList(request.getQuery())).get(0);
        return vectorStore.search(embedding, request.getTopK(), request.getFilter());
    }

    @Override
    public void delete(Map<String, Object> filter) {
        vectorStore.delete(filter);
    }

    @Override
    public void reset() {
        vectorStore.reset();
    }
}
