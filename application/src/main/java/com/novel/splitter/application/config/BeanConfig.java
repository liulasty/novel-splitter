package com.novel.splitter.application.config;

import com.novel.splitter.repository.api.NovelRepository;
import com.novel.splitter.repository.api.SceneRepository;
import com.novel.splitter.repository.impl.LocalFileNovelRepository;
import com.novel.splitter.repository.impl.LocalFileSceneRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public NovelRepository novelRepository() {
        return new LocalFileNovelRepository();
    }

    @Bean
    public SceneRepository sceneRepository(AppConfig appConfig) {
        return new LocalFileSceneRepository(appConfig.getStorage().getRootPath());
    }
}
