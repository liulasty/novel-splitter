import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { AlertTriangle, ArrowLeft, Loader2, RefreshCw } from 'lucide-react';
import { toast } from 'sonner';
import { dlqApi, type DlqStat } from '@/api/dlqApi';
import { getApiErrorMessage } from '@/lib/apiError';
import { cn } from '@/lib/utils';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

export default function DlqMonitorPanel() {
  const queryClient = useQueryClient();

  const { data: stats = [], isLoading, isFetching } = useQuery({
    queryKey: ['dlq-stats'],
    queryFn: dlqApi.getStats,
    refetchInterval: 8000,
  });

  const requeueMutation = useMutation({
    mutationFn: ({ queueName }: { queueName: string }) => dlqApi.requeue(queueName),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['dlq-stats'] });
      toast.success(`已重投 ${res.requeued} 条，队列剩余约 ${res.remaining} 条`);
    },
    onError: (error: unknown) => {
      toast.error(getApiErrorMessage(error, '重投失败'));
    },
  });

  const totalBacklog = stats.reduce((sum, s) => sum + (s.messageCount > 0 ? s.messageCount : 0), 0);

  return (
    <div className="flex flex-col gap-6 max-w-4xl mx-auto">
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold bg-gradient-to-r from-rose-600 via-orange-600 to-amber-600 bg-clip-text text-transparent">
            异常队列监控
          </h1>
          <p className="text-sm text-gray-500 mt-1.5 leading-relaxed">
            查看 RabbitMQ 死信队列积压；修复根因后可一键重投回主任务队列（Worker 幂等清理可安全重复消费）。
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link
            to="/tasks"
            className="inline-flex items-center gap-2 h-9 px-4 rounded-full text-sm font-medium text-gray-700 bg-white border border-gray-200 hover:bg-gray-50 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            任务监控
          </Link>
          <button
            type="button"
            onClick={() => queryClient.invalidateQueries({ queryKey: ['dlq-stats'] })}
            className="inline-flex items-center gap-2 h-9 px-4 rounded-full text-sm font-medium text-gray-700 bg-white border border-gray-200 hover:bg-gray-50 transition-colors"
          >
            <RefreshCw className={cn('w-4 h-4', isFetching && 'animate-spin')} />
            刷新
          </button>
        </div>
      </div>

      {totalBacklog > 0 && (
        <div className="rounded-2xl border border-amber-200 bg-amber-50/80 px-4 py-3 flex items-start gap-3 text-sm text-amber-900">
          <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5 text-amber-600" />
          <div>
            <p className="font-medium">检测到 DLQ 积压</p>
            <p className="text-amber-800/90 mt-0.5">
              请先排除下游故障（向量库、大模型、数据库等），再对对应队列执行「一键重试」。
            </p>
          </div>
        </div>
      )}

      <Card className="border-gray-200/80 shadow-sm">
        <CardHeader className="pb-3">
          <CardTitle className="text-lg">死信队列</CardTitle>
          <CardDescription>约每 8 秒自动刷新；单键重投单次最多 10000 条，可多次点击直至清空。</CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="flex items-center gap-2 text-sm text-gray-500 py-8 justify-center">
              <Loader2 className="w-5 h-5 animate-spin" />
              加载中…
            </div>
          ) : (
            <div className="overflow-x-auto rounded-xl border border-gray-100">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-gray-50/80 text-left text-gray-600">
                    <th className="px-4 py-3 font-medium">队列</th>
                    <th className="px-4 py-3 font-medium">重投 routing key</th>
                    <th className="px-4 py-3 font-medium text-right">积压消息数</th>
                    <th className="px-4 py-3 font-medium text-right w-40">操作</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {stats.map((row: DlqStat) => (
                    <tr key={row.queueName} className="bg-white hover:bg-gray-50/50 transition-colors">
                      <td className="px-4 py-3 font-mono text-xs text-gray-800">{row.queueName}</td>
                      <td className="px-4 py-3 font-mono text-xs text-blue-700">{row.targetRoutingKey}</td>
                      <td
                        className={cn(
                          'px-4 py-3 text-right tabular-nums font-medium',
                          row.messageCount > 0 ? 'text-rose-600' : 'text-gray-500'
                        )}
                      >
                        {row.messageCount < 0 ? '—' : row.messageCount}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <button
                          type="button"
                          disabled={row.messageCount === 0 || requeueMutation.isPending}
                          onClick={() => requeueMutation.mutate({ queueName: row.queueName })}
                          className={cn(
                            'inline-flex items-center justify-center rounded-full px-3 py-1.5 text-xs font-semibold transition-colors',
                            row.messageCount > 0
                              ? 'bg-rose-600 text-white hover:bg-rose-700 disabled:opacity-50'
                              : 'bg-gray-100 text-gray-400 cursor-not-allowed'
                          )}
                        >
                          {requeueMutation.isPending ? (
                            <Loader2 className="w-3.5 h-3.5 animate-spin" />
                          ) : (
                            '一键重试'
                          )}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
