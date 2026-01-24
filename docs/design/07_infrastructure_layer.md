# Infrastructure 层详细设计

## 1. 概述
Infrastructure 层提供技术基础设施支持，包括具体的 IO 操作、JSON 序列化、通用工具类等。它是最底层的技术支撑，对上层业务屏蔽具体的技术实现细节。

## 2. 核心功能

### 2.1 文件 IO
- 提供健壮的文件读写工具。
- 支持多种字符集检测（GBK, UTF-8 等）。
- 异常处理和资源关闭。

### 2.2 JSON 处理
- 封装 Jackson 或 Gson。
- 配置序列化策略（如是否格式化输出、日期格式、空字段处理）。

### 2.3 配置管理
- 加载 `application.yml` 或 `application.properties`。
- 提供配置对象的注入。

## 3. 模块结构
```
infrastructure
├── io
│   ├── FileReaderUtil.java
│   └── FileWriterUtil.java
├── json
│   ├── JsonUtils.java
│   └── CustomSerializers.java
├── config
│   └── AppConfiguration.java (读取外部配置)
└── util
    ├── StringUtils.java
    └── IdGenerator.java (UUID/Snowflake)
```

## 4. 技术选型
- **JSON**: Jackson (Spring Boot 默认，性能好，功能强)。
- **IO**: Java NIO (Files, Path) 或 Apache Commons IO。
- **Logging**: SLF4J + Logback。

## 5. 待办事项
- [ ] 实现一个能够自动识别文件编码的读取工具（小说文件常见 GBK/GB2312）。
- [ ] 封装统一的异常体系，将 IOException 转换为 RuntimeException（如 `InfrastructureException`）。
