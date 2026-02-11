# 接口设计文档

## 1. 引言
本文档定义前端与后端交互的 RESTful API 接口规范。
基于后端 `com.novel.splitter.application.controller` 包下的控制器实现。

## 2. 接口列表

### 2.1 聊天 (Chat)

#### 发送消息
- **URL**: `/api/chat`
- **Method**: `POST`
- **Content-Type**: `application/json`
- **Request Body**:
  ```json
  {
    "question": "string",
    "novel": "string",   // 小说名称
    "version": "string", // 版本号
    "topK": "number"     // (可选) 引用数量
  }
  ```
- **Response**: `Answer` 对象
  ```json
  {
    "answer": "string",
    "citations": [
      {
        "content": "string",
        "metadata": { ... },
        "score": "number"
      }
    ]
  }
  ```

### 2.2 小说管理 (Novels)

#### 获取小说列表
- **URL**: `/api/novels`
- **Method**: `GET`
- **Response**: `string[]` (文件名列表)

#### 上传小说
- **URL**: `/api/novels/upload`
- **Method**: `POST`
- **Content-Type**: `multipart/form-data`
- **Form Data**:
  - `file`: File
- **Response**:
  ```json
  {
    "message": "string",
    "error": "string" // if failed
  }
  ```

#### 触发入库 (Ingest)
- **URL**: `/api/novels/ingest`
- **Method**: `POST`
- **Content-Type**: `application/json`
- **Request Body**:
  ```json
  {
    "fileName": "string",
    "version": "string",
    "maxScenes": "number" // 0 for all
  }
  ```
- **Response**:
  ```json
  {
    "message": "string"
  }
  ```

### 2.3 知识库管理 (Knowledge Base)

#### 获取版本列表
- **URL**: `/api/knowledge/{novelName}/versions`
- **Method**: `GET`
- **Response**: `string[]` (版本号列表)

#### 获取场景列表
- **URL**: `/api/knowledge/{novelName}/scenes`
- **Method**: `GET`
- **Response**: `Scene[]`

### 2.4 系统管理 (System / Chroma)

#### 获取数据库状态
- **URL**: `/api/admin/chroma/stats`
- **Method**: `GET`
- **Response**:
  ```json
  {
    "count": "number",
    "storeType": "string"
  }
  ```

#### 重置数据库
- **URL**: `/api/admin/chroma/reset`
- **Method**: `POST`
- **Response**:
  ```json
  {
    "message": "string"
  }
  ```

#### 按条件删除
- **URL**: `/api/admin/chroma/delete`
- **Method**: `POST`
- **Request Body**:
  ```json
  {
    "novel": "string" // 过滤条件
  }
  ```
- **Response**:
  ```json
  {
    "message": "string"
  }
  ```

## 3. TypeScript 接口定义 (参考)

```typescript
export interface ChatRequest {
  question: string;
  novel: string;
  version: string;
  topK?: number;
}

export interface Answer {
  answer: string;
  citations: Citation[];
}

export interface Citation {
  content: string;
  score?: number;
  metadata?: Record<string, any>;
}

export interface IngestRequest {
  fileName: string;
  version: string;
  maxScenes: number;
}

export interface SystemStats {
  count: number;
  storeType: string;
}
```
