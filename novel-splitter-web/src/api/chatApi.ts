import { apiClient, type ApiEnvelope } from './client';
import type { ChatRequest, Answer } from '@/types/api';

export const chatApi = {
  sendMessage: async (request: ChatRequest): Promise<Answer> => {
    const response = await apiClient.post<ApiEnvelope<Answer>, Answer>('/chat', request);
    return response;
  },
};
