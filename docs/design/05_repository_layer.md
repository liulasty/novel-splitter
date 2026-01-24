# Repository 层详细设计

## 1. 概述
Repository 层负责数据的持久化。本项目**不使用数据库**，而是采用结构化的本地文件系统作为存储介质。这种设计保证了数据的完全掌控、易迁移和易审计。

## 2. 存储结构设计

```
novel-storage/          # 根存储目录（可配置）
├── raw/                # 原始文件归档
│   └── {novel_name}.txt
├── scene/              # 切分结果
│   ├── {novel_name}/
│   │   ├── v1-basic/   # 策略版本 v1
│   │   │   ├── scenes.json       # 核心数据
│   │   │   └── metadata.json     # 统计信息
│   │   └── v2-enhanced/ # 策略版本 v2
│   │       └── ...
└── meta/               # 全局元数据
    └── processing-log.jsonl
```

## 3. 核心接口

### 3.1 NovelRepository
管理原始小说文件。
```java
public interface NovelRepository {
    String saveRaw(String filename, InputStream content);
    String loadRaw(String filename);
    boolean exists(String filename);
}
```

### 3.2 SceneRepository
管理切分后的 Scene 数据。
```java
public interface SceneRepository {
    void saveScenes(String novelName, String version, List<Scene> scenes);
    List<Scene> loadScenes(String novelName, String version);
}
```

## 4. 实现细节
- **JSON 序列化**：使用 Jackson 或 Gson 将对象序列化为 JSON 文件。
- **原子写入**：为了防止写入中断导致文件损坏，应先写入 `.tmp` 文件，成功后重命名。
- **版本控制**：通过目录区分不同的切分策略版本，允许对比不同算法的效果。

## 5. 模块结构
```
repository
├── api
│   ├── NovelRepository.java
│   └── SceneRepository.java
├── impl
│   ├── LocalFileNovelRepository.java
│   └── LocalFileSceneRepository.java
└── util
    └── FileUtils.java
```

## 6. 待办事项
- [ ] 确定文件命名规范（处理中文文件名、特殊字符）。
- [ ] 实现文件写入的原子性保障工具类。
- [ ] 考虑大文件的流式读写（虽然小说通常 <10MB，但考虑到百万字级别，内存优化仍有必要）。
