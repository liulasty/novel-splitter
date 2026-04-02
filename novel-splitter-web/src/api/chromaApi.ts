import axios from 'axios';

const api = axios.create({
  baseURL: '/api/admin/chroma',
});

export const chromaApi = {
  getHealthcheck: async () => {
    const response = await api.get('/healthcheck');
    return response.data;
  },

  getVersion: async () => {
    const response = await api.get('/version');
    return response.data;
  },

  getHeartbeat: async () => {
    const response = await api.get('/heartbeat');
    return response.data;
  },

  getCollections: async () => {
    const response = await api.get('/collections');
    return response.data;
  },
};