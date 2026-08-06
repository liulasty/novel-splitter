package com.novel.splitter.application.service.novel;

import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.repository.NovelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NovelServiceImpl implements NovelService {

    /** 与 DB 常见 varchar 上限对齐，避免极长文件名撑爆行 */
    private static final int MAX_TITLE_LENGTH = 200;

    private final NovelRepository novelRepository;
    private final NovelStorageService novelStorageService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public String createNovel(InputStream content, String originalFilename, String title, String author, String description) throws IOException {
        String novelId = UUID.randomUUID().toString();

        // 1) DB 优先：先用稳定的 novelId + 计划好的存储相对路径创建记录
        String storedRelativePath = novelStorageService.rawRelativePath(novelId);
        try {
            transactionTemplate.execute(status -> {
                saveNovelRecordWithId(novelId, storedRelativePath, title, author, description, originalFilename);
                return null;
            });
        } catch (RuntimeException ex) {
            throw ex;
        }

        // 2) 基于 novelId 路径将文件保存到本地存储
        try {
            String actualStored = novelStorageService.saveNovelAsRawByNovelId(novelId, content);
            if (!storedRelativePath.equals(actualStored)) {
                // 保持 DB 与实际存储路径一致
                transactionTemplate.execute(status -> {
                    Novel n = novelRepository.findById(novelId).orElseThrow();
                    n.setFilePath(actualStored);
                    n.setUpdatedAt(System.currentTimeMillis());
                    novelRepository.save(n);
                    return null;
                });
            }
        } catch (RuntimeException | IOException ex) {
            // 尽力而为：将小说标记为已删除，保持 DB 优先列表干净。
            transactionTemplate.execute(status -> {
                novelRepository.findById(novelId).ifPresent(n -> {
                    n.setDeleted(true);
                    n.setUpdatedAt(System.currentTimeMillis());
                    novelRepository.save(n);
                });
                return null;
            });
            // 若文件部分写入则清理本地文件
            novelStorageService.deleteNovelIfExists(storedRelativePath);
            throw ex;
        }

        return novelId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createNovelFromStoredFile(String storedRelativePath, String title, String author, String description) {
        if (storedRelativePath == null || storedRelativePath.isBlank()) {
            throw new IllegalArgumentException("storedRelativePath must not be blank");
        }
        return saveNovelRecord(storedRelativePath, title, author, description);
    }

    private String saveNovelRecord(String fileName, String title, String author, String description) {
        String novelId = UUID.randomUUID().toString();
        saveNovelRecordWithId(novelId, fileName, title, author, description, null);
        return novelId;
    }

    private void saveNovelRecordWithId(
            String novelId,
            String fileName,
            String title,
            String author,
            String description,
            String originalFilename) {
        String resolvedTitle = resolveNovelTitle(title, originalFilename, fileName);
        Novel novel = Novel.builder()
                .id(novelId)
                .title(resolvedTitle)
                .author(author)
                .description(description)
                .filePath(fileName)
                .status(NovelStatus.PENDING)
                .createdAt(System.currentTimeMillis())
                .updatedAt(System.currentTimeMillis())
                .isDeleted(false)
                .build();

        novelRepository.save(novel);
        log.info("小说实体已保存到数据库, novelId: {}, title: {}", novelId, resolvedTitle);
    }

    /**
     * 标题：显式 title &gt; 上传原始文件名（去扩展名）&gt; 存储路径最后一段（如 original）&gt; 兜底。
     */
    static String resolveNovelTitle(String explicitTitle, String originalFilename, String storedRelativePath) {
        String fromExplicit = explicitTitle != null ? sanitizeSingleLineTitle(explicitTitle) : "";
        if (!fromExplicit.isEmpty()) {
            return truncateByCodePoints(fromExplicit, MAX_TITLE_LENGTH);
        }
        String fromUpload = stripTxtExtension(lastPathSegment(originalFilename));
        if (!fromUpload.isEmpty()) {
            String s = sanitizeSingleLineTitle(fromUpload);
            if (!s.isEmpty()) {
                return truncateByCodePoints(s, MAX_TITLE_LENGTH);
            }
        }
        String fromPath = stripTxtExtension(lastPathSegment(storedRelativePath));
        if (!fromPath.isEmpty()) {
            String s = sanitizeSingleLineTitle(fromPath);
            if (!s.isEmpty()) {
                return truncateByCodePoints(s, MAX_TITLE_LENGTH);
            }
        }
        return "未命名小说";
    }

    private static String lastPathSegment(String path) {
        if (path == null) {
            return "";
        }
        String n = path.replace('\\', '/').trim();
        if (n.isEmpty()) {
            return "";
        }
        int i = n.lastIndexOf('/');
        return i >= 0 ? n.substring(i + 1) : n;
    }

    private static String stripTxtExtension(String name) {
        if (name == null) {
            return "";
        }
        String t = name.trim();
        if (t.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            return t.substring(0, t.length() - 4).trim();
        }
        return t;
    }

    /** 去掉不可见控制字符，折叠空白，单行展示 */
    private static String sanitizeSingleLineTitle(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0 || Character.getType(cp) == Character.CONTROL) {
                continue;
            }
            b.appendCodePoint(cp);
        }
        String s = b.toString().trim().replaceAll("\\s+", " ");
        return s;
    }

    private static String truncateByCodePoints(String s, int maxCodePoints) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        if (s.codePointCount(0, s.length()) <= maxCodePoints) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, maxCodePoints)).trim();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNovelStatus(String novelId, NovelStatus status) {
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new IllegalArgumentException("Novel not found: " + novelId));
                
        novel.updateStatus(status);
        novelRepository.save(novel);
        log.info("小说 {} 状态已更新为 {}", novelId, status);
    }

    @Override
    public Novel getNovelById(String novelId) {
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new IllegalArgumentException("Novel not found: " + novelId));
        if (novel.isDeleted()) {
            throw new IllegalArgumentException("Novel is deleted: " + novelId);
        }
        return novel;
    }

    @Override
    public List<Novel> listNovels() {
        return novelRepository.findAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeleteNovel(String novelId) {
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new IllegalArgumentException("Novel not found: " + novelId));
        if (novel.isDeleted()) {
            return;
        }
        novel.setDeleted(true);
        novel.setUpdatedAt(System.currentTimeMillis());
        novelRepository.save(novel);
        log.info("已软删除小说 {}", novelId);
    }
}
