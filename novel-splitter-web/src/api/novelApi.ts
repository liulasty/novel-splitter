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
  novelId?: string;
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

/** 与后端 ChapterDto JSON 对齐 */
export interface NovelChapterDto {
  id: number;
  novelId: string;
  index: number;
  title: string;
  startParagraphIndex: number;
  endParagraphIndex: number;
  wordCount: number;
  paragraphCount: number;
  volumeTitle?: string | null;
  originalTitle?: string | null;
}

/** @deprecated 使用 NovelChapterDto */
export type ChapterTreeDto = NovelChapterDto;

/** 与后端 SceneDto JSON 对齐（章节下切分片段） */
export interface SceneDto {
  id: string;
  chapterTitle: string;
  chapterIndex: number;
  startParagraphIndex: number;
  endParagraphIndex: number;
  text: string;
  wordCount: number;
  prefixContext?: string | null;
  canSplit: boolean;
  metadata?: Record<string, unknown> | null;
  score?: number | null;
}

/**
 * 与后端 domain PagedResult 对齐（非 Spring Data Page）。
 * 部分接口仍返回 Spring Page，请用 {@link PageResponse}。
 */
export interface DomainPagedResult<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
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

export type SplitEntry = 'FULL' | 'CHAPTER_RELOAD' | 'SCENE_ONLY';

export interface NovelPipelineRequestDto {
  stages: Array<'SPLIT' | 'EMBED'>;
  version?: string;
  maxScenes?: number;
  /** 与后端 NovelPipelineRequestDto.splitEntry 对齐 */
  splitEntry?: SplitEntry;
  chunkSize?: number;
  chunkOverlap?: number;
  chapterTitleRegex?: string;
}

export interface ChapterStrategy {
  key: string;
  label: string;
  description: string;
}

export interface ReparseChaptersRequest {
  version?: string;
  chapterTitleRegex?: string;
  strategy?: string;
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
    request: { maxScenes?: number; version?: string; chapterTitleRegex?: string; strategy?: string }
  ): Promise<TaskSubmitResponse> => {
    const response = await apiClient.post<ApiEnvelope<TaskSubmitResponse>, TaskSubmitResponse>(
      `/novels/${encodeURIComponent(novelId)}/split`,
      {
        fileName: 'placeholder.txt',
        maxScenes: request.maxScenes ?? 0,
        version: request.version ?? 'v1',
        ...(request.chapterTitleRegex != null && request.chapterTitleRegex !== ''
          ? { chapterTitleRegex: request.chapterTitleRegex }
          : {}),
        ...(request.strategy != null && request.strategy !== ''
          ? { strategy: request.strategy }
          : {}),
      }
    );
    return response;
  },

  reparseChapters: async (
    novelId: string,
    body?: ReparseChaptersRequest
  ): Promise<TaskSubmitResponse> => {
    const response = await apiClient.post<ApiEnvelope<TaskSubmitResponse>, TaskSubmitResponse>(
      `/novels/${encodeURIComponent(novelId)}/re-parse-chapters`,
      body ?? {}
    );
    return response;
  },

  loadNovel: async (
    novelId: string,
    body?: { version?: string; force?: boolean; chapterTitleRegex?: string; strategy?: string }
  ): Promise<TaskSubmitResponse> => {
    const response = await apiClient.post<ApiEnvelope<TaskSubmitResponse>, TaskSubmitResponse>(
      `/novels/${encodeURIComponent(novelId)}/load`,
      body ?? {}
    );
    return response;
  },

  listChapterStrategies: async (): Promise<ChapterStrategy[]> => {
    const response = await apiClient.get<ApiEnvelope<ChapterStrategy[]>, ChapterStrategy[]>(
      '/novels/chapter-strategies'
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

  /**
   * 场景切分（Split 队列），需已完成章节解析。
   * 后端按 (novelId, version) 分区：先删该 version 旧场景再写入多条 Scene；换 chunk 规则若要并存请改 version。
   */
  sceneSplit: async (
    novelId: string,
    body: {
      version?: string;
      maxScenes?: number;
      chunkSize?: number;
      chunkOverlap?: number;
      triggerEmbed?: boolean;
    }
  ): Promise<TaskSubmitResponse> => {
    const response = await apiClient.post<ApiEnvelope<TaskSubmitResponse>, TaskSubmitResponse>(
      `/novels/${encodeURIComponent(novelId)}/scene-split`,
      body
    );
    return response;
  },

  getChapters: async (novelId: string): Promise<NovelChapterDto[]> => {
    const response = await apiClient.get<ApiEnvelope<NovelChapterDto[]>, NovelChapterDto[]>(
      `/novels/${novelId}/chapters`
    );
    return response;
  },

  getScenes: async (novelId: string, chapterId: string, page = 0, size = 200): Promise<DomainPagedResult<SceneDto>> => {
    const response = await apiClient.get<ApiEnvelope<DomainPagedResult<SceneDto>>, DomainPagedResult<SceneDto>>(
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
