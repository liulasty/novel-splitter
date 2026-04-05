import { apiClient } from './client';
import type { SystemStats, VectorSearchRequest, VectorRecord } from '@/types/api';

export const vectorApi = {
  getStats: async (): Promise<SystemStats> => {
    const response = await apiClient.get<SystemStats>('/admin/vector/stats');
    return response.data;
  },

  search: async (request: VectorSearchRequest): Promise<VectorRecord[]> => {
    const response = await apiClient.post<VectorRecord[]>('/admin/vector/search', request);
    return response.data;
  },

  delete: async (filter: Record<string, any>): Promise<void> => {
    await apiClient.delete('/admin/vector/', { data: filter });
  },

  reset: async (): Promise<void> => {
    await apiClient.post('/admin/vector/reset');
  }
};
