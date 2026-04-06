package com.novel.splitter.application.service.chroma;

import java.util.Map;

public interface ChromaAdminService {

    Map<String, Object> getStats();

    Map<String, String> reset();

    Map<String, String> delete(Map<String, Object> filter);

    Map<String, Object> healthcheck();

    Map<String, String> version();

    Map<String, Object> heartbeat();

    Object proxyGet(String path);

    Object proxyPost(String path, Object body);

    Object proxyPut(String path, Object body);

    Object proxyPatch(String path, Object body);

    Object proxyDelete(String path);
}
