import { apiClient, type ApiEnvelope } from './client';
import type { ChatRequest, RagDebugResponse } from '@/types/api';

export const ragApi = {
  debug: async (request: ChatRequest): Promise<RagDebugResponse> => {
    const response = await apiClient.post<ApiEnvelope<RagDebugResponse>, RagDebugResponse>('/v1/rag/debug', request);
    return response;
  },
};
