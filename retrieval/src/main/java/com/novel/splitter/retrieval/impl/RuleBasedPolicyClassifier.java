package com.novel.splitter.retrieval.impl;

import com.novel.splitter.domain.model.AnswerType;
import com.novel.splitter.retrieval.api.AnswerPolicyClassifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class RuleBasedPolicyClassifier implements AnswerPolicyClassifier {

    // Keywords that indicate the question is out of scope for a novel RAG system
    private static final Set<String> UNSUPPORTED_KEYWORDS = Set.of(
        "写代码", "编程", "java", "python", "c++", 
        "天气", "股票", "汇率", 
        "现实", "今天", "新闻", 
        "哈利波特", "三体", "金庸", // Cross-novel comparisons might be unsupported initially
        "翻译", "英语", "总结全文"
    );

    private static final List<String> CHARACTER_KEYWORDS = List.of(
        "谁", "关系", "性格", "外貌", "长相", "身份", "父亲", "母亲", "师傅", "徒弟", "主角", "配角", "妻子", "丈夫", "兄弟", "姐妹"
    );

    private static final List<String> TIMELINE_KEYWORDS = List.of(
        "什么时候", "时间", "多久", "哪一年", "何时", "几点", "年代", "岁月", "之前", "之后", "顺序", "先", "后"
    );

    private static final List<String> LOCATION_KEYWORDS = List.of(
        "在哪里", "在哪", "地点", "位置", "去哪", "位于", "何处", "城市", "地方", "方位"
    );

    @Override
    public AnswerType classify(String question) {
        if (question == null || question.isBlank()) {
            return AnswerType.UNSUPPORTED;
        }
        
        String q = question.toLowerCase().trim();

        // 1. UNSUPPORTED Check (Highest Priority)
        for (String keyword : UNSUPPORTED_KEYWORDS) {
            if (q.contains(keyword)) {
                return AnswerType.UNSUPPORTED;
            }
        }
        
        // 2. CHARACTER Check
        for (String keyword : CHARACTER_KEYWORDS) {
            if (q.contains(keyword)) {
                return AnswerType.CHARACTER;
            }
        }

        // 3. TIMELINE Check
        for (String keyword : TIMELINE_KEYWORDS) {
            if (q.contains(keyword)) {
                return AnswerType.TIMELINE;
            }
        }

        // 4. LOCATION Check
        for (String keyword : LOCATION_KEYWORDS) {
            if (q.contains(keyword)) {
                return AnswerType.LOCATION;
            }
        }

        // 5. Default to FACT
        return AnswerType.FACT;
    }
}
