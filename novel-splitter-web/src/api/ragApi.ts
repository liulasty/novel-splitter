import axios from 'axios';
import type { ChatRequest, RagDebugResponse } from '@/types/api';

const apiClient = axios.create({
  baseURL: '/api/v1',
});

export const ragApi = {
  debug: async (request: ChatRequest): Promise<RagDebugResponse> => {
    const response = await apiClient.post<RagDebugResponse>('/rag/debug', request);
    return response.data;
  },
};
