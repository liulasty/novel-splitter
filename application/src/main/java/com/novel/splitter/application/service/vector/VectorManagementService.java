package com.novel.splitter.application.service.vector;

import com.novel.splitter.application.model.dto.VectorSearchRequest;
import com.novel.splitter.domain.model.embedding.VectorRecord;

import java.util.List;
import java.util.Map;

public interface VectorManagementService {

    Map<String, Object> getStats();

    List<VectorRecord> search(VectorSearchRequest request);

    void delete(Map<String, Object> filter);

    void reset();
}
