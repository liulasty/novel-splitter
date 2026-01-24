package com.novel.splitter.application.runner;

import com.novel.splitter.application.service.SplitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;


import java.util.concurrent.Callable;

/**
 * CLI 入口
 * 使用 picocli 解析命令行参数（需要引入 picocli 依赖，或者简单手动解析）
 * 这里为了演示简单，直接手动解析 Spring Boot 的 args
 */
@Slf4j
@Component
public class SplitCommandRunner implements CommandLineRunner {

    private final SplitService splitService;

    public SplitCommandRunner(SplitService splitService) {
        this.splitService = splitService;
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0) {
            return; // 无参数启动，可能是 Web 模式，不执行 CLI 逻辑
        }

        // 简单参数解析: --file=path --version=v1
        String file = null;
        String version = "v1-default";

        for (String arg : args) {
            if (arg.startsWith("--file=")) {
                file = arg.substring(7);
            } else if (arg.startsWith("--version=")) {
                version = arg.substring(10);
            }
        }

        if (file != null) {
            log.info("Starting CLI task for file: {}", file);
            try {
                splitService.executeSplit(file, version);
                log.info("CLI task completed successfully.");
            } catch (Exception e) {
                log.error("CLI task failed", e);
            }
        }
    }
}
