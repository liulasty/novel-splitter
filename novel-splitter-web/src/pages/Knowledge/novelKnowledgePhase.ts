/**
 * 知识库页展示分组：与后端 {@code NovelStatus} 字符串对齐（见 NovelSummaryDto.status）。
 * 对话 / RAG 选书仍用 scope=embed_ready（仅 COMPLETED）；本页用 scope=all 时按此分支 UI。
 */
export type NovelKnowledgePhase =
  | 'ready'
  | 'processing'
  | 'awaitingEmbed'
  | 'awaitingSplit'
  | 'failed'
  | 'unknown';

export function novelKnowledgePhase(status: string | null | undefined): NovelKnowledgePhase {
  const s = (status ?? '').trim().toUpperCase();
  switch (s) {
    case 'COMPLETED':
      return 'ready';
    case 'SPLITTING':
    case 'EMBEDDING':
      return 'processing';
    case 'SPLIT_COMPLETED':
      return 'awaitingEmbed';
    case 'PENDING':
    case 'PARSED':
      return 'awaitingSplit';
    case 'FAILED':
      return 'failed';
    default:
      return 'unknown';
  }
}

export const KNOWLEDGE_SECTION_ORDER: NovelKnowledgePhase[] = [
  'processing',
  'awaitingSplit',
  'awaitingEmbed',
  'failed',
  'ready',
  'unknown',
];

export function sectionTitleForPhase(phase: NovelKnowledgePhase): string {
  switch (phase) {
    case 'processing':
      return '入库处理中';
    case 'awaitingSplit':
      return '等待切分 / 解析';
    case 'awaitingEmbed':
      return '等待向量化';
    case 'failed':
      return '处理失败';
    case 'ready':
      return '已向量化完成（可检索）';
    case 'unknown':
      return '状态未知';
    default:
      return '其他';
  }
}

export function phaseBadge(phase: NovelKnowledgePhase): { label: string; className: string } {
    switch (phase) {
        case 'ready':
            return { label: '可检索', className: 'bg-emerald-50 text-emerald-800 border-emerald-200' };
        case 'awaitingSplit':
            return { label: '等待切分', className: 'bg-amber-50 text-amber-900 border-amber-200' };
        case 'awaitingEmbed':
            return { label: '待向量化', className: 'bg-sky-50 text-sky-900 border-sky-200' };
        case 'processing':
            return { label: '处理中', className: 'bg-violet-50 text-violet-900 border-violet-200' };
        case 'failed':
            return { label: '失败', className: 'bg-red-50 text-red-800 border-red-200' };
        default:
            return { label: '未知', className: 'bg-slate-100 text-slate-700 border-slate-200' };
    }
}
