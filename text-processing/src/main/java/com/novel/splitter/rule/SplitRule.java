package com.novel.splitter.rule;

import com.novel.splitter.domain.model.SemanticSegment;

import java.util.List;

/**
 * 切分规则接口
 * <p>
 * 定义了小说文本处理系统中的文本分块规则的统一接口。
 * 实现该接口的类负责基于不同策略（如长度、语义密度、边界标志等）评估当前文本块是否应该切分。
 * </p>
 */
public interface SplitRule {

    /**
     * 切分决策枚举类型
     * 用于表示切分规则对当前文本块评估后得出的决策结果。
     */
    enum Decision {
        /** 必须进行切分（例如：遇到强边界或超过绝对最大长度阈值） */
        MUST_SPLIT,
        
        /** 允许进行切分（例如：字数达到目标长度且语义结构相对完整） */
        CAN_SPLIT,
        
        /** 不应进行切分（例如：处于对话连续部分，或字数过少尚未达到目标） */
        NO_SPLIT
    }

    /**
     * 评估当前累积的文本是否满足切分条件。
     * 规则实现类将根据自己的逻辑结合参数返回具体的切分决策。
     *
     * @param currentLength 当前 Scene (场景或文本块) 已经累积的总字数
     * @param currentBuffer 当前 Scene 已经累积的语义段落列表
     * @param nextSegment   下一个即将加入当前 Scene 的语义段落对象
     * @return 评估得出的切分决策 {@link Decision}，指导调用方是否切分
     */
    Decision evaluate(int currentLength, List<SemanticSegment> currentBuffer, SemanticSegment nextSegment);
}
