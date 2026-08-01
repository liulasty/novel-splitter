package com.novel.splitter.application.service.settings;

import com.novel.splitter.application.model.dto.SystemSettingsDto;
import com.novel.splitter.infrastructure.persistence.entity.JpaSystemConfigEntity;
import com.novel.splitter.infrastructure.persistence.repository.JpaSystemConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SystemSettingsServiceImplTest {

    @Mock
    private JpaSystemConfigRepository repo;
    @Mock
    private ConfigurableEnvironment env;

    private SystemSettingsServiceImpl service;

    @BeforeEach
    void setUp() {
        when(env.getPropertySources()).thenReturn(new MutablePropertySources());
        service = new SystemSettingsServiceImpl(repo, env);
    }

    @Test
    void testGetSettingsReturnsDefaultsWhenDbEmpty() {
        when(repo.findAll()).thenReturn(Collections.emptyList());
        SystemSettingsDto dto = service.getSettings();
        assertNotNull(dto);
        assertNotNull(dto.getCategories());
    }

    @Test
    void testDbOverrideMergesWithDefaults() {
        JpaSystemConfigEntity e = new JpaSystemConfigEntity();
        e.setId(1L);
        e.setConfigKey("test.key");
        e.setConfigValue("overridden");
        e.setCategory("other");

        when(repo.findAll()).thenReturn(Collections.singletonList(e));
        SystemSettingsDto dto = service.getSettings();
        assertNotNull(dto);
        assertTrue(dto.getCategories().values().stream()
                .flatMap(java.util.Collection::stream)
                .anyMatch(item -> "test.key".equals(item.getConfigKey()) && !item.isDefault()));
    }
}
