import { Clock, Loader2, CheckCircle, AlertCircle, Activity, PlayCircle, XCircle } from "lucide-react";
import { TaskItem } from "@/components/TaskItem";
import { TaskDetailDrawer } from "@/components/TaskDetailDrawer";
import { taskApi, SplitTask } from "@/api/taskApi";
import { useQuery } from "@tanstack/react-query";

const STATUS_CONFIG = {
    PENDING:    { label: 'PENDING',    pill: 'bg-gray-100 text-gray-600',    bar: 'bg-gray-400',  Icon: Clock },
    PROCESSING: { label: 'PROCESSING', pill: 'bg-blue-100 text-blue-700',    bar: 'bg-gradient-to-r from-violet-500 to-blue-500', Icon: Loader2 },
    SUCCESS:    { label: 'SUCCESS',    pill: 'bg-green-100 text-green-700',   bar: 'bg-gradient-to-r from-teal-500 to-green-500',  Icon: CheckCircle },
    FAILED:     { label: 'FAILED',     pill: 'bg-red-100 text-red-700',       bar: 'bg-red-500',   Icon: AlertCircle },
} as const;

interface TaskQueueBoardProps {
    tasks: SplitTask[];
    selectedTaskId: string | null;
    actions: {
        deleteTask: (id: string) => void;
        setSelectedTaskId: (id: string | null) => void;
    };
}

export function TaskQueueBoard({ tasks, selectedTaskId, actions }: TaskQueueBoardProps) {
    const { data: stats } = useQuery({
        queryKey: ['jobStats'],
        queryFn: taskApi.getJobStats,
        refetchInterval: 5000,
    });

    return (
        <div className="space-y-4">
            {/* Global Queue Stats Dashboard */}
            <div className="grid grid-cols-4 gap-4">
                <div className="bg-white rounded-xl p-4 border border-blue-100 shadow-sm">
                    <div className="flex items-center gap-2 text-blue-600 mb-2">
                        <PlayCircle className="w-4 h-4" />
                        <span className="text-xs font-bold uppercase tracking-wider">运行中</span>
                    </div>
                    <div className="text-2xl font-bold text-gray-900">{stats?.running ?? '-'}</div>
                </div>
                <div className="bg-white rounded-xl p-4 border border-amber-100 shadow-sm">
                    <div className="flex items-center gap-2 text-amber-600 mb-2">
                        <Clock className="w-4 h-4" />
                        <span className="text-xs font-bold uppercase tracking-wider">等待中</span>
                    </div>
                    <div className="text-2xl font-bold text-gray-900">{stats?.waiting ?? '-'}</div>
                </div>
                <div className="bg-white rounded-xl p-4 border border-green-100 shadow-sm">
                    <div className="flex items-center gap-2 text-green-600 mb-2">
                        <CheckCircle className="w-4 h-4" />
                        <span className="text-xs font-bold uppercase tracking-wider">今日完成</span>
                    </div>
                    <div className="text-2xl font-bold text-gray-900">{stats?.completedToday ?? '-'}</div>
                </div>
                <div className="bg-white rounded-xl p-4 border border-red-100 shadow-sm">
                    <div className="flex items-center gap-2 text-red-600 mb-2">
                        <XCircle className="w-4 h-4" />
                        <span className="text-xs font-bold uppercase tracking-wider">今日失败</span>
                    </div>
                    <div className="text-2xl font-bold text-gray-900">{stats?.failedToday ?? '-'}</div>
                </div>
            </div>

            <div className="rounded-2xl border border-gray-100 shadow-sm bg-white overflow-hidden">
                <div className="px-5 py-3.5 bg-gradient-to-r from-violet-50 to-blue-50 border-b border-gray-100 flex items-center justify-between">
                    <h3 className="text-sm font-semibold text-violet-800">入库任务队列</h3>
                    {tasks.length > 0 && (
                        <span className="text-xs bg-violet-100 text-violet-700 font-medium px-2.5 py-0.5 rounded-full">
                            {tasks.length} 项任务
                        </span>
                    )}
                </div>

                <div className="p-4 space-y-3">
                    {tasks.length === 0 ? (
                        <p className="text-sm text-gray-400 text-center py-6">暂无切分任务</p>
                    ) : tasks.map((task) => {
                        const cfg = STATUS_CONFIG[task.status as keyof typeof STATUS_CONFIG] ?? STATUS_CONFIG.PENDING;
                        return (
                            <TaskItem 
                                key={task.taskId} 
                                task={task} 
                                onDelete={actions.deleteTask} 
                                onViewLogs={actions.setSelectedTaskId}
                                Icon={cfg.Icon} 
                            />
                        );
                    })}
                </div>
            </div>

            {/* Task Detail Drawer */}
            <TaskDetailDrawer 
                taskId={selectedTaskId} 
                onClose={() => actions.setSelectedTaskId(null)} 
            />
        </div>
    );
}