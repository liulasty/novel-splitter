package com.novel.splitter.application.service.settings;

import com.novel.splitter.application.model.dto.ConfigSaveRequest;
import com.novel.splitter.application.model.dto.SystemSettingsDto;
import com.novel.splitter.application.model.dto.SystemSettingsDto.ConfigItem;
import com.novel.splitter.infrastructure.persistence.entity.JpaSystemConfigEntity;
import com.novel.splitter.infrastructure.persistence.repository.JpaSystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingsServiceImpl implements SystemSettingsService {

    private static final String MASKED = "••••••••";
    private static final Set<String> SECRET_KEYWORDS = Set.of(
            "api-key", "api_key", "apikey",
            "password", "passwd",
            "secret", "token");

    private final JpaSystemConfigRepository repo;
    private final ConfigurableEnvironment env;

    /** yml 默认值缓存，启动后只读 */
    private volatile Map<String, ConfigItem> ymlDefaults;

    private static boolean isSecretKey(String key) {
        String lower = key.toLowerCase();
        for (String kw : SECRET_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private static String maskIfSecret(String key, String value) {
        if (value == null || value.isEmpty()) return value;
        return isSecretKey(key) ? MASKED : value;
    }

    @Override
    public Map<String, ConfigItem> getYmlDefaults() {
        if (ymlDefaults == null) {
            ymlDefaults = loadYmlDefaults();
        }
        return ymlDefaults;
    }

    private Map<String, ConfigItem> loadYmlDefaults() {
        Map<String, ConfigItem> result = new LinkedHashMap<>();
        MutablePropertySources sources = env.getPropertySources();
        for (org.springframework.core.env.PropertySource<?> ps : sources) {
            if (ps.getName().contains("application") && ps instanceof EnumerablePropertySource) {
                EnumerablePropertySource<?> eps = (EnumerablePropertySource<?>) ps;
                for (String key : eps.getPropertyNames()) {
                    String cat = categoryOf(key);
                    if (cat == null) continue;
                    String resolved = env.getProperty(key);
                    if (resolved == null) {
                        Object raw = eps.getProperty(key);
                        resolved = raw != null ? String.valueOf(raw) : "";
                    }
                    result.put(key, ConfigItem.builder()
                            .configKey(key).configValue(maskIfSecret(key, resolved)).category(cat)
                            .isDefault(true).build());
                }
            }
        }
        return result;
    }

    /** 返回 category 名，null 表示不需要暴露给 settings 页面 */
    private static String categoryOf(String key) {
        if (key.startsWith("embedding.") || key.startsWith("chroma.")) return "embedding_chroma";
        if (key.startsWith("llm.")) return "llm";
        if (key.startsWith("splitter.ingestion") || key.startsWith("splitter.rule")
                || key.startsWith("splitter.embed")
                || key.startsWith("splitter.rabbitmq") || key.startsWith("splitter.storage"))
            return "splitter";
        if (key.startsWith("assembler.")) return "assembler";
        if (key.startsWith("splitter.rag.")) return "rag";
        if (key.startsWith("novel.")) return "llm";
        return null; // 排除 spring.* / server.* / logging.* 等框架配置
    }

    @Override
    @Transactional(readOnly = true)
    public SystemSettingsDto getSettings() {
        Map<String, ConfigItem> defaults = new HashMap<>(getYmlDefaults());
        List<JpaSystemConfigEntity> dbEntries = repo.findAll();

        for (JpaSystemConfigEntity e : dbEntries) {
            defaults.put(e.getConfigKey(), ConfigItem.builder()
                    .id(e.getId())
                    .configKey(e.getConfigKey())
                    .configValue(maskIfSecret(e.getConfigKey(), e.getConfigValue()))
                    .category(e.getCategory() != null ? e.getCategory() : categoryOf(e.getConfigKey()))
                    .description(e.getDescription())
                    .isDefault(false)
                    .build());
        }

        Map<String, List<ConfigItem>> grouped = defaults.values().stream()
                .collect(Collectors.groupingBy(ConfigItem::getCategory,
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (List<ConfigItem> items : grouped.values()) {
            items.sort(Comparator.comparing(ConfigItem::getConfigKey));
        }

        return SystemSettingsDto.builder().categories(grouped).build();
    }

    @Override
    @Transactional
    public ConfigItem save(ConfigSaveRequest request) {
        String key = request.getConfigKey();
        String value = request.getConfigValue();

        // 密钥类字段，如果传了掩码值就保留 DB 已有值
        if (isSecretKey(key) && MASKED.equals(value)) {
            JpaSystemConfigEntity existing = repo.findByConfigKey(key).orElse(null);
            if (existing != null) {
                existing.setDescription(request.getDescription());
                existing.setCategory(request.getCategory() != null ? request.getCategory() : categoryOf(key));
                repo.save(existing);
                return ConfigItem.builder()
                        .id(existing.getId()).configKey(key).configValue(MASKED)
                        .category(existing.getCategory()).description(existing.getDescription())
                        .isDefault(false).build();
            }
            // 无 DB 记录时 fall through 用 yml 默认值（不写入，直接返回 masked）
            return ConfigItem.builder()
                    .configKey(key).configValue(MASKED)
                    .category(request.getCategory() != null ? request.getCategory() : categoryOf(key))
                    .description(request.getDescription())
                    .isDefault(true).build();
        }

        JpaSystemConfigEntity entity = repo.findByConfigKey(key)
                .orElseGet(JpaSystemConfigEntity::new);
        entity.setConfigKey(key);
        entity.setConfigValue(value);
        entity.setCategory(request.getCategory() != null ? request.getCategory()
                : categoryOf(key));
        entity.setDescription(request.getDescription());
        entity = repo.save(entity);
        return ConfigItem.builder()
                .id(entity.getId())
                .configKey(entity.getConfigKey())
                .configValue(maskIfSecret(key, entity.getConfigValue()))
                .category(entity.getCategory())
                .description(entity.getDescription())
                .isDefault(false)
                .build();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteByKey(String configKey) {
        repo.deleteByConfigKey(configKey);
    }
}
