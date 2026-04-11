import { apiClient, type ApiEnvelope } from './client';
import type { IngestRequest } from '@/types/api';

export interface NovelUploadResponse {
  novelId: string;
  message: string;
}

export interface NovelSummaryDto {
  novelId: string;
  title: string;
  author?: string | null;
  status?: string | null;
  filePath?: string | null;
  createdAt: number;
  updatedAt: number;
}

export interface NovelStatRecordDto {
  novelName: string;
  versions: string[];
  sceneCount: number;
  vectorCount: number;
  ingestTime?: string | null;
  status?: string | null;
}

export interface DashboardStatsDto {
  qaCount: number;
  todayQaCount: number;
  avgRetrievalTimeMs: number;
  retrievalTimeTrend: string;
}

export interface ModelHealthDto {
  embeddingModelLoaded: boolean;
  llmBackendReachable: boolean;
}

export interface NovelSplitRequestDto {
  version: string;
  strategy: string;
  maxTokens: number;
  overlapTokens: number;
}

export interface ChapterTreeDto {
  chapterId: string;
  title: string;
  chapterIndex: number;
}

export interface ScenePreviewDto {
  sceneId: string;
  content: string;
  tokens: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface TaskSubmitResponse {
  taskId: string;
  message: string;
}

export interface NovelPipelineRequestDto {
  stages: Array<'SPLIT' | 'EMBED'>;
  version?: string;
  maxScenes?: number;
}

/** 与 GET /api/novels/summaries?scope= 对齐 */
export type NovelSummaryScope = 'all' | 'embed_ready';

export const novelApi = {
  getDashboardStats: async (): Promise<DashboardStatsDto> => {
    const response = await apiClient.get<ApiEnvelope<DashboardStatsDto>, DashboardStatsDto>('/stats/dashboard');
    return response;
  },

  getModelHealth: async (): Promise<ModelHealthDto> => {
    const response = await apiClient.get<ApiEnvelope<ModelHealthDto>, ModelHealthDto>('/system/health/models');
    return response;
  },

  /**
   * DB 小说摘要列表（按页面职责选用 scope）。
   * - all：入库/运维/知识库总览等
   * - embed_ready：仅向量化已完成，用于 RAG 调试与对话选书
   */
  getNovelSummaries: async (scope: NovelSummaryScope = 'all'): Promise<NovelSummaryDto[]> => {
    const response = await apiClient.get<ApiEnvelope<NovelSummaryDto[]>, NovelSummaryDto[]>(
      '/novels/summaries',
      { params: { scope } }
    );
    return response;
  },

  // Legacy: list local .txt files under storage root
  getNovelFiles: async (): Promise<string[]> => {
    const response = await apiClient.get<ApiEnvelope<string[]>, string[]>('/novels');
    return response;
  },

  getNovelStats: async (): Promise<NovelStatRecordDto[]> => {
    const response = await apiClient.get<ApiEnvelope<NovelStatRecordDto[]>, NovelStatRecordDto[]>('/novels/stats');
    return response;
  },

  uploadNovel: async (file: File): Promise<NovelUploadResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    
    const response = await apiClient.post<ApiEnvelope<NovelUploadResponse>, NovelUploadResponse>('/novels/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response;
  },

  /** 后端 IngestRequest 要求 fileName 非空，占位即可（实际以 novelId 对应 DB 为准） */
  splitNovel: async (
    novelId: string,
    request: { maxScenes?: number; version?: string }
  ): Promise<TaskSubmitResponse> => {
    const response = await apiClient.post<ApiEnvelope<TaskSubmitResponse>, TaskSubmitResponse>(
      `/novels/${encodeURIComponent(novelId)}/split`,
      {
        fileName: 'placeholder.txt',
        maxScenes: request.maxScenes ?? 0,
        version: request.version ?? 'v1',
      }
    );
    return response;
  },

  loadNovel: async (
    novelId: string,
    body?: { version?: string; force?: boolean }
  ): Promise<TaskSubmitResponse> => {
    const response = await apiClient.post<ApiEnvelope<TaskSubmitResponse>, TaskSubmitResponse>(
      `/novels/${encodeURIComponent(novelId)}/load`,
      body ?? {}
    );
    return response;
  },

  embedNovel: async (novelId: string, version?: string): Promise<TaskSubmitResponse> => {
    const response = await apiClient.post<ApiEnvelope<TaskSubmitResponse>, TaskSubmitResponse>(
      `/novels/${encodeURIComponent(novelId)}/embed`,
      undefined,
      version ? { params: { version } } : undefined
    );
    return response;
  },

  triggerPipeline: async (novelId: string, request: NovelPipelineRequestDto): Promise<TaskSubmitResponse> => {
    const response = await apiClient.post<ApiEnvelope<TaskSubmitResponse>, TaskSubmitResponse>(`/novels/${novelId}/pipeline`, request);
    return response;
  },

  getChapters: async (novelId: string): Promise<ChapterTreeDto[]> => {
    const response = await apiClient.get<ApiEnvelope<ChapterTreeDto[]>, ChapterTreeDto[]>(`/novels/${novelId}/chapters`);
    return response;
  },

  getScenes: async (novelId: string, chapterId: string, page = 0, size = 200): Promise<PageResponse<ScenePreviewDto>> => {
    const response = await apiClient.get<ApiEnvelope<PageResponse<ScenePreviewDto>>, PageResponse<ScenePreviewDto>>(
      `/novels/${novelId}/chapters/${chapterId}/scenes`,
      { params: { page, size } }
    );
    return response;
  },

  softDeleteNovel: async (novelId: string): Promise<void> => {
    await apiClient.delete<ApiEnvelope<void>, void>(`/novels/${encodeURIComponent(novelId)}`);
  },

  // Deprecated, keep for backwards compatibility if needed, or replace.
  ingestNovel: async (request: IngestRequest): Promise<TaskSubmitResponse> => {
    const response = await apiClient.post<ApiEnvelope<TaskSubmitResponse>, TaskSubmitResponse>('/novels/ingest', request);
    return response;
  },
};
