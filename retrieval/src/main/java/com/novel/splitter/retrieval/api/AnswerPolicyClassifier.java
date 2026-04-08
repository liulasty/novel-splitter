package com.novel.splitter.retrieval.api;

import com.novel.splitter.domain.model.AnswerType;

/**
 * 回答策略分类器接口
 * <p>
 * 在 NLP/RAG 问答系统中，用于对用户提出的问题意图进行分类。
 * 从而决定后续的检索策略或生成策略（例如：事实问答、摘要生成、闲聊等）。
 * </p>
 */
public interface AnswerPolicyClassifier {
    /**
     * 将用户的问题分类为预定义的类型。
     * 
     * // 根据分类结果可以为后续的 RAG 检索链路选择不同的处理策略
     * 
     * @param question 用户输入的自然语言问题
     * @return 确定的回答类型（AnswerType），用于指导后续处理策略
     */
    AnswerType classify(String question);
}
