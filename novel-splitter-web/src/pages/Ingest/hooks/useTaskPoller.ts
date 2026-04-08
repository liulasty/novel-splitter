import { useState, useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { taskPollApi } from '@/api/taskPollApi';
import { usePollingInterval } from './usePollingInterval';
import type { SplitTask } from '@/api/taskApi';

export function useTaskPoller(initialTasks: SplitTask[] = [], novelId?: string) {
  const [activeTaskIds, setActiveTaskIds] = useState<string[]>([]);
  const [batchedTaskIds, setBatchedTaskIds] = useState<string[]>([]);
  const [errorCount, setErrorCount] = useState(0);
  const queryClient = useQueryClient();

  // Initialize activeTaskIds from initialTasks
  useEffect(() => {
    const activeIds = initialTasks
      .filter(t => t.status === 'PENDING' || t.status === 'PROCESSING')
      .map(t => t.taskId);
    
    // Only update if there are new active tasks to prevent infinite loops
    setActiveTaskIds(prev => {
      const newSet = new Set([...prev, ...activeIds]);
      if (newSet.size !== prev.length) {
        return Array.from(newSet);
      }
      return prev;
    });
  }, [initialTasks]);

  useEffect(() => {
    if (!novelId) return;
    let cancelled = false;
    (async () => {
      const restoredTasks = await taskPollApi.pollByNovelId(novelId);
      if (cancelled || restoredTasks.length === 0) return;

      queryClient.setQueryData<SplitTask[]>(['tasks'], (oldTasks = []) => {
        const merged = [...oldTasks];
        restoredTasks.forEach(task => {
          const idx = merged.findIndex(t => t.taskId === task.taskId);
          if (idx >= 0) {
            merged[idx] = { ...merged[idx], ...task };
          } else {
            merged.push(task);
          }
        });
        return merged;
      });

      const restoredActiveIds = restoredTasks
        .filter(task => task.status === 'PENDING' || task.status === 'PROCESSING')
        .map(task => task.taskId);
      if (restoredActiveIds.length > 0) {
        setActiveTaskIds(prev => Array.from(new Set([...prev, ...restoredActiveIds])));
      }
    })().catch(() => {
      // Recovery poll failure should not block normal polling flow.
    });
    return () => {
      cancelled = true;
    };
  }, [novelId, queryClient]);

  useEffect(() => {
    // 50ms merge window to collapse frequent task id changes.
    const timer = window.setTimeout(() => {
      setBatchedTaskIds(Array.from(new Set(activeTaskIds)));
    }, 50);
    return () => window.clearTimeout(timer);
  }, [activeTaskIds]);

  const getInterval = usePollingInterval(batchedTaskIds.length > 0);

  const {
    data: polledTasks = [],
    refetch,
    isError,
    isSuccess,
  } = useQuery<SplitTask[]>({
    queryKey: ['pollTasks', batchedTaskIds],
    queryFn: () => taskPollApi.pollTasks(batchedTaskIds),
    enabled: batchedTaskIds.length > 0,
    retry: false,
    refetchInterval: (query) => {
      const hasError = query.state.status === 'error';
      const hasProcessing = (query.state.data ?? []).some(t => t.status === 'PROCESSING');
      return getInterval(hasError, hasProcessing) ?? false;
    },
  });

  useEffect(() => {
    if (isError) {
      setErrorCount(prev => prev + 1);
    }
  }, [isError]);

  useEffect(() => {
    if (isSuccess) {
      setErrorCount(0);
    }
  }, [isSuccess]);

  // Update ['tasks'] cache and remove finished tasks from activeTaskIds
  useEffect(() => {
    if (polledTasks.length > 0) {
      // Update the global tasks list
      queryClient.setQueryData<SplitTask[]>(['tasks'], (oldTasks = []) => {
        const updated = [...oldTasks];
        let changed = false;
        
        polledTasks.forEach(pt => {
          const idx = updated.findIndex(t => t.taskId === pt.taskId);
          if (idx !== -1) {
            // Check if status or progress changed to avoid unnecessary rerenders
            if (updated[idx].status !== pt.status || updated[idx].progress !== pt.progress) {
              updated[idx] = { ...updated[idx], ...pt };
              changed = true;
            }
          } else {
            updated.push(pt);
            changed = true;
          }
        });
        
        return changed ? updated : oldTasks;
      });

      // Remove completed tasks from polling
      const finishedIds = polledTasks
        .filter(t => t.status === 'SUCCESS' || t.status === 'FAILED')
        .map(t => t.taskId);
      
      if (finishedIds.length > 0) {
        setActiveTaskIds(prev => prev.filter(id => !finishedIds.includes(id)));
      }
    }
  }, [polledTasks, queryClient]);

  const addActiveTask = (taskId: string) => {
    setActiveTaskIds(prev => Array.from(new Set([...prev, taskId])));
  };

  const latestServerTime = polledTasks.length > 0
    ? Math.max(...polledTasks.map(task => task.updatedAt || Date.now()))
    : Date.now();

  const stuckTaskIds = polledTasks
    .filter(task => latestServerTime - (task.updatedAt || latestServerTime) > 5 * 60 * 1000)
    .map(task => task.taskId);
  const timeoutTaskIds = polledTasks
    .filter(task => latestServerTime - (task.updatedAt || latestServerTime) > 15 * 60 * 1000)
    .map(task => task.taskId);

  return {
    activeTaskIds,
    addActiveTask,
    polledTasks,
    manualRefresh: async () => {
      await refetch();
      setErrorCount(0);
    },
    poller: {
      errorCount,
      isPaused: errorCount >= 5,
      stuckTaskIds,
      timeoutTaskIds,
    },
  };
}
