import { apiClient, type ApiEnvelope } from './client';

export interface ChromaHealth {
  "nanosecond heartbeat"?: number;
  status?: string;
  error?: string;
}

export interface ChromaVersion {
  version: string;
}

export interface ChromaTenant {
  name: string;
}

export interface ChromaDatabase {
  name: string;
  tenant: string;
}

export interface ChromaCollection {
  id: string;
  name: string;
  metadata: Record<string, any> | null;
  tenant: string;
  database: string;
}

export interface ChromaRecordQuery {
  ids?: string[];
  where?: Record<string, any>;
  where_document?: Record<string, any>;
  limit?: number;
  offset?: number;
  include?: string[];
}

export interface ChromaQueryRequest {
  query_embeddings?: number[][];
  n_results?: number;
  where?: Record<string, any>;
  where_document?: Record<string, any>;
  include?: string[];
  query_texts?: string[];
}

/** 与后端 ChromaVersionDiagnosticDto 对齐 */
export interface ChromaVersionDiagnosticDto {
  dbCount: number;
  chromaCount: number;
  consistent: boolean;
  metadataKeys?: string[];
}

export const chromaAdminApi = {
  // System
  getHealthcheck: async (): Promise<ChromaHealth> => {
    const response = await apiClient.get('/admin/chroma/healthcheck');
    return response;
  },
  getVersion: async (): Promise<ChromaVersion> => {
    const response = await apiClient.get('/admin/chroma/version');
    return response;
  },
  getHeartbeat: async (): Promise<ChromaHealth> => {
    const response = await apiClient.get('/admin/chroma/heartbeat');
    return response;
  },
  getPreFlightChecks: async (): Promise<any> => {
    const response = await apiClient.get('/admin/chroma/pre-flight-checks');
    return response;
  },
  getAuthIdentity: async (): Promise<any> => {
    const response = await apiClient.get('/admin/chroma/auth/identity');
    return response;
  },

  // Tenants & Databases
  getTenants: async (): Promise<ChromaTenant[]> => {
    const response = await apiClient.get('/admin/chroma/tenants');
    return response;
  },
  getDatabases: async (tenant: string): Promise<ChromaDatabase[]> => {
    const response = await apiClient.get(`/admin/chroma/tenants/${tenant}/databases`);
    return response;
  },

  // Collections
  getCollections: async (): Promise<ChromaCollection[]> => {
    const response = await apiClient.get('/admin/chroma/collections');
    return response;
  },
  getCollection: async (id: string): Promise<ChromaCollection> => {
    const response = await apiClient.get(`/admin/chroma/collections/${id}`);
    return response;
  },

  // Records
  getRecords: async (collectionId: string, params: ChromaRecordQuery = {}): Promise<any> => {
    const response = await apiClient.post(`/admin/chroma/collections/${collectionId}/get`, params);
    return response;
  },
  queryRecords: async (collectionId: string, params: ChromaQueryRequest): Promise<any> => {
    const response = await apiClient.post(`/admin/chroma/collections/${collectionId}/query`, params);
    return response;
  },
  countDocuments: async (collectionId: string): Promise<number> => {
    const response = await apiClient.get(`/admin/chroma/collections/${collectionId}/count`);
    return response as number;
  },

  // Diagnostics & Rebuild
  getDiagnostics: async (
    novel: string,
    version: string,
    chunkSize?: number | null,
    chunkOverlap?: number | null
  ): Promise<ChromaVersionDiagnosticDto> => {
    let url = `/admin/chroma/diagnostics?novel=${encodeURIComponent(novel)}&version=${encodeURIComponent(version)}`;
    if (chunkSize != null && chunkOverlap != null) {
      url += `&chunkSize=${encodeURIComponent(String(chunkSize))}&chunkOverlap=${encodeURIComponent(String(chunkOverlap))}`;
    }
    const response = await apiClient.get<ApiEnvelope<ChromaVersionDiagnosticDto>, ChromaVersionDiagnosticDto>(url);
    return response;
  },
  rebuildCollection: async (): Promise<{ message: string }> => {
    const response = await apiClient.post<ApiEnvelope<{ message: string }>, { message: string }>('/admin/chroma/collections/rebuild');
    return response;
  },
  deleteVersion: async (novel: string, version: string): Promise<{ message: string }> => {
    const response = await apiClient.delete<ApiEnvelope<{ message: string }>, { message: string }>(`/admin/chroma/collections/versions?novel=${encodeURIComponent(novel)}&version=${encodeURIComponent(version)}`);
    return response;
  },

  /** 按 metadata 过滤删除 Chroma 文档（与 CleanupWorker / 向量写入字段一致） */
  deleteByMetadataFilter: async (filter: Record<string, unknown>): Promise<{ message: string }> => {
    const response = await apiClient.post<ApiEnvelope<{ message: string }>, { message: string }>(
      '/admin/chroma/delete',
      filter
    );
    return response;
  },
};
