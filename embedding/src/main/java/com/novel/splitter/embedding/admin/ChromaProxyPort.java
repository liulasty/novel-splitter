package com.novel.splitter.embedding.admin;

import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Chroma HTTP 代理端口
 * 用于底层模块（如 embedding）需要通过 HTTP 访问 Chroma 时使用，由 application 层提供适配器实现
 */
public interface ChromaProxyPort {
    Map<String, Object> getMap(String path);
    String getString(String path);
    ResponseEntity<?> get(String path);
    ResponseEntity<?> post(String path, Object body);
    ResponseEntity<?> put(String path, Object body);
    ResponseEntity<?> patch(String path, Object body);
    ResponseEntity<?> delete(String path);
}
