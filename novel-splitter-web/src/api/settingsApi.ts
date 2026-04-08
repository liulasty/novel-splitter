import { apiClient, type ApiEnvelope } from './client';

export interface SystemSettingsDto {
  embedding?: Record<string, any>;
  llm?: Record<string, any>;
  chroma?: Record<string, any>;
  splitStrategy?: Record<string, any>;
  [key: string]: any;
}

export const settingsApi = {
  getSettings: async (): Promise<SystemSettingsDto> => {
    const response = await apiClient.get<ApiEnvelope<SystemSettingsDto>, SystemSettingsDto>('/settings');
    return response;
  },

  updateSettings: async (settings: SystemSettingsDto): Promise<{ message: string }> => {
    const response = await apiClient.put<ApiEnvelope<{ message: string }>, { message: string }>('/settings', settings);
    return response;
  }
};
