package com.novel.splitter.application.service.chroma;

import java.util.Map;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public interface ChromaAdminService {

    StreamingResponseBody exportData(String novelName, String version, Integer chunkSize, Integer chunkOverlap);

    Map<String, Object> getStats();

    Map<String, String> reset();

    Map<String, String> delete(Map<String, Object> filter);

    Map<String, Object> healthcheck();

    Map<String, String> version();

    Map<String, Object> heartbeat();

    Map<String, String> rebuildCollection();

    com.novel.splitter.application.model.dto.ChromaVersionDiagnosticDto getVersionDiagnostics(
            String novel, String version, Integer chunkSize, Integer chunkOverlap);

    Object proxyGet(String path);

    Object proxyPost(String path, Object body);

    Object proxyPut(String path, Object body);

    Object proxyPatch(String path, Object body);

    Object proxyDelete(String path);
}
