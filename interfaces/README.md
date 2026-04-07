# interfaces

## 模块概述
作为整个系统的网络出入口和 Spring Boot 启动容器，负责对外暴露 RESTful API 端点、处理 HTTP 请求与响应转换，并提供集成式的 Swagger/OpenAPI 在线文档。

## 核心职责
- **RESTful API 暴露**：提供 `NovelController`、`RagController` 等系列 Controller，接收前端请求并路由到 `application` 层的服务进行处理。
- **全局异常与响应包装**：通过 `GlobalExceptionHandler` 捕获各层抛出的异常，并使用 `GlobalResponseAdvice` 统一将响应格式化为标准的 `ApiResponse<T>` 结构。
- **Web 层配置与拦截**：提供 `WebMvcConfig` 和 `AuthInterceptor`，管理 CORS 跨域规则、静态资源映射以及 API 简单的安全认证机制。
- **应用启动管理**：包含 `NovelSplitApplication`，作为整个 Spring Boot 框架的最终启动入口。

## 技术栈
- 核心语言：Java 21
- 主要依赖：Spring Boot Starter Web, Spring Boot Starter Validation, Knife4j (OpenAPI 3), PostgreSQL Driver

## 模块依赖
- 本模块依赖的内部子模块：`application`, `infrastructure`
- 依赖本模块的内部子模块：无（处于系统依赖链的最外层顶端）

## 核心组件
| 组件名称 | 类型 | 核心职责 |
|----------|------|----------|
| `NovelSplitApplication` | 启动类 | 包含 `@SpringBootApplication` 注解，引导 Spring 容器扫描并初始化全项目 Bean。 |
| `RagController` | 控制器 | 处理 RAG 相关的交互请求（如智能问答 `chat` 端点），返回大模型分析结果。 |
| `NovelController` | 控制器 | 管理小说的上传、解析进度查询及系统基本状态。 |
| `GlobalExceptionHandler` | 切面(Advice) | 统一拦截并处理系统异常（如参数校验失败、数据未找到等），返回规范化错误码。 |
| `ApiResponse` | 泛型实体 | 定义全局统一的 HTTP 响应体结构（包含 code, message, data）。 |

## 使用示例
```java
// Controller 中典型的接口定义
@RestController
@RequestMapping("/api/v1/novels")
@Tag(name = "小说管理")
public class NovelController {

    @Autowired
    private NovelFacadeService facadeService;

    @PostMapping("/upload")
    @Operation(summary = "上传小说")
    public ApiResponse<String> uploadNovel(@RequestParam("file") MultipartFile file) {
        Novel novel = facadeService.uploadAndProcess(file, file.getOriginalFilename());
        return ApiResponse.success(novel.getId());
    }
}
```

## 扩展点
- **扩展点 1**：在 `WebMvcConfig` 中添加新的拦截器（Interceptor）或过滤器（Filter），以支持复杂的鉴权体系（如 JWT 或 OAuth2）。
- **扩展点 2**：引入 Spring Security 模块替换现有的简易 `AuthInterceptor` 机制，提供细粒度的 API 权限控制（RBAC）。

## 注意事项
- **注意 1**：Controller 层必须做到“极薄”，绝不允许包含任何业务逻辑处理，只能负责参数的接收、校验与结果的直接转发。
- **注意 2**：所有返回前端的数据对象必须是定义在 `application` 层的 DTO，严禁直接将 `domain` 或 `infrastructure` 的内部实体通过 Controller 暴露。