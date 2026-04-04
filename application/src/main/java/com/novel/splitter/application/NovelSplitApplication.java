package com.novel.splitter.application;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * @author Administrator
 */
@SpringBootApplication
@EnableConfigurationProperties
@ComponentScan("com.novel.splitter")
@EntityScan({"com.novel.splitter.domain.entity", "com.novel.splitter.repository.entity"})
@EnableJpaRepositories("com.novel.splitter.repository.api")
public class NovelSplitApplication {
    public static void main(String[] args) {
        String activeProfile = resolveActiveProfile();
        Dotenv dotenv = Dotenv.configure()
                .directory("config")
                .filename(".env." + activeProfile)
                .ignoreIfMissing()
                .load();
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
        if (System.getProperty("spring.profiles.active") == null) {
            String configuredProfile = dotenv.get("SPRING_PROFILES_ACTIVE");
            if (configuredProfile != null && !configuredProfile.isBlank()) {
                System.setProperty("spring.profiles.active", configuredProfile);
            }
        }

        SpringApplication.run(NovelSplitApplication.class, args);
    }

    private static String resolveActiveProfile() {
        String profile = System.getProperty("spring.profiles.active");
        if (profile == null || profile.isBlank()) {
            profile = System.getenv("SPRING_PROFILES_ACTIVE");
        }
        if (profile == null || profile.isBlank()) {
            return "dev";
        }
        String normalized = profile.split(",")[0].trim().toLowerCase();
        return switch (normalized) {
            case "prod", "production" -> "prod";
            default -> "dev";
        };
    }
}
