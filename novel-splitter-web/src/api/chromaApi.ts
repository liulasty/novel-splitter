import { apiClient, type ApiEnvelope } from './client';

interface ChromaHealthResponse {
  [key: string]: unknown;
}

interface ChromaVersionResponse {
  version: string;
}

interface ChromaCollectionResponse {
  [key: string]: unknown;
}

export const chromaApi = {
  getHealthcheck: async () => {
    const response = await apiClient.get<ApiEnvelope<ChromaHealthResponse>, ChromaHealthResponse>('/admin/chroma/healthcheck');
    return response;
  },

  getVersion: async () => {
    const response = await apiClient.get<ApiEnvelope<ChromaVersionResponse>, ChromaVersionResponse>('/admin/chroma/version');
    return response;
  },

  getHeartbeat: async () => {
    const response = await apiClient.get<ApiEnvelope<ChromaHealthResponse>, ChromaHealthResponse>('/admin/chroma/heartbeat');
    return response;
  },

  getCollections: async () => {
    const response = await apiClient.get<ApiEnvelope<ChromaCollectionResponse>, ChromaCollectionResponse>('/admin/chroma/collections');
    return response;
  },
};