import { apiClient, type ApiEnvelope } from './client';

export interface ConfigItem {
  id: number | null;
  configKey: string;
  configValue: string;
  category: string;
  description?: string;
  isDefault: boolean;
}

export interface SystemSettingsDto {
  categories: Record<string, ConfigItem[]>;
}

export interface ConfigSaveRequest {
  configKey: string;
  configValue: string;
  category?: string;
  description?: string;
}

export const settingsApi = {
  getSettings: async (): Promise<SystemSettingsDto> => {
    const response = await apiClient.get<ApiEnvelope<SystemSettingsDto>, SystemSettingsDto>('/settings');
    return response;
  },

  saveConfig: async (req: ConfigSaveRequest): Promise<ConfigItem> => {
    const response = await apiClient.post<ApiEnvelope<ConfigItem>, ConfigItem>('/settings', req);
    return response;
  },

  deleteConfig: async (id: number): Promise<void> => {
    await apiClient.delete(`/settings/${id}`);
  },

  deleteConfigByKey: async (key: string): Promise<void> => {
    await apiClient.delete('/settings/key', { params: { key } });
  },
};
