import { apiClient, type ApiEnvelope } from './client';
import type { Scene } from '@/types/api';

/** 与后端 SceneSplitProfileDto 一致 */
export interface SceneSplitProfileDto {
  version: string;
  chunkSize: number | null;
  chunkOverlap: number | null;
}

export function splitProfileLabel(p: SceneSplitProfileDto): string {
  if (p.chunkSize != null && p.chunkOverlap != null) {
    return `${p.version} (${p.chunkSize}/${p.chunkOverlap})`;
  }
  return p.version;
}

/** 解析 {@link splitProfileLabel} 生成的展示串 */
export function parseSplitProfileLabel(label: string): { version: string; chunkSize?: number; chunkOverlap?: number } {
  const m = label.trim().match(/^(.+)\s+\((\d+)\/(\d+)\)\s*$/);
  if (m) {
    return { version: m[1].trim(), chunkSize: Number(m[2]), chunkOverlap: Number(m[3]) };
  }
  return { version: label.trim() };
}

export interface VectorPreviewRecordDto {
  id: string;
  novelId: string;
  version: string;
  chapterTitle: string;
  sceneIndex: number;
  text: string;
  // ... other lightweight fields
}

export interface PageResponse<T> {
  content: T[];
  pageable: any;
  totalElements: number;
  totalPages: number;
  last: boolean;
  size: number;
  number: number;
  first: boolean;
  numberOfElements: number;
  empty: boolean;
}

export const knowledgeApi = {
  // Preferred: query by novelId (DB-first)
  getVersionsByNovelId: async (novelId: string): Promise<string[]> => {
    const response = await apiClient.get<ApiEnvelope<string[]>, string[]>(`/knowledge/id/${encodeURIComponent(novelId)}/versions`);
    return response;
  },

  listSplitProfilesByNovelId: async (novelId: string): Promise<SceneSplitProfileDto[]> => {
    const response = await apiClient.get<ApiEnvelope<SceneSplitProfileDto[]>, SceneSplitProfileDto[]>(
      `/knowledge/id/${encodeURIComponent(novelId)}/split-profiles`
    );
    return response;
  },

  // Legacy: query by novelName (kept for backwards compatibility)
  getVersions: async (novelName: string): Promise<string[]> => {
    const response = await apiClient.get<ApiEnvelope<string[]>, string[]>(`/knowledge/${encodeURIComponent(novelName)}/versions`);
    return response;
  },

  getLightweightScenes: async (page: number = 0, size: number = 20): Promise<PageResponse<VectorPreviewRecordDto>> => {
    const response = await apiClient.get<ApiEnvelope<PageResponse<VectorPreviewRecordDto>>, PageResponse<VectorPreviewRecordDto>>(`/knowledge/scenes/lightweight?page=${page}&size=${size}`);
    return response;
  },

  // Preferred: query scenes by novelId
  getScenesByNovelId: async (novelId: string): Promise<Scene[]> => {
    const response = await apiClient.get<ApiEnvelope<Scene[]>, Scene[]>(`/knowledge/id/${encodeURIComponent(novelId)}/scenes`);
    return response;
  },

  // Legacy: query scenes by novelName
  getScenes: async (novelName: string): Promise<Scene[]> => {
    const response = await apiClient.get<ApiEnvelope<Scene[]>, Scene[]>(`/knowledge/${encodeURIComponent(novelName)}/scenes`);
    return response;
  },

  // Preferred: delete version by novelId
  deleteVersionByNovelId: async (
    novelId: string,
    version: string,
    chunkSize: number,
    chunkOverlap: number,
    purgeTerminalSplitTasks?: boolean
  ): Promise<number> => {
    const params = new URLSearchParams({
      chunkSize: String(chunkSize),
      chunkOverlap: String(chunkOverlap),
    });
    if (purgeTerminalSplitTasks) {
      params.set('purgeTerminalSplitTasks', 'true');
    }
    const cleanupTaskId = await apiClient.delete<ApiEnvelope<number>, number>(
      `/knowledge/id/${encodeURIComponent(novelId)}/versions/${encodeURIComponent(version)}?${params.toString()}`
    );
    return cleanupTaskId;
  },

  // Legacy: delete version by novelName
  deleteVersion: async (
    novelName: string,
    version: string,
    chunkSize: number,
    chunkOverlap: number,
    purgeTerminalSplitTasks?: boolean
  ): Promise<number> => {
    const params = new URLSearchParams({
      chunkSize: String(chunkSize),
      chunkOverlap: String(chunkOverlap),
    });
    if (purgeTerminalSplitTasks) {
      params.set('purgeTerminalSplitTasks', 'true');
    }
    const cleanupTaskId = await apiClient.delete<ApiEnvelope<number>, number>(
      `/knowledge/${encodeURIComponent(novelName)}/versions/${encodeURIComponent(version)}?${params.toString()}`
    );
    return cleanupTaskId;
  },

  deleteKnowledgeBase: async (novelName: string, purgeTerminalSplitTasks?: boolean): Promise<number> => {
    const q = purgeTerminalSplitTasks ? '?purgeTerminalSplitTasks=true' : '';
    const cleanupTaskId = await apiClient.delete<ApiEnvelope<number>, number>(
      `/knowledge/${encodeURIComponent(novelName)}${q}`
    );
    return cleanupTaskId;
  },

  deleteKnowledgeBaseById: async (novelId: string, purgeTerminalSplitTasks?: boolean): Promise<number> => {
    const q = purgeTerminalSplitTasks ? '?purgeTerminalSplitTasks=true' : '';
    const cleanupTaskId = await apiClient.delete<ApiEnvelope<number>, number>(
      `/knowledge/id/${encodeURIComponent(novelId)}${q}`
    );
    return cleanupTaskId;
  },
};
