package com.novel.splitter.application.service.chroma;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@Slf4j
public class ChromaApiClient {

    private final RestClient restClient = RestClient.builder().build();

    @Value("${chroma.url:http://localhost:8081}")
    private String chromaUrl;

    public Map<String, Object> getMap(String path) {
        return restClient.get()
                .uri(chromaUrl + path)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
    }

    public String getString(String path) {
        return restClient.get()
                .uri(chromaUrl + path)
                .retrieve()
                .body(String.class);
    }

    public ResponseEntity<?> get(String path) {
        return exchange(HttpMethod.GET, path, null);
    }

    public ResponseEntity<?> post(String path, Object body) {
        return exchange(HttpMethod.POST, path, body);
    }

    public ResponseEntity<?> put(String path, Object body) {
        return exchange(HttpMethod.PUT, path, body);
    }

    public ResponseEntity<?> patch(String path, Object body) {
        return exchange(HttpMethod.PATCH, path, body);
    }

    public ResponseEntity<?> delete(String path) {
        return exchange(HttpMethod.DELETE, path, null);
    }

    private ResponseEntity<?> exchange(HttpMethod method, String path, Object body) {
        try {
            ResponseEntity<String> response = switch (method) {
                case GET -> restClient.get()
                        .uri(chromaUrl + path)
                        .retrieve()
                        .toEntity(String.class);
                case POST -> restClient.post()
                        .uri(chromaUrl + path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toEntity(String.class);
                case PUT -> restClient.put()
                        .uri(chromaUrl + path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toEntity(String.class);
                case PATCH -> restClient.patch()
                        .uri(chromaUrl + path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toEntity(String.class);
                case DELETE -> restClient.delete()
                        .uri(chromaUrl + path)
                        .retrieve()
                        .toEntity(String.class);
            };
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("{} {} 调用失败", method.name(), path, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private enum HttpMethod {
        GET,
        POST,
        PUT,
        PATCH,
        DELETE
    }
}
