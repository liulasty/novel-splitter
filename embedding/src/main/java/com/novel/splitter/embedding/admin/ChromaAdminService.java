package com.novel.splitter.embedding.admin;

import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface ChromaAdminService {

    ResponseEntity<Map<String, Object>> getStats();

    ResponseEntity<Map<String, String>> reset();

    ResponseEntity<Map<String, String>> delete(Map<String, Object> filter);

    ResponseEntity<Map<String, Object>> healthcheck();

    ResponseEntity<Map<String, String>> version();

    ResponseEntity<Map<String, Object>> heartbeat();
}
