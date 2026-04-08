import { useState, useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { taskPollApi } from '@/api/taskPollApi';
import { usePollingInterval } from './usePollingInterval';
import type { SplitTask } from '@/api/taskApi';

export function useTaskPoller(initialTasks: SplitTask[] = []) {
  const [activeTaskIds, setActiveTaskIds] = useState<string[]>([]);
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

  const getInterval = usePollingInterval(activeTaskIds.length > 0);

  const { data: polledTasks } = useQuery({
    queryKey: ['pollTasks', activeTaskIds],
    queryFn: () => taskPollApi.pollTasks(activeTaskIds),
    enabled: activeTaskIds.length > 0,
    refetchInterval: (query) => {
      const hasError = query.state.status === 'error';
      return getInterval(hasError) ?? false;
    },
  });

  // Update ['tasks'] cache and remove finished tasks from activeTaskIds
  useEffect(() => {
    if (polledTasks && polledTasks.length > 0) {
      // Update the global tasks list
      queryClient.setQueryData<SplitTask[]>(['tasks'], (oldTasks = []) => {
        const updated = [...oldTasks];
        let changed = false;
        
        polledTasks.forEach(pt => {
          const idx = updated.findIndex(t => t.taskId === pt.taskId);
          if (idx !== -1) {
            // Check if status or progress changed to avoid unnecessary rerenders
            if (updated[idx].status !== pt.status || updated[idx].progress !== pt.progress) {
              updated[idx] = pt;
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

  return { activeTaskIds, addActiveTask, polledTasks };
}
