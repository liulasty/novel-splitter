## application

`application` 模块是整个多模块工程的“总控中心”与启动入口，其核心职责是将所有分散的业务模块组装成一个完整的、可独立运行的应用程序。该模块主要承担了暴露对外交互接口、统筹系统级任务调度以及管理 Spring 容器生命周期的重任。其主要功能包括：基于 Spring Boot Web 提供用于小说上传、知识库管理和 RAG 问答的 RESTful 风格 API；集成并配置 RabbitMQ 以支持长耗时拆分任务的异步消息队列处理（如 `SplitWorker`）；以及整合 Knife4j 自动生成友好的在线接口文档。此外，它还负责加载 `application.yml` 和 `.env`（通过 dotenv）等全局配置文件。在依赖关系上，作为顶层聚合模块，它向下囊括了 `pipeline`、`retrieval`、`llm-client` 等所有核心业务与支撑模块，并引入了 Spring Boot 全家桶作为基础运行环境。

**典型使用场景与入口类说明：**
* 系统的主启动类为 `NovelSplitApplication`，开发者或部署环境通过运行此类来启动整个后台服务进程。
* 包含了如 `NovelController`、`TaskController` 和 `ChromaManagementController` 等各种 Web API 控制器，它们是前端页面（如 React 客户端）与系统交互的直接入口。
* 在处理如“小说解析”这样的大型任务时，Controller 会发布消息，而本模块内的异步监听器（Worker）则负责拉取消息并驱动底层 Pipeline 运作。
