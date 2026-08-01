import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { knowledgeApi } from '@/api/knowledgeApi';

const SESSION_PREFIX = 'kb:version:';

/**
 * 共享版本状态：URL `?version=` 唯一事实源。
 * 解析优先级：URL（校验存在）> sessionStorage（按 novelId 隔离、校验存在）> 最新 profile > "v1"。
 * - 显式选择（setVersion / URL / session）永不自动覆盖，直到切换小说。
 * - 自动发现（latest / v1）不写 URL/session，发现器刷新出现更新版本时自动升级。
 * - URL 携带不存在的 version（如 v99）→ 降级到最新有效版本。
 */
export function useSplitVersion(novelId: string | undefined) {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();

  const [version, setVersionState] = useState<string>('');
  const originRef = useRef<'explicit' | 'auto'>('auto');
  const novelRef = useRef<string | undefined>(undefined);

  const { data: profiles = [], isPending: isDiscovering } = useQuery({
    queryKey: ['splitProfiles', novelId],
    queryFn: () => knowledgeApi.listSplitProfilesByNovelId(novelId as string),
    enabled: !!novelId,
  });

  const writeUrl = useCallback((v: string) => {
    setSearchParams(
      (prev) => {
        const p = new URLSearchParams(prev);
        p.set('version', v);
        return p;
      },
      { replace: true }
    );
  }, [setSearchParams]);

  // 切换小说：清空版本与来源，触发重新解析。
  useEffect(() => {
    if (novelRef.current !== novelId) {
      novelRef.current = novelId;
      originRef.current = 'auto';
      setVersionState('');
    }
  }, [novelId]);

  // 解析版本（仅当当前版本非显式选择，且 profiles 已加载以便校验）。
  useEffect(() => {
    if (!novelId) { setVersionState(''); return; }
    if (originRef.current === 'explicit') return;
    if (isDiscovering) return;

    const exists = (v: string) => profiles.some((p) => p.version === v);
    const urlVersion = searchParams.get('version')?.trim();

    if (urlVersion && (profiles.length === 0 || exists(urlVersion))) {
      setVersionState(urlVersion);
      originRef.current = 'explicit';
      try { sessionStorage.setItem(SESSION_PREFIX + novelId, urlVersion); } catch { /* ignore */ }
      return;
    }
    let sessionVersion: string | null = null;
    try { sessionVersion = sessionStorage.getItem(SESSION_PREFIX + novelId)?.trim() ?? null; } catch { /* ignore */ }
    if (sessionVersion && exists(sessionVersion)) {
      setVersionState(sessionVersion);
      originRef.current = 'explicit';
      writeUrl(sessionVersion);
      return;
    }
    const latest = profiles[profiles.length - 1]?.version;
    if (latest) {
      setVersionState(latest);
      originRef.current = 'auto';
      return;
    }
    setVersionState('v1');
    originRef.current = 'auto';
  }, [novelId, profiles, isDiscovering, searchParams, writeUrl]);

  const setVersion = useCallback((v: string) => {
    const t = (v ?? '').trim();
    setVersionState(t);
    originRef.current = 'explicit';
    if (!t || !novelId) return;
    writeUrl(t);
    try { sessionStorage.setItem(SESSION_PREFIX + novelId, t); } catch { /* ignore */ }
  }, [novelId, writeUrl]);

  const currentProfile = profiles.find((p) => p.version === version);
  const latestVersion = profiles.length > 0 ? profiles[profiles.length - 1].version : undefined;

  const refresh = useCallback(() => {
    if (novelId) queryClient.invalidateQueries({ queryKey: ['splitProfiles', novelId] });
  }, [queryClient, novelId]);

  return { version, setVersion, profiles, currentProfile, latestVersion, isDiscovering, refresh };
}
