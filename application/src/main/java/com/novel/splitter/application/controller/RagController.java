package com.novel.splitter.application.controller;

import com.novel.splitter.application.service.rag.RagService;
import com.novel.splitter.domain.model.Answer;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @PostMapping
    public Answer ask(@RequestBody RagRequest request) {
        return ragService.ask(request.getQuestion(), request.getTopK() > 0 ? request.getTopK() : 3);
    }

    @Data
    public static class RagRequest {
        private String question;
        private int topK = 3;
    }
}
