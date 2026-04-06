import { apiClient } from './client';
import type { Scene } from '@/types/api';

export const knowledgeApi = {
  getVersions: async (novelName: string): Promise<string[]> => {
    const response = await apiClient.get<string[]>(`/knowledge/${encodeURIComponent(novelName)}/versions`);
    return response;
  },

  getScenes: async (novelName: string): Promise<Scene[]> => {
    const response = await apiClient.get<Scene[]>(`/knowledge/${encodeURIComponent(novelName)}/scenes`);
    return response;
  },

  deleteVersion: async (novelName: string, version: string): Promise<void> => {
    await apiClient.delete(`/knowledge/${encodeURIComponent(novelName)}/versions/${encodeURIComponent(version)}`);
  },

  deleteKnowledgeBase: async (novelName: string): Promise<void> => {
    await apiClient.delete(`/knowledge/${encodeURIComponent(novelName)}`);
  },
};
