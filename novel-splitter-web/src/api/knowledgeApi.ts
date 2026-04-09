import { apiClient, type ApiEnvelope } from './client';
import type { Scene } from '@/types/api';

export interface VectorPreviewRecordDto {
  id: string;
  novelId: string;
  version: string;
  chapterTitle: string;
  sceneIndex: number;
  text: string;
  // ... other lightweight fields
}

export interface PageResponse<T> {
  content: T[];
  pageable: any;
  totalElements: number;
  totalPages: number;
  last: boolean;
  size: number;
  number: number;
  first: boolean;
  numberOfElements: number;
  empty: boolean;
}

export const knowledgeApi = {
  // Preferred: query by novelId (DB-first)
  getVersionsByNovelId: async (novelId: string): Promise<string[]> => {
    const response = await apiClient.get<ApiEnvelope<string[]>, string[]>(`/knowledge/id/${encodeURIComponent(novelId)}/versions`);
    return response;
  },

  // Legacy: query by novelName (kept for backwards compatibility)
  getVersions: async (novelName: string): Promise<string[]> => {
    const response = await apiClient.get<ApiEnvelope<string[]>, string[]>(`/knowledge/${encodeURIComponent(novelName)}/versions`);
    return response;
  },

  getLightweightScenes: async (page: number = 0, size: number = 20): Promise<PageResponse<VectorPreviewRecordDto>> => {
    const response = await apiClient.get<ApiEnvelope<PageResponse<VectorPreviewRecordDto>>, PageResponse<VectorPreviewRecordDto>>(`/knowledge/scenes/lightweight?page=${page}&size=${size}`);
    return response;
  },

  // Preferred: query scenes by novelId
  getScenesByNovelId: async (novelId: string): Promise<Scene[]> => {
    const response = await apiClient.get<ApiEnvelope<Scene[]>, Scene[]>(`/knowledge/id/${encodeURIComponent(novelId)}/scenes`);
    return response;
  },

  // Legacy: query scenes by novelName
  getScenes: async (novelName: string): Promise<Scene[]> => {
    const response = await apiClient.get<ApiEnvelope<Scene[]>, Scene[]>(`/knowledge/${encodeURIComponent(novelName)}/scenes`);
    return response;
  },

  // Preferred: delete version by novelId
  deleteVersionByNovelId: async (novelId: string, version: string): Promise<number> => {
    const cleanupTaskId = await apiClient.delete<ApiEnvelope<number>, number>(
      `/knowledge/id/${encodeURIComponent(novelId)}/versions/${encodeURIComponent(version)}`
    );
    return cleanupTaskId;
  },

  // Legacy: delete version by novelName
  deleteVersion: async (novelName: string, version: string): Promise<number> => {
    const cleanupTaskId = await apiClient.delete<ApiEnvelope<number>, number>(
      `/knowledge/${encodeURIComponent(novelName)}/versions/${encodeURIComponent(version)}`
    );
    return cleanupTaskId;
  },

  deleteKnowledgeBase: async (novelName: string): Promise<number> => {
    const cleanupTaskId = await apiClient.delete<ApiEnvelope<number>, number>(`/knowledge/${encodeURIComponent(novelName)}`);
    return cleanupTaskId;
  },

  deleteKnowledgeBaseById: async (novelId: string): Promise<number> => {
    const cleanupTaskId = await apiClient.delete<ApiEnvelope<number>, number>(`/knowledge/id/${encodeURIComponent(novelId)}`);
    return cleanupTaskId;
  },
};
