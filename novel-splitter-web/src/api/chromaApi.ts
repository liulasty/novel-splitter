import { apiClient } from './client';

export const chromaApi = {
  getHealthcheck: async () => {
    const response = await apiClient.get('/admin/chroma/healthcheck');
    return response.data;
  },

  getVersion: async () => {
    const response = await apiClient.get('/admin/chroma/version');
    return response.data;
  },

  getHeartbeat: async () => {
    const response = await apiClient.get('/admin/chroma/heartbeat');
    return response.data;
  },

  getCollections: async () => {
    const response = await apiClient.get('/admin/chroma/collections');
    return response.data;
  },
};