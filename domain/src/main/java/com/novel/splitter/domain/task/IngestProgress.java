package com.novel.splitter.domain.task;

public class IngestProgress {

    // 各阶段进度区间（起点、终点）
    public static final int LOAD_START = 5;
    public static final int LOAD_END = 15;
    
    public static final int CHAPTER_START = 15;
    public static final int CHAPTER_END = 30;
    
    public static final int PARAGRAPH_START = 30;
    public static final int PARAGRAPH_END = 45;
    
    public static final int SCENE_START = 45;
    public static final int SCENE_END = 55;
    
    public static final int VALIDATE_START = 55;
    public static final int VALIDATE_END = 60;
    
    public static final int SAVE_START = 60;
    public static final int SAVE_END = 63;
    
    public static final int EMBED_START = 63;
    public static final int EMBED_END = 99;

    /**
     * 工具方法：根据当前index/total计算区间内线性进度
     * 公式：start + (int)((double)(index+1)/total * (end-start))
     */
    public static int calc(int start, int end, int index, int total) {
        if (total <= 0) {
            return start;
        }
        return start + (int) ((double) (index + 1) / total * (end - start));
    }
}
