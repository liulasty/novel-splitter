package com.novel.splitter.retrieval.impl;

import com.novel.splitter.domain.model.AnswerType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PolicyClassifierTest {

    private final RuleBasedPolicyClassifier classifier = new RuleBasedPolicyClassifier();

    @Test
    void testExamples() {
        assertClassification("萧炎的父亲是谁？", AnswerType.CHARACTER);
        assertClassification("这本小说和哈利波特比怎么样？", AnswerType.UNSUPPORTED);
        assertClassification("萧炎什么时候晋升斗帝？", AnswerType.TIMELINE);
        assertClassification("迦南学院在哪里？", AnswerType.LOCATION);
        assertClassification("异火榜排名第一是什么？", AnswerType.FACT);
        
        // Print results for the final answer
        System.out.println("1. 萧炎的父亲是谁？ -> " + classifier.classify("萧炎的父亲是谁？"));
        System.out.println("2. 这本小说和哈利波特比怎么样？ -> " + classifier.classify("这本小说和哈利波特比怎么样？"));
        System.out.println("3. 萧炎什么时候晋升斗帝？ -> " + classifier.classify("萧炎什么时候晋升斗帝？"));
        System.out.println("4. 迦南学院在哪里？ -> " + classifier.classify("迦南学院在哪里？"));
        System.out.println("5. 异火榜排名第一是什么？ -> " + classifier.classify("异火榜排名第一是什么？"));
    }

    private void assertClassification(String question, AnswerType expected) {
        assertEquals(expected, classifier.classify(question), "Failed for question: " + question);
    }
}
