import { apiClient, type ApiEnvelope } from './client';
import type { SystemStats, VectorSearchRequest, VectorRecord } from '@/types/api';

export const vectorApi = {
  getStats: async (): Promise<SystemStats> => {
    const response = await apiClient.get<ApiEnvelope<SystemStats>, SystemStats>('/admin/vector/stats');
    return response;
  },

  search: async (request: VectorSearchRequest): Promise<VectorRecord[]> => {
    const response = await apiClient.post<ApiEnvelope<VectorRecord[]>, VectorRecord[]>('/admin/vector/search', request);
    return response;
  },

  delete: async (filter: Record<string, any>): Promise<void> => {
    await apiClient.delete('/admin/vector/', { data: filter });
  },

  reset: async (): Promise<void> => {
    await apiClient.post('/admin/vector/reset');
  }
};
