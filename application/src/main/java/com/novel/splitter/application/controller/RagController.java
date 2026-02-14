package com.novel.splitter.application.controller;

import com.novel.splitter.application.service.rag.RagService;
import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.dto.RagDebugResponse;
import com.novel.splitter.domain.model.dto.RagRequest;
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
        return ragService.ask(request.getQuestion(), request.getTopK() > 0 ? request.getTopK() : 3, request.getNovel(), request.getVersion());
    }

    @PostMapping("/debug")
    public RagDebugResponse debug(@RequestBody RagRequest request) {
        return ragService.preview(request.getQuestion(), request.getTopK() > 0 ? request.getTopK() : 3, request.getNovel(), request.getVersion());
    }
}
