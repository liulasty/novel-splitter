import axios from 'axios';
import type { ChatRequest, Answer } from '@/types/api';

const apiClient = axios.create({
  baseURL: '/api', // Proxy will handle this in development
  headers: {
    'Content-Type': 'application/json',
  },
});

export const chatApi = {
  sendMessage: async (request: ChatRequest): Promise<Answer> => {
    const response = await apiClient.post<Answer>('/chat', request);
    return response.data;
  },
};
