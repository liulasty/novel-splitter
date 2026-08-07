import type { SplitTask } from "@/api/taskApi";
import type { SceneSplitProfileDto } from '@/api/knowledgeApi';
import type { CreateVersionRequest, NovelVersionDto } from '@/api/novelApi';

export interface ProcessState {
  currentNovelId: string;
  /** currentNovelId 指向已删/不存在的小说（novelOptions 已加载且不含该书） */
  novelMissing: boolean;
  version: string;
  profiles: SceneSplitProfileDto[];
  currentProfile?: SceneSplitProfileDto;
  maxTokens: number;
  overlapTokens: number;
  chapterReviewAck: boolean;
  chapterTitleRegex: string;
  recognitionStrategy: string;
  tasks: SplitTask[];
  activeTasks: SplitTask[];
  poller: {
    errorCount: number;
    isPaused: boolean;
    stuckTaskIds: string[];
    timeoutTaskIds: string[];
  };
  isChapterParsing: boolean;
  isSceneSplitting: boolean;
  isEmbedding: boolean;
  // 版本实验视图（/process 主数据）
  versions: NovelVersionDto[];
  versionsLoading: boolean;
  isBaselineReady: boolean;
  isCreatingVersion: boolean;
  isStartingSplit: boolean;
  isStartingEmbed: boolean;
  isActivating: boolean;
  isDeletingVersion: boolean;
}

export interface ProcessActions {
  setVersion: (v: string) => void;
  setMaxTokens: (v: number) => void;
  setOverlapTokens: (v: number) => void;
  setChapterTitleRegex: (v: string) => void;
  setRecognitionStrategy: (v: string) => void;
  acknowledgeChapterReview: () => void;
  handleChapterParse: () => void;
  handleSceneSplit: (triggerEmbed: boolean) => void;
  handleForceReparseChapters: () => void;
  handleEmbed: () => void;
  manualRefresh: () => Promise<void>;
  selectNovelById: (novelId: string) => void;
  clearSelectedNovel: () => void;
  addActiveTask: (taskId: string) => void;
  // 版本实验视图
  createVersion: (body: CreateVersionRequest) => void;
  startSplit: (versionTag: string) => void;
  startEmbed: (versionTag: string) => void;
  activate: (versionTag: string) => void;
  deleteVersion: (versionTag: string) => void;
  reEnrich: (versionTag: string) => void;
  resetVersionEnrich: (versionTag: string) => void;
}

/** ProcessingPanel 派生的门控布尔，各 tab 用于按钮禁用 */
export interface ProcessGates {
  chapterParseBusy: boolean;
  chapterParseSucceeded: boolean;
  structurallyReady: boolean;
  canSceneSplit: boolean;
}
