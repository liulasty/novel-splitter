# Application 层详细设计

## 1. 概述
Application 层是整个系统的入口和协调者。它负责启动 Spring Boot 应用，接收外部请求（CLI 或 REST），并编排底层的切分任务。该层不包含具体的业务规则（切分逻辑），而是将请求委托给 Pipeline 层执行。

## 2. 核心职责
- **启动引导**：负责 Spring Boot 容器的初始化。
- **接口暴露**：提供 CLI（命令行）和 REST API 两种交互方式。
- **任务调度**：接收用户请求，创建切分任务上下文，并触发 Pipeline。
- **配置管理**：加载系统配置（如存储路径、默认策略等）。

## 3. 模块结构
```
application
├── config/                  # Spring 配置类
│   ├── AppConfig.java
│   └── ExecutorConfig.java  # 线程池配置（如果有异步需求）
├── controller/              # REST 接口
│   ├── SplitController.java
│   └── model/              # DTO 对象
│       ├── SplitRequest.java
│       └── SplitResponse.java
├── runner/                  # CLI 入口
│   └── SplitCommandRunner.java
└── NovelSplitApplication.java # 启动类
```

## 4. 详细组件说明

### 4.1 NovelSplitApplication
- **功能**：Spring Boot 标准启动类。
- **注解**：`@SpringBootApplication`
- **依赖**：扫描所有子模块的组件（如果包结构统一）。

### 4.2 SplitCommandRunner
- **功能**：实现 `CommandLineRunner` 接口，支持命令行方式运行切分任务。
- **输入参数**：
  - `--file=<path>`：小说文件路径。
  - `--strategy=<version>`：切分策略版本（可选，默认 v1）。
- **流程**：
  1. 解析命令行参数。
  2. 验证文件是否存在。
  3. 构建 `PipelineContext`。
  4. 调用 `SplitPipeline.execute(context)`。
  5. 输出执行结果摘要到控制台。

### 4.3 SplitController (REST API)
- **功能**：提供 HTTP 接口供外部系统调用。
- **端点**：
  - `POST /api/v1/split`
    - Request Body:
      ```json
      {
        "filePath": "/path/to/novel.txt",
        "strategy": "v1-rule-basic",
        "options": {
          "overwrite": true
        }
      }
      ```
    - Response:
      ```json
      {
        "taskId": "uuid",
        "status": "SUCCESS",
        "sceneCount": 150,
        "outputPath": "/path/to/output"
      }
      ```

## 5. 交互流程
1. **用户触发** -> Application Layer (Runner/Controller)
2. Application Layer -> 组装参数 -> `PipelineContext`
3. Application Layer -> 调用 `SplitPipeline.run(context)`
4. `SplitPipeline` 返回执行结果
5. Application Layer -> 格式化输出/响应

## 6. 异常处理
- 捕获 Pipeline 抛出的业务异常（如文件无法读取、格式错误）。
- 转换为用户友好的错误信息（CLI 输出或 HTTP 错误码）。
- 记录系统日志。

## 7. 待办事项
- [ ] 定义详细的 DTO 结构。
- [ ] 确定日志规范（Log4j2/Logback）。
- [ ] 增加 Swagger/OpenAPI 文档注解（可选）。
