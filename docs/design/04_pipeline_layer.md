# Pipeline 层详细设计

## 1. 概述
Pipeline 层负责将 Splitter 层的原子能力、Repository 层的存储能力和 Validation 层的校验能力串联起来，形成一个完整的、健壮的处理流程。

## 2. 核心概念
- **PipelineContext**：在流水线各阶段传递的上下文对象，包含配置、中间产物和最终结果。
- **Stage (阶段)**：流水线中的独立处理单元。
- **Middleware (中间件)**：可选的拦截器，用于日志、监控等。

## 3. 流程设计

### 3.1 标准 Pipeline 流程
1. **LoadStage**: 从文件系统读取 Raw Text。
2. **PreprocessStage**: 文本清洗（去乱码、统一标点）。
3. **SplitStage**: 调用 Splitter 核心逻辑生成 Scenes。
4. **ValidationStage**: 检查生成的 Scenes 是否合规。
5. **PersistStage**: 将 Scenes 和 Metadata 写入 Repository。
6. **ReportStage**: 生成处理报告。

### 3.2 错误处理
- **Fail-Fast**：关键错误（如文件不存在、格式完全错误）立即终止。
- **Fail-Safe**：非关键错误（如某一段过长）记录警告但继续处理。

## 4. 详细组件

### 4.1 PipelineContext
```java
public class PipelineContext {
    private final String taskId;
    private final SplitConfig config;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final List<String> errors = new ArrayList<>();
    
    // 数据槽位
    private String rawText;
    private List<Scene> resultScenes;
    // ...
}
```

### 4.2 Stage 接口
```java
public interface Stage {
    void process(PipelineContext context) throws PipelineException;
    String getName();
}
```

### 4.3 DefaultPipeline
```java
public class DefaultPipeline {
    private List<Stage> stages;

    public void execute(PipelineContext context) {
        for (Stage stage : stages) {
            try {
                stage.process(context);
            } catch (Exception e) {
                // Handle exception
                throw e;
            }
        }
    }
}
```

## 5. 模块结构
```
pipeline
├── api
│   ├── Pipeline.java
│   └── Stage.java
├── context
│   └── PipelineContext.java
├── impl
│   └── SequentialPipeline.java
└── stages
    ├── LoadFileStage.java
    ├── CoreSplitStage.java
    ├── ValidationStage.java
    └── SaveResultStage.java
```

## 6. 待办事项
- [ ] 实现 PipelineContext 的线程安全性（如果打算并发处理多个任务）。
- [ ] 设计 Stage 的顺序配置机制（代码硬编码 vs 配置文件）。
- [ ] 添加计时器，统计每个 Stage 的耗时。
