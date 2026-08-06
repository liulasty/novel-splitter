package com.novel.splitter.application.service.enrich;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.application.model.dto.SceneExtractionDto;
import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.llm.client.robust.RobustLlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 场景语义抽取：将一批场景（通常同一章）发送给 LLM，
 * 返回每个场景的 characters/location/time/role。
 * 复用 RobustLlmClient 的 Answer 契约：抽取结果以 JSON 数组字符串嵌在 answer 字段。
 * 解析失败返回空列表（不抛异常），由调用方按章降级。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SceneSemanticExtractor {

    private static final String SYSTEM_INSTRUCTION = """
            你是小说场景语义标注器。对每个上下文块（一块一个场景）抽取结构化语义：
            - characters: 该场景出场的人物名列表（JSON 数组），无明显人物则为空数组 []
            - location: 故事发生地点；无则为 null
            - time: 故事发生时间；无则为 null
            - role: 场景功能，取值必须是 dialogue（对话）/ narration（叙事）/ action（动作）/ transition（过渡）之一
            严格按每个块的 Chunk ID 对应输出。

            输出 Answer JSON（外层固定为 answer/citations/confidence 三个字段）：
            {
              "answer": "<JSON 数组字符串，每个元素形如 {\"id\":\"<Chunk ID>\",\"characters\":[\"角色1\"],\"location\":\"地点或null\",\"time\":\"时间或null\",\"role\":\"场景功能\"}，数组内双引号需转义>",
              "citations": [],
              "confidence": 1.0
            }
            """;

    private final RobustLlmClient llmClient;
    private final ObjectMapper objectMapper;

    public List<SceneExtractionDto> extract(List<Scene> scenes) {
        if (scenes == null || scenes.isEmpty()) {
            return List.of();
        }
        List<ContextBlock> blocks = new ArrayList<>();
        for (Scene scene : scenes) {
            blocks.add(ContextBlock.builder()
                    .chunkId(scene.getId())
                    .content(scene.getText())
                    .build());
        }
        Prompt prompt = Prompt.builder()
                .systemInstruction(SYSTEM_INSTRUCTION)
                .contextBlocks(blocks)
                .userQuestion("请对上述每个上下文块执行语义抽取，严格按 Answer JSON 格式输出。")
                .build();

        Answer answer;
        try {
            answer = llmClient.chat(prompt);
        } catch (Exception e) {
            log.warn("抽取 LLM 调用失败（{} 个场景），降级为空: {}", scenes.size(), e.toString());
            return List.of();
        }
        String payload = answer != null ? answer.getAnswer() : null;
        if (payload == null || payload.isBlank()) {
            log.warn("抽取返回空 answer（{} 个场景），降级为空", scenes.size());
            return List.of();
        }
        try {
            SceneExtractionDto[] parsed = objectMapper.readValue(payload, SceneExtractionDto[].class);
            List<SceneExtractionDto> result = new ArrayList<>();
            if (parsed != null) {
                for (SceneExtractionDto d : parsed) {
                    if (d != null && d.getId() != null) {
                        result.add(d);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("抽取 JSON 解析失败，降级为空: {}", e.toString());
            return List.of();
        }
    }
}
