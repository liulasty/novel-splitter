import { apiClient } from './client';
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

export const novelApi = {
  getDashboardStats: async (): Promise<DashboardStatsDto> => {
    const response = await apiClient.get<DashboardStatsDto>('/stats/dashboard');
    return response;
  },

  getModelHealth: async (): Promise<ModelHealthDto> => {
    const response = await apiClient.get<ModelHealthDto>('/system/health/models');
    return response;
  },

  getNovels: async (): Promise<string[]> => {
    const response = await apiClient.get<string[]>('/novels');
    return response;
  },

  getNovelStats: async (): Promise<NovelStatRecordDto[]> => {
    const response = await apiClient.get<NovelStatRecordDto[]>('/novels/stats');
    return response;
  },

  uploadNovel: async (file: File): Promise<NovelUploadResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    
    const response = await apiClient.post<NovelUploadResponse>('/novels/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response;
  },

  splitNovel: async (novelId: string, request: NovelSplitRequestDto): Promise<{ taskId: string; message: string }> => {
    const response = await apiClient.post<{ taskId: string; message: string }>(`/novels/${novelId}/split`, request);
    return response;
  },

  embedNovel: async (novelId: string): Promise<{ taskId: string; message: string }> => {
    const response = await apiClient.post<{ taskId: string; message: string }>(`/novels/${novelId}/embed`);
    return response;
  },

  getChapters: async (novelId: string): Promise<ChapterTreeDto[]> => {
    const response = await apiClient.get<ChapterTreeDto[]>(`/novels/${novelId}/chapters`);
    return response;
  },

  getScenes: async (novelId: string, chapterId: string): Promise<ScenePreviewDto[]> => {
    const response = await apiClient.get<ScenePreviewDto[]>(`/novels/${novelId}/chapters/${chapterId}/scenes`);
    return response;
  },

  deleteNovel: async (novelId: string): Promise<{ message: string }> => {
    const response = await apiClient.delete<{ message: string }>(`/novels/${novelId}`);
    return response;
  },

  // Deprecated, keep for backwards compatibility if needed, or replace.
  ingestNovel: async (request: IngestRequest): Promise<{ message: string }> => {
    const response = await apiClient.post<{ message: string }>('/novels/ingest', request);
    return response;
  },
};
