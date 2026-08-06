package com.novel.splitter.application.service.enrich;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.application.model.dto.SceneExtractionDto;
import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.llm.client.robust.RobustLlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class SceneSemanticExtractorTest {

    private RobustLlmClient llmClient;
    private SceneSemanticExtractor extractor;

    @BeforeEach
    void setUp() {
        llmClient = Mockito.mock(RobustLlmClient.class);
        extractor = new SceneSemanticExtractor(llmClient, new ObjectMapper());
    }

    @Test
    void extract_parsesAnswerJsonArray() {
        String payload = "[{\"id\":\"s1\",\"characters\":[\"萧炎\"],\"location\":\"乌坦城\",\"time\":null,\"role\":\"narration\"}]";
        when(llmClient.chat(any(Prompt.class)))
                .thenReturn(Answer.builder().answer(payload).build());

        List<SceneExtractionDto> result = extractor.extract(List.of(
                Scene.builder().id("s1").text("正文").build()));

        assertEquals(1, result.size());
        assertEquals("s1", result.get(0).getId());
        assertEquals(List.of("萧炎"), result.get(0).getCharacters());
        assertEquals("乌坦城", result.get(0).getLocation());
        assertEquals("narration", result.get(0).getRole());
    }

    @Test
    void extract_returnsEmptyOnBlankAnswer() {
        when(llmClient.chat(any(Prompt.class))).thenReturn(Answer.builder().answer("  ").build());
        assertTrue(extractor.extract(List.of(Scene.builder().id("s1").text("t").build())).isEmpty());
    }

    @Test
    void extract_returnsEmptyOnParseFailure() {
        when(llmClient.chat(any(Prompt.class))).thenReturn(Answer.builder().answer("not json").build());
        assertTrue(extractor.extract(List.of(Scene.builder().id("s1").text("t").build())).isEmpty());
    }

    @Test
    void extract_emptyScenes_returnsEmpty() {
        assertTrue(extractor.extract(List.of()).isEmpty());
    }
}
