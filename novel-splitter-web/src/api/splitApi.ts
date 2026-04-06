import { apiClient } from './client';

export interface SplitPreviewRequestDto {
  sourceText: string;
  strategy?: string;
  maxTokens?: number;
  overlapTokens?: number;
}

export interface ChunkPreviewDto {
  index: number;
  text: string;
  length: number;
  type: string;
}

export const splitApi = {
  previewSplit: async (request: SplitPreviewRequestDto): Promise<ChunkPreviewDto[]> => {
    const response = await apiClient.post<ChunkPreviewDto[]>('/split/preview', request);
    return response;
  }
};
