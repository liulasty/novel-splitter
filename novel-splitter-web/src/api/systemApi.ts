import axios from 'axios';
import type { SystemStats, DeleteRequest } from '@/types/api';

const apiClient = axios.create({
  baseURL: '/api',
});

export const systemApi = {
  getStats: async (): Promise<SystemStats> => {
    const response = await apiClient.get<SystemStats>('/admin/chroma/stats');
    return response.data;
  },

  resetDatabase: async (): Promise<{ message: string }> => {
    const response = await apiClient.post<{ message: string }>('/admin/chroma/reset');
    return response.data;
  },

  deleteByCondition: async (request: DeleteRequest): Promise<{ message: string }> => {
    const response = await apiClient.post<{ message: string }>('/admin/chroma/delete', request);
    return response.data;
  },
};
