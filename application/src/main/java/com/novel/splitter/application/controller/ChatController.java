package com.novel.splitter.application.controller;

import com.novel.splitter.application.dto.ChatRequest;
import com.novel.splitter.application.service.rag.RagService;
import com.novel.splitter.domain.model.Answer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final RagService ragService;

    @PostMapping
    public ResponseEntity<Answer> chat(@RequestBody ChatRequest request) {
        log.info("Received chat request: {}", request);
        try {
            Answer answer = ragService.ask(request.getQuestion(), request.getTopK(), request.getNovel(), request.getVersion());
            return ResponseEntity.ok(answer);
        } catch (Exception e) {
            log.error("Error processing chat request", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
