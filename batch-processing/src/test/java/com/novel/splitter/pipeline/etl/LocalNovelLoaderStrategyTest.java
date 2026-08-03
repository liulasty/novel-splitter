package com.novel.splitter.pipeline.etl;

import com.novel.splitter.core.ChapterRecognitionStrategyRegistry;
import com.novel.splitter.domain.model.ChapterData;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 章节识别按 {@link com.novel.splitter.domain.enums.RecognitionStrategyType} 枚举分发：
 * CN_CHAPTER 只识别第X章；PROLOGUE 识别序章/楔子/前言；CUSTOM 用给定正则；
 * 空/null 默认 CN_CHAPTER；非法字符串抛 IllegalArgumentException；旧值 PLAIN 兼容为 CN_CHAPTER。
 *
 * <p>正文行统一以「这里是…」开头，避免误命中第X章/序章等标题正则。</p>
 */
class LocalNovelLoaderStrategyTest {

    @TempDir
    Path tmp;

    private LocalNovelLoader loader;

    @BeforeEach
    void setUp() {
        loader = new LocalNovelLoader(noopRepository(), new ChapterRecognitionStrategyRegistry());
    }

    /** novelId=null 时 load 不会调用 repository，故此处提供永不触发的桩。 */
    private NovelCacheRepository noopRepository() {
        return new NovelCacheRepository() {
            private UnsupportedOperationException unsupported() {
                return new UnsupportedOperationException("repository not used when novelId=null");
            }
            @Override public Path rawOriginalPath(String novelId) { throw unsupported(); }
            @Override public Path rawDirPath(String novelId) { throw unsupported(); }
            @Override public Path parsedDirPath(String novelId) { throw unsupported(); }
            @Override public Path parsedChapterPath(String novelId, int chapterIndex) { throw unsupported(); }
            @Override public void saveChapter(String novelId, int chapterIndex, ChapterData chapterData) { throw unsupported(); }
            @Override public ChapterData loadChapter(String novelId, int chapterIndex) { throw unsupported(); }
            @Override public Stream<Path> listChapterFiles(String novelId) { throw unsupported(); }
            @Override public InputStream openChapterInputStream(String novelId, int chapterIndex) { throw unsupported(); }
            @Override public OutputStream openChapterOutputStream(String novelId, int chapterIndex) { throw unsupported(); }
            @Override public void removeParsedArtifacts(String novelId) { throw unsupported(); }
            @Override public void removeNovelArtifacts(String novelId) { throw unsupported(); }
        };
    }

    private Path writeFile(String content) throws Exception {
        Path f = tmp.resolve("n-" + System.nanoTime() + ".txt");
        Files.writeString(f, content);
        return f;
    }

    private static final String CN_CHAPTER_FILE = """
            序章 乱世
            这里是序章正文，用于填充长度校验。这里是序章正文，用于填充长度校验。这里是序章正文，用于填充长度校验。
            第一章 初入江湖
            这里是第一章正文，用于填充长度校验。这里是第一章正文，用于填充长度校验。这里是第一章正文，用于填充长度校验。
            第二章 剑试天下
            这里是第二章正文，用于填充长度校验。这里是第二章正文，用于填充长度校验。这里是第二章正文，用于填充长度校验。
            """;

    @Test
    void loadWithCnChapterStrategyOnlySplitsOnChapter() throws Exception {
        Path f = writeFile(CN_CHAPTER_FILE);

        Novel novel = loader.load(null, f, null, "CN_CHAPTER");

        assertEquals(2, novel.getChapters().size());
        assertEquals("第一章 初入江湖", novel.getChapters().get(0).getTitle());
        assertEquals("第二章 剑试天下", novel.getChapters().get(1).getTitle());
    }

    @Test
    void loadWithPrologueStrategyRecognizesPrologue() throws Exception {
        Path f = writeFile("""
                前言 开篇
                这里是前言正文，用于填充长度校验。这里是前言正文，用于填充长度校验。这里是前言正文，用于填充长度校验。
                第一章 初入江湖
                这里是第一章正文，用于填充长度校验。这里是第一章正文，用于填充长度校验。这里是第一章正文，用于填充长度校验。
                """);

        Novel novel = loader.load(null, f, null, "PROLOGUE");

        assertEquals(1, novel.getChapters().size());
        assertTrue(novel.getChapters().get(0).getTitle().startsWith("前言"));
    }

    @Test
    void loadWithCustomStrategyUsesGivenRegex() throws Exception {
        Path f = writeFile("""
                序章 乱世
                这里是序章正文，用于填充长度校验。这里是序章正文，用于填充长度校验。这里是序章正文，用于填充长度校验。
                第一卷 风云
                这里是第一卷正文，用于填充长度校验。这里是第一卷正文，用于填充长度校验。这里是第一卷正文，用于填充长度校验。
                第一章 初入江湖
                这里是第一章正文，用于填充长度校验。这里是第一章正文，用于填充长度校验。这里是第一章正文，用于填充长度校验。
                """);

        Novel novel = loader.load(null, f, "第一卷.*", "CUSTOM");

        assertEquals(1, novel.getChapters().size());
        assertEquals("第一卷 风云", novel.getChapters().get(0).getTitle());
    }

    @Test
    void loadWithNullStrategyDefaultsToCnChapter() throws Exception {
        Path f = writeFile(CN_CHAPTER_FILE);

        Novel novel = loader.load(null, f, null, null);

        assertEquals(2, novel.getChapters().size());
    }

    @Test
    void loadWithBlankStrategyDefaultsToCnChapter() throws Exception {
        Path f = writeFile(CN_CHAPTER_FILE);

        Novel novel = loader.load(null, f, null, "");

        assertEquals(2, novel.getChapters().size());
    }

    @Test
    void loadWithLegacyPlainStrategyDefaultsToCnChapter() throws Exception {
        Path f = writeFile(CN_CHAPTER_FILE);

        Novel novel = loader.load(null, f, null, "PLAIN");

        assertEquals(2, novel.getChapters().size());
    }

    @Test
    void loadWithUnknownStrategyThrowsIllegalArgumentException() throws Exception {
        Path f = writeFile(CN_CHAPTER_FILE);

        assertThrows(IllegalArgumentException.class,
                () -> loader.load(null, f, null, "FOO_BAR"));
    }

    /**
     * parsed 缓存文件（chapter_N.json）是可重建产物：写盘失败不影响基准完整性——
     * load 仍返回完整章节集合（写失败仅记日志）。
     */
    @Test
    void loadToleratesCacheWriteFailure_baselineStillProduced() throws Exception {
        NovelCacheRepository throwingRepo = new NovelCacheRepository() {
            @Override
            public void saveChapter(String novelId, int chapterIndex, ChapterData chapterData) {
                throw new RuntimeException("disk full: chapter cache write failed");
            }
            @Override public Path rawOriginalPath(String novelId) { return tmp.resolve(novelId).resolve("original.txt"); }
            @Override public Path rawDirPath(String novelId) { return tmp.resolve(novelId); }
            @Override public Path parsedDirPath(String novelId) { return tmp.resolve(novelId + "-parsed"); }
            @Override public Path parsedChapterPath(String novelId, int chapterIndex) { return tmp.resolve(novelId + "-parsed").resolve("chapter_" + chapterIndex + ".json"); }
            @Override public ChapterData loadChapter(String novelId, int chapterIndex) { throw new UnsupportedOperationException("not used"); }
            @Override public Stream<Path> listChapterFiles(String novelId) { return Stream.empty(); }
            @Override public InputStream openChapterInputStream(String novelId, int chapterIndex) { throw new UnsupportedOperationException("not used"); }
            @Override public OutputStream openChapterOutputStream(String novelId, int chapterIndex) { throw new UnsupportedOperationException("not used"); }
            @Override public void removeParsedArtifacts(String novelId) { }
            @Override public void removeNovelArtifacts(String novelId) { }
        };
        LocalNovelLoader loaderWithBrokenCache = new LocalNovelLoader(throwingRepo, new ChapterRecognitionStrategyRegistry());
        Path f = writeFile(CN_CHAPTER_FILE);

        Novel novel = loaderWithBrokenCache.load("n1", f, null, "CN_CHAPTER");

        assertEquals(2, novel.getChapters().size());
        assertEquals("第一章 初入江湖", novel.getChapters().get(0).getTitle());
    }
}
