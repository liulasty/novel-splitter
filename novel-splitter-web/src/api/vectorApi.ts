import axios from 'axios';
import type { SystemStats, VectorSearchRequest, VectorRecord } from '@/types/api';

const apiClient = axios.create({
  baseURL: '/api/admin/vector',
});

export const vectorApi = {
  getStats: async (): Promise<SystemStats> => {
    const response = await apiClient.get<SystemStats>('/stats');
    return response.data;
  },

  search: async (request: VectorSearchRequest): Promise<VectorRecord[]> => {
    const response = await apiClient.post<VectorRecord[]>('/search', request);
    return response.data;
  },

  delete: async (filter: Record<string, any>): Promise<void> => {
    await apiClient.delete('/', { data: filter });
  },

  reset: async (): Promise<void> => {
    await apiClient.post('/reset');
  }
};
