import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { knowledgeApi } from '@/api/knowledgeApi';
import { novelApi } from '@/api/novelApi';

/**
 * 共享版本状态：URL `?version=` 为事实源（版本选择不再持久化到 sessionStorage）。
 * 解析优先级：URL（存在且有效）> 显式选择 > 最新 profile > "v1"。
 * - URL 中有效的 version 始终被采纳（含浏览器前进/后退、手动改地址），即使当前是显式选择。
 * - 显式选择（setVersion）写 URL，优先于最新，直到换小说或 URL 变化。
 * - 自动发现（latest / v1）不写 URL，发现器刷新出现更新版本时自动升级。
 * - URL 携带不存在的 version（如 v99）→ 若用户已显式选择则保留，否则降级到最新有效版本。
 * - 已知限制：显式选择的版本被删除后无法与"待生成的新版本"区分，暂不自动重解析。
 */
export function useSplitVersion(novelId: string | undefined) {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();

  const [version, setVersionState] = useState<string>('');
  const originRef = useRef<'explicit' | 'auto'>('auto');
  const novelRef = useRef<string | undefined>(undefined);
  const switchPendingRef = useRef(false);

  // 有效性门控：novelId 指向已删/不存在的小说不发 split-profiles 请求（避免 400）。查询 key 复用全局缓存。
  const { data: novelOptions = [] } = useQuery({
    queryKey: ['novelSummaries', 'all'],
    queryFn: () => novelApi.getNovelSummaries('all'),
  });
  const novelValid = Boolean(novelId && novelOptions.some((n) => n.novelId === novelId));

  const { data: profiles = [], isPending: isDiscovering, isError } = useQuery({
    queryKey: ['splitProfiles', novelId],
    queryFn: () => knowledgeApi.listSplitProfilesByNovelId(novelId as string),
    enabled: novelValid,
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
    // eslint-disable-next-line react-hooks/set-state-in-effect -- 切换小说时刻意重置版本状态
    setVersionState('');
    if (prevNovel && prevNovel !== novelId) {
      switchPendingRef.current = true;
      clearUrlVersion();
    }
  }, [novelId, clearUrlVersion]);

  // 解析版本：URL 有效则始终采纳；否则保留显式选择（含待生成的新版本）；再否则降级到 v1 / session / 最新。
  useEffect(() => {
    if (!novelId) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- novelId 清空时刻意重置版本状态
      setVersionState('');
      return;
    }
    if (isDiscovering) return;

    const exists = (v: string) => profiles.some((p) => p.version === v);
    const urlVersion = searchParams.get('version')?.trim();
    const urlValid = !!urlVersion && (profiles.length === 0 || exists(urlVersion));

    if (urlVersion && urlValid && !switchPendingRef.current) {
      if (urlVersion !== version) {
        setVersionState(urlVersion);
        originRef.current = 'explicit';
      }
      return;
    }
    // 切换小说后的首个解析窗口：忽略上个小说残留的 URL 版本，改走最新/兜底。
    switchPendingRef.current = false;

    if (originRef.current === 'explicit') return;

    if (isError) {
      if (version !== 'v1') setVersionState('v1');
      originRef.current = 'auto';
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
  }, [novelId, profiles, isDiscovering, isError, searchParams, version]);

  const setVersion = useCallback((v: string) => {
    const t = (v ?? '').trim();
    if (!t) {
      setVersionState('');
      originRef.current = 'auto';
      clearUrlVersion();
      return;
    }
    setVersionState(t);
    originRef.current = 'explicit';
    writeUrl(t);
  }, [writeUrl, clearUrlVersion]);

  const currentProfile = profiles.find((p) => p.version === version);
  const latestVersion = profiles.length > 0 ? profiles[profiles.length - 1].version : undefined;

  const refresh = useCallback(() => {
    if (novelId) queryClient.invalidateQueries({ queryKey: ['splitProfiles', novelId] });
  }, [queryClient, novelId]);

  return { version, setVersion, profiles, currentProfile, latestVersion, isDiscovering, refresh };
}
