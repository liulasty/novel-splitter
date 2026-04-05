package com.novel.splitter.retrieval.impl;

import com.novel.splitter.domain.model.AnswerType;
import com.novel.splitter.retrieval.api.AnswerPolicyClassifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class RuleBasedPolicyClassifier implements AnswerPolicyClassifier {

    private static final Set<String> UNSUPPORTED_KEYWORDS = Set.of(
        "写代码", "编程", "java", "python", "c++",
        "天气", "股票", "汇率", "新闻",
        "现实", "今天", "翻译", "英语", "总结全文",
        "哈利波特", "三体", "金庸"
    );

    private static final List<String> CHARACTER_KEYWORDS = List.of(
        "谁", "关系", "性格", "外貌", "长相", "身份",
        "父亲", "母亲", "师傅", "徒弟", "主角", "配角",
        "妻子", "丈夫", "兄弟", "姐妹"
    );

    private static final List<String> TIMELINE_KEYWORDS = List.of(
        "什么时候", "时间", "多久", "哪一年", "何时",
        "几点", "年代", "岁月", "之前", "之后", "顺序", "先", "后"
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

        // 最高优先级：域外问题直接拦截
        if (UNSUPPORTED_KEYWORDS.stream().anyMatch(q::contains)) {
            return AnswerType.UNSUPPORTED;
        }

        // 意图匹配
        if (CHARACTER_KEYWORDS.stream().anyMatch(q::contains)) {
            return AnswerType.CHARACTER;
        }
        if (TIMELINE_KEYWORDS.stream().anyMatch(q::contains)) {
            return AnswerType.TIMELINE;
        }
        if (LOCATION_KEYWORDS.stream().anyMatch(q::contains)) {
            return AnswerType.LOCATION;
        }

        // 默认：事实类问题
        return AnswerType.FACT;
    }
}
