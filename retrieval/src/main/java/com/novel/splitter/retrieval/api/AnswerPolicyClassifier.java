package com.novel.splitter.retrieval.api;

import com.novel.splitter.domain.model.AnswerType;

public interface AnswerPolicyClassifier {
    /**
     * Classifies the user question into a predefined type.
     * Used to determine retrieval strategy or policy.
     *
     * @param question The user's input question
     * @return The determined AnswerType
     */
    AnswerType classify(String question);
}
