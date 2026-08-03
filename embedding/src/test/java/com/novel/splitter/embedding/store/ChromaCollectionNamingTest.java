package com.novel.splitter.embedding.store;

import com.novel.splitter.embedding.api.VectorStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChromaDB 集合命名规范测试。
 * <p>
 * 验证 {@link VectorStore#collectionNameFor} 产出的名称满足 Chroma 限额与项目约束。
 * </p>
 */
class ChromaCollectionNamingTest {

    private static final Pattern ALLOWED = Pattern.compile("^c_[a-z0-9_]{1,55}$");

    @Test
    void nameMatchesAllowedPattern() {
        String name = VectorStore.collectionNameFor("abc123", "v1");

        assertNotNull(name);
        assertTrue(ALLOWED.matcher(name).matches(),
                "expected pattern " + ALLOWED.pattern() + " but got: " + name);
    }

    @Test
    void nameLengthWithinChromaLimit() {
        // Chroma recommends collection names <= 63 chars
        String name = VectorStore.collectionNameFor("abc123", "v1");

        assertTrue(name.length() <= 63,
                "collection name too long (" + name.length() + "): " + name);
    }

    @Test
    void idempotentSameInput() {
        String a = VectorStore.collectionNameFor("novel-1", "version-A");
        String b = VectorStore.collectionNameFor("novel-1", "version-A");

        assertEquals(a, b, "same inputs must produce same collection name");
    }

    @Test
    void differentNovelIdProducesDifferentName() {
        String a = VectorStore.collectionNameFor("novel-a", "v1");
        String b = VectorStore.collectionNameFor("novel-b", "v1");

        assertNotEquals(a, b, "different novel ids must produce different collection names");
    }

    @Test
    void differentVersionProducesDifferentName() {
        String a = VectorStore.collectionNameFor("novel-1", "v1");
        String b = VectorStore.collectionNameFor("novel-1", "v2");

        assertNotEquals(a, b, "different versions must produce different collection names");
    }

    @Test
    void stripsIllegalCharacters() {
        String name = VectorStore.collectionNameFor("My Novel!@#", "V 1.0-beta");

        assertTrue(ALLOWED.matcher(name).matches(),
                "name with illegal chars should be sanitized but got: " + name);
        assertFalse(name.contains("!"), "name should not contain '!'");
        assertFalse(name.contains("@"), "name should not contain '@'");
        assertFalse(name.contains("#"), "name should not contain '#'");
        assertFalse(name.contains(" "), "name should not contain spaces");
        assertFalse(name.contains("-"), "name should not contain hyphens");
        assertFalse(name.contains("."), "name should not contain dots");
    }

    @Test
    void longNovelIdIsTruncated() {
        String longId = "abcdefghijklmnopqrstuvwxyz123456";
        String name = VectorStore.collectionNameFor(longId, "v1");

        assertTrue(name.length() <= 63,
                "long novel id should be truncated, got length " + name.length() + ": " + name);
        assertTrue(ALLOWED.matcher(name).matches(),
                "truncated name should match pattern: " + name);
    }

    @Test
    void longVersionIsTruncated() {
        String longVer = "a".repeat(60) + "-very-very-very-long-suffix-that-exceeds-limits";
        String name = VectorStore.collectionNameFor("abc", longVer);

        assertTrue(name.length() <= 63,
                "long version should be truncated, got length " + name.length() + ": " + name);
        assertTrue(ALLOWED.matcher(name).matches(),
                "truncated name should match pattern: " + name);
    }

    @Test
    void preferStartsWithLowercaseC() {
        String name = VectorStore.collectionNameFor("abc123", "v1");

        assertTrue(name.startsWith("c_"),
                "collection name must start with 'c_': " + name);
    }

    @Test
    void allNamesAreDistinctWithManyInputs() {
        Set<String> names = new HashSet<>();
        String[] novelIds = {"n1", "n2", "n3", "n4", "n5"};
        String[] versions = {"v1", "v2", "v3"};

        for (String nid : novelIds) {
            for (String ver : versions) {
                String name = VectorStore.collectionNameFor(nid, ver);
                assertTrue(names.add(name),
                        "duplicate collection name: " + name + " for (" + nid + ", " + ver + ")");
            }
        }
        assertEquals(15, names.size());
    }
}
