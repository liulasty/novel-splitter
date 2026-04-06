import { apiClient } from './client';

export const chromaApi = {
  getHealthcheck: async () => {
    const response = await apiClient.get('/admin/chroma/healthcheck');
    return response;
  },

  getVersion: async () => {
    const response = await apiClient.get('/admin/chroma/version');
    return response;
  },

  getHeartbeat: async () => {
    const response = await apiClient.get('/admin/chroma/heartbeat');
    return response;
  },

  getCollections: async () => {
    const response = await apiClient.get('/admin/chroma/collections');
    return response;
  },
};