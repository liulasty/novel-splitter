import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { knowledgeApi } from '@/api/knowledgeApi';

const SESSION_PREFIX = 'kb:version:';

/**
 * 共享版本状态：URL `?version=` 为事实源。
 * 解析优先级：URL（存在且有效）> 显式选择 > sessionStorage（按 novelId 隔离、校验有效）> 最新 profile > "v1"。
 * - URL 中有效的 version 始终被采纳（含浏览器前进/后退、手动改地址），即使当前是显式选择。
 * - 显式选择（setVersion）写 URL + sessionStorage，且优先于 session/最新，直到换小说或 URL 变化。
 * - 自动发现（latest / v1）不写 URL/session，发现器刷新出现更新版本时自动升级。
 * - URL 携带不存在的 version（如 v99）→ 若用户已显式选择则保留，否则降级到最新有效版本。
 * - 已知限制：显式选择的版本被删除后无法与"待生成的新版本"区分，暂不自动重解析。
 */
export function useSplitVersion(novelId: string | undefined) {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();

  const [version, setVersionState] = useState<string>('');
  const originRef = useRef<'explicit' | 'auto'>('auto');
  const novelRef = useRef<string | undefined>(undefined);

  const { data: profiles = [], isPending: isDiscovering, isError } = useQuery({
    queryKey: ['splitProfiles', novelId],
    queryFn: () => knowledgeApi.listSplitProfilesByNovelId(novelId as string),
    enabled: !!novelId,
  });

  const writeUrl = useCallback((v: string) => {
    setSearchParams((prev) => {
      const p = new URLSearchParams(prev);
      p.set('version', v);
      return p;
    }, { replace: true });
  }, [setSearchParams]);

  const clearUrlVersion = useCallback(() => {
    setSearchParams((prev) => {
      if (!prev.get('version')) return prev;
      const p = new URLSearchParams(prev);
      p.delete('version');
      return p;
    }, { replace: true });
  }, [setSearchParams]);

  // 首次挂载保留深链 ?version=；仅当真实切换小说（前一 novelId 非空）时清掉残留 version 参数与旧 session。
  useEffect(() => {
    if (novelRef.current === novelId) return;
    const prevNovel = novelRef.current;
    novelRef.current = novelId;
    originRef.current = 'auto';
    setVersionState('');
    if (prevNovel && prevNovel !== novelId) {
      clearUrlVersion();
      try { sessionStorage.removeItem(SESSION_PREFIX + prevNovel); } catch { /* ignore */ }
    }
  }, [novelId, clearUrlVersion]);

  // 解析版本：URL 有效则始终采纳；否则保留显式选择（含待生成的新版本）；再否则降级到 v1 / session / 最新。
  useEffect(() => {
    if (!novelId) { setVersionState(''); return; }
    if (isDiscovering) return;

    const exists = (v: string) => profiles.some((p) => p.version === v);
    const urlVersion = searchParams.get('version')?.trim();
    const urlValid = !!urlVersion && (profiles.length === 0 || exists(urlVersion));

    if (urlVersion && urlValid) {
      if (urlVersion !== version) {
        setVersionState(urlVersion);
        originRef.current = 'explicit';
        if (!isError) {
          try { sessionStorage.setItem(SESSION_PREFIX + novelId, urlVersion); } catch { /* ignore */ }
        }
      }
      return;
    }

    if (originRef.current === 'explicit') return;

    if (isError) {
      if (version !== 'v1') setVersionState('v1');
      originRef.current = 'auto';
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
    // 后端 /split-profiles 按 MAX(id) 排序（旧→新），末位 = 最新。
    const latest = profiles[profiles.length - 1]?.version;
    if (latest) {
      if (latest !== version) setVersionState(latest);
      originRef.current = 'auto';
      return;
    }
    if (version !== 'v1') setVersionState('v1');
    originRef.current = 'auto';
  }, [novelId, profiles, isDiscovering, isError, searchParams, writeUrl, version]);

  const setVersion = useCallback((v: string) => {
    const t = (v ?? '').trim();
    if (!t) {
      setVersionState('');
      originRef.current = 'auto';
      clearUrlVersion();
      if (novelId) {
        try { sessionStorage.removeItem(SESSION_PREFIX + novelId); } catch { /* ignore */ }
      }
      return;
    }
    setVersionState(t);
    originRef.current = 'explicit';
    writeUrl(t);
    if (novelId) {
      try { sessionStorage.setItem(SESSION_PREFIX + novelId, t); } catch { /* ignore */ }
    }
  }, [novelId, writeUrl, clearUrlVersion]);

  const currentProfile = profiles.find((p) => p.version === version);
  const latestVersion = profiles.length > 0 ? profiles[profiles.length - 1].version : undefined;

  const refresh = useCallback(() => {
    if (novelId) queryClient.invalidateQueries({ queryKey: ['splitProfiles', novelId] });
  }, [queryClient, novelId]);

  return { version, setVersion, profiles, currentProfile, latestVersion, isDiscovering, refresh };
}
