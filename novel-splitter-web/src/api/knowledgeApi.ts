import axios from 'axios';
import type { Scene } from '@/types/api';

const apiClient = axios.create({
  baseURL: '/api',
});

export const knowledgeApi = {
  getVersions: async (novelName: string): Promise<string[]> => {
    // Note: URL needs to match backend expectation, assuming path parameter
    const response = await apiClient.get<string[]>(`/knowledge/${encodeURIComponent(novelName)}/versions`);
    return response.data;
  },

  getScenes: async (novelName: string): Promise<Scene[]> => {
    const response = await apiClient.get<Scene[]>(`/knowledge/${encodeURIComponent(novelName)}/scenes`);
    return response.data;
  },

  deleteVersion: async (novelName: string, version: string): Promise<void> => {
    await apiClient.delete(`/knowledge/${encodeURIComponent(novelName)}/versions/${encodeURIComponent(version)}`);
  },

  deleteKnowledgeBase: async (novelName: string): Promise<void> => {
    await apiClient.delete(`/knowledge/${encodeURIComponent(novelName)}`);
  },
};
