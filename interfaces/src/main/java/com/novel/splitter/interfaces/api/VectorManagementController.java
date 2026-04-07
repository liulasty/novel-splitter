package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.model.dto.VectorSearchRequest;
import com.novel.splitter.application.service.vector.VectorManagementService;
import com.novel.splitter.domain.model.embedding.VectorRecord;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 通用向量数据库管理控制器
 * 提供通用向量数据库的搜索、删除、重置及统计功能
 */
@Tag(name = "通用向量数据库管理", description = "提供通用向量数据库的搜索、删除、重置及统计功能")
@RestController
@RequestMapping("/api/admin/vector")
@RequiredArgsConstructor
public class VectorManagementController {

    private final VectorManagementService vectorManagementService;

    /**
     * 获取向量数据库统计信息
     *
     * @return 包含向量总数和向量库类型的统计信息
     */
    @Operation(summary = "获取向量数据库统计信息", description = "获取当前向量数据库中的向量总数及数据库实现类型")
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return vectorManagementService.getStats();
    }

    /**
     * 执行向量相似度搜索
     *
     * @param request 向量搜索请求参数，包含查询文本、返回数量及过滤条件
     * @return 匹配的向量记录列表
     */
    @Operation(summary = "执行向量相似度搜索", description = "根据查询文本进行嵌入转换，并在向量数据库中搜索最相似的记录")
    @PostMapping("/search")
    public List<VectorRecord> search(@Valid @RequestBody VectorSearchRequest request) {
        return vectorManagementService.search(request);
    }

    /**
     * 根据条件删除向量数据
     *
     * @param filter 删除过滤条件
     * @return 响应实体
     */
    @Operation(summary = "删除向量数据", description = "根据指定的过滤条件删除向量数据库中的记录")
    @DeleteMapping
    public void delete(@RequestBody Map<String, Object> filter) {
        vectorManagementService.delete(filter);
    }
    
    /**
     * 重置向量数据库
     *
     * @return 响应实体
     */
    @Operation(summary = "重置向量数据库", description = "清空向量数据库中的所有数据")
    @PostMapping("/reset")
    public void reset() {
        vectorManagementService.reset();
    }
}
