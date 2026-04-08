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
  getVersions: async (novelName: string): Promise<string[]> => {
    const response = await apiClient.get<ApiEnvelope<string[]>, string[]>(`/knowledge/${encodeURIComponent(novelName)}/versions`);
    return response;
  },

  getLightweightScenes: async (page: number = 0, size: number = 20): Promise<PageResponse<VectorPreviewRecordDto>> => {
    const response = await apiClient.get<ApiEnvelope<PageResponse<VectorPreviewRecordDto>>, PageResponse<VectorPreviewRecordDto>>(`/knowledge/scenes/lightweight?page=${page}&size=${size}`);
    return response;
  },

  getScenes: async (novelName: string): Promise<Scene[]> => {
    const response = await apiClient.get<ApiEnvelope<Scene[]>, Scene[]>(`/knowledge/${encodeURIComponent(novelName)}/scenes`);
    return response;
  },

  deleteVersion: async (novelName: string, version: string): Promise<void> => {
    await apiClient.delete(`/knowledge/${encodeURIComponent(novelName)}/versions/${encodeURIComponent(version)}`);
  },

  deleteKnowledgeBase: async (novelName: string): Promise<void> => {
    await apiClient.delete(`/knowledge/${encodeURIComponent(novelName)}`);
  },
};
