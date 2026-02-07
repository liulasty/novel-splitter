package com.novel.splitter.retrieval.impl;

import com.novel.splitter.domain.model.dto.RetrievalQuery;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RetrievalQueryBuilderTest {

    private final RetrievalQueryBuilder builder = new RetrievalQueryBuilder();

    @Test
    void testBuild_PreviousChapter() {
        // Input: "上一章发生了什么？", current = 10
        RetrievalQuery query = builder.build("上一章发生了什么？", 10);
        
        assertEquals("上一章发生了什么？", query.getQuestion());
        assertEquals(9, query.getChapterFrom());
        assertEquals(9, query.getChapterTo());
        assertNull(query.getRole());
    }

    @Test
    void testBuild_ThisChapter() {
        // Input: "这一章有几个人？", current = 5
        RetrievalQuery query = builder.build("这一章有几个人？", 5);
        
        assertEquals("这一章有几个人？", query.getQuestion());
        assertEquals(5, query.getChapterFrom());
        assertEquals(5, query.getChapterTo());
        assertNull(query.getRole());
    }

    @Test
    void testBuild_DialogueRole() {
        // Input: "他说了什么", current = 8
        RetrievalQuery query = builder.build("他说了什么", 8);
        
        assertEquals("他说了什么", query.getQuestion());
        assertEquals("dialogue", query.getRole());
        assertNull(query.getChapterFrom());
        assertNull(query.getChapterTo());
    }

    @Test
    void testBuild_ComplexCombination() {
        // Input: "上一章他说了什么", current = 20
        RetrievalQuery query = builder.build("上一章他说了什么", 20);
        
        assertEquals("上一章他说了什么", query.getQuestion());
        assertEquals(19, query.getChapterFrom());
        assertEquals(19, query.getChapterTo());
        assertEquals("dialogue", query.getRole());
    }

    @Test
    void testBuild_NoMatch() {
        // Input: "楚晨是谁？", current = 10
        RetrievalQuery query = builder.build("楚晨是谁？", 10);
        
        assertEquals("楚晨是谁？", query.getQuestion());
        assertNull(query.getChapterFrom());
        assertNull(query.getChapterTo());
        assertNull(query.getRole());
    }
}
