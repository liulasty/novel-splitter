// Common interfaces
export interface ApiResponse<T = any> {
  message?: string;
  error?: string;
  data?: T;
}

// Chat related interfaces
export interface ChatRequest {
  question: string;
  novel: string;
  version: string;
  topK?: number;
}

export interface Citation {
  content: string;
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
  error?: string;
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
  storeType: string;
}

export interface DeleteRequest {
  novel: string;
}
