package com.novel.splitter.retrieval.impl;

import com.novel.splitter.domain.model.AnswerType;
import com.novel.splitter.retrieval.api.AnswerPolicyClassifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 基于规则的回答策略分类器
 * <p>
 * 通过关键词匹配来判断用户问题的意图类型（如人物、时间线、地点等），
 * 并能拦截与小说领域无关的通用问题。
 * </p>
 */
@Component
public class RuleBasedPolicyClassifier implements AnswerPolicyClassifier {

    // 不支持回答的领域外关键词集合
    private static final Set<String> UNSUPPORTED_KEYWORDS = Set.of(
        "写代码", "编程", "java", "python", "c++",
        "天气", "股票", "汇率", "新闻",
        "现实", "今天", "翻译", "英语", "总结全文",
        "哈利波特", "三体", "金庸"
    );

    // 人物关系及特征相关问题的关键词列表
    private static final List<String> CHARACTER_KEYWORDS = List.of(
        "谁", "关系", "性格", "外貌", "长相", "身份",
        "父亲", "母亲", "师傅", "徒弟", "主角", "配角",
        "妻子", "丈夫", "兄弟", "姐妹"
    );

    // 时间线及时间顺序相关问题的关键词列表
    private static final List<String> TIMELINE_KEYWORDS = List.of(
        "什么时候", "时间", "多久", "哪一年", "何时",
        "几点", "年代", "岁月", "之前", "之后", "顺序", "先", "后"
    );

    // 地点及位置相关问题的关键词列表
    private static final List<String> LOCATION_KEYWORDS = List.of(
        "在哪里", "在哪", "地点", "位置", "去哪", "位于", "何处", "城市", "地方", "方位"
    );

    /**
     * 对用户问题进行意图分类
     *
     * @param question 用户的自然语言提问
     * @return 问题的意图类型 {@link AnswerType}，例如人物、时间线、地点、事实或不支持的类型
     */
    @Override
    public AnswerType classify(String question) {
        // 判空处理，若问题为空则认为不受支持
        if (question == null || question.isBlank()) {
            return AnswerType.UNSUPPORTED;
        }

        // 统一转换为小写并去除首尾空格，方便后续匹配
        String q = question.toLowerCase().trim();

        // 最高优先级：域外问题直接拦截
        // 只要问题中包含任何一个不支持的关键词，即拒绝回答
        if (UNSUPPORTED_KEYWORDS.stream().anyMatch(q::contains)) {
            return AnswerType.UNSUPPORTED;
        }

        // 意图匹配：依次判断是否符合人物、时间线、地点等特征
        
        // 检查是否是关于人物或角色的问题
        if (CHARACTER_KEYWORDS.stream().anyMatch(q::contains)) {
            return AnswerType.CHARACTER;
        }
        
        // 检查是否是关于时间线或顺序的问题
        if (TIMELINE_KEYWORDS.stream().anyMatch(q::contains)) {
            return AnswerType.TIMELINE;
        }
        
        // 检查是否是关于地点或位置的问题
        if (LOCATION_KEYWORDS.stream().anyMatch(q::contains)) {
            return AnswerType.LOCATION;
        }

        // 默认情况：如果未匹配上述特定意图，则归类为通用事实类问题
        return AnswerType.FACT;
    }
}
