# Validation 层详细设计

## 1. 概述
Validation 层用于在切分完成后、持久化之前，对生成的 Scene 数据进行质量检查。它是保证数据质量的最后一道防线，防止因规则缺陷或特殊文本格式导致生成不可用的数据。

## 2. 校验维度

### 2.1 结构完整性
- **连续性检查**：检查所有 Scene 是否覆盖了全文，没有遗漏段落，也没有重叠段落（除非设计允许重叠）。
- **字段完整性**：检查 Scene 的必要字段（id, text, chapterTitle）是否为空。

### 2.2 业务合理性
- **长度校验**：
  - 警告：Scene < 200 字 或 Scene > 2000 字。
  - 错误：Scene 为空 或 Scene > 5000 字（可能切分失败）。
- **跨章校验**：一个 Scene 原则上不应跨越两个章节（除非特殊设计）。
- **内容质量**：检测是否存在大量乱码或无意义符号。

## 3. 组件设计

### 3.1 Validator 接口
```java
public interface SceneValidator {
    ValidationResult validate(List<Scene> scenes);
}
```

### 3.2 ValidationResult
```java
public class ValidationResult {
    private boolean passed;
    private List<ValidationError> errors;
    private List<ValidationWarning> warnings;
}
```

### 3.3 具体校验器
- `ContinuityValidator`: 验证段落索引连续性。
- `LengthDistributionValidator`: 统计长度分布，发现异常值。
- `ChapterBoundaryValidator`: 确保 Scene 边界与章节边界对齐。

## 4. 模块结构
```
validation
├── api
│   ├── SceneValidator.java
│   └── ValidationResult.java
├── impl
│   ├── ContinuityValidator.java
│   ├── LengthValidator.java
│   └── ChapterBoundaryValidator.java
└── report
    └── ValidationReporter.java (生成可读报告)
```

## 5. 待办事项
- [ ] 定义具体的长度阈值（可配置）。
- [ ] 决定校验失败时的策略（是中断流程，还是仅标记警告）。建议默认策略为：Error 中断，Warning 记录。
