// Common interfaces
export interface ApiResponse<T = any> {
  code: number;
  message: string;
  data: T;
}

// Chat related interfaces
export interface ChatRequest {
  question: string;
  novelId: string;
  version: string;
  topK?: number;
  /** 与后端 Scene 分区一致；同一 version 多数据集时建议始终发送 */
  chunkSize?: number | null;
  chunkOverlap?: number | null;
  /** 上下文场景数上限 */
  maxScenes?: number;
  /** 上下文 Token 预算 */
  maxContextTokens?: number;
  /** 回答目标字数 */
  maxAnswerTokens?: number;
}

export interface Citation {
  content: string;
  chapterPosition?: string;
  score?: number;
  metadata?: Record<string, any>;
}

export interface Answer {
  answer: string;
  citations: Citation[];
}

// Novel & Ingest related interfaces
export interface IngestRequest {
  fileName: string;
  version: string;
  maxScenes: number; // 0 for all
}

export interface NovelUploadResponse {
  message: string;
  novelId: string;
}

// Scene related interfaces
export interface Scene {
  id?: string;
  content: string;
  metadata?: Record<string, any>;
}

// System related interfaces
export interface SystemStats {
  count: number;
  type: string;
}

export interface VectorSearchRequest {
  query: string;
  topK?: number;
  filter?: Record<string, any>;
}

export interface VectorRecord {
  chunkId: string;
  score: number;
}

// RAG Debug related interfaces
export interface ContextBlock {
  chunkId: string;
  content: string;
  sceneMetadata?: Record<string, any>;
  tokenCount: number;
  rank?: number;
  score: number;
  metadata?: Record<string, any>;
}

export interface Prompt {
  systemInstruction: string;
  userQuestion: string;
  contextBlocks: ContextBlock[];
  outputConstraint: string;
}

export interface RagDebugResponse {
  retrievedScenes: Scene[];
  contextBlocks: ContextBlock[];
  finalPrompt: Prompt;
  stats: Record<string, any>;
}
