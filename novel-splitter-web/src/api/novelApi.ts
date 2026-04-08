import { apiClient, type ApiEnvelope } from './client';
import type { IngestRequest } from '@/types/api';

export interface NovelUploadResponse {
  novelId: string;
  message: string;
}

export interface NovelStatRecordDto {
  novelId: string;
  version: string;
  sceneCount: number;
  vectorCount: number;
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

export interface TaskSubmitResponse {
  taskId: string;
  message: string;
}

export const novelApi = {
  getDashboardStats: async (): Promise<DashboardStatsDto> => {
    const response = await apiClient.get<ApiEnvelope<DashboardStatsDto>, DashboardStatsDto>('/stats/dashboard');
    return response;
  },

  getModelHealth: async (): Promise<ModelHealthDto> => {
    const response = await apiClient.get<ApiEnvelope<ModelHealthDto>, ModelHealthDto>('/system/health/models');
    return response;
  },

  getNovels: async (): Promise<string[]> => {
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

  splitNovel: async (novelId: string, request: NovelSplitRequestDto): Promise<TaskSubmitResponse> => {
    const response = await apiClient.post<ApiEnvelope<TaskSubmitResponse>, TaskSubmitResponse>(`/novels/${novelId}/split`, request);
    return response;
  },

  embedNovel: async (novelId: string): Promise<TaskSubmitResponse> => {
    const response = await apiClient.post<ApiEnvelope<TaskSubmitResponse>, TaskSubmitResponse>(`/novels/${novelId}/embed`);
    return response;
  },

  getChapters: async (novelId: string): Promise<ChapterTreeDto[]> => {
    const response = await apiClient.get<ApiEnvelope<ChapterTreeDto[]>, ChapterTreeDto[]>(`/novels/${novelId}/chapters`);
    return response;
  },

  getScenes: async (novelId: string, chapterId: string): Promise<ScenePreviewDto[]> => {
    const response = await apiClient.get<ApiEnvelope<ScenePreviewDto[]>, ScenePreviewDto[]>(`/novels/${novelId}/chapters/${chapterId}/scenes`);
    return response;
  },

  deleteNovel: async (novelId: string): Promise<{ message: string }> => {
    const response = await apiClient.delete<ApiEnvelope<{ message: string }>, { message: string }>(`/novels/${novelId}`);
    return response;
  },

  // Deprecated, keep for backwards compatibility if needed, or replace.
  ingestNovel: async (request: IngestRequest): Promise<TaskSubmitResponse> => {
    const response = await apiClient.post<ApiEnvelope<TaskSubmitResponse>, TaskSubmitResponse>('/novels/ingest', request);
    return response;
  },
};
