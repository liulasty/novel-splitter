package com.novel.splitter.application.config;

import com.novel.splitter.repository.api.JpaSceneRepository;
import com.novel.splitter.repository.api.NovelRepository;
import com.novel.splitter.repository.api.SceneRepository;
import com.novel.splitter.repository.impl.JpaSceneRepositoryImpl;
import com.novel.splitter.repository.impl.LocalFileNovelRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public NovelRepository novelRepository() {
        return new LocalFileNovelRepository();
    }

    @Bean
    public SceneRepository sceneRepository(JpaSceneRepository jpaSceneRepository) {
        return new JpaSceneRepositoryImpl(jpaSceneRepository);
    }
}
