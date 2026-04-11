-- PostgreSQL 数据对账（上线前可手工执行）
-- 用法：psql -U postgres -d novel_splitter -f scripts/reconcile.sql

-- 1) 行数快照
SELECT 'novels' AS tbl, COUNT(*) AS n FROM novels WHERE is_deleted = false
UNION ALL SELECT 'chapters', COUNT(*) FROM chapters
UNION ALL SELECT 'scenes', COUNT(*) FROM scenes
UNION ALL SELECT 'split_tasks', COUNT(*) FROM split_tasks
UNION ALL SELECT 'task_events', COUNT(*) FROM task_events;

-- 2) 孤儿 chapters（小说已软删或不存在）
SELECT c.id, c.novel_id
FROM chapters c
LEFT JOIN novels n ON n.id = c.novel_id
WHERE n.id IS NULL OR n.is_deleted = true
LIMIT 50;

-- 3) 孤儿 scenes
SELECT s.id, s.novel_id, s.version
FROM scenes s
LEFT JOIN novels n ON n.id = s.novel_id
WHERE n.id IS NULL OR n.is_deleted = true
LIMIT 50;

-- 4) split_tasks 无对应小说（任务残留）
SELECT t.task_id, t.novel_id
FROM split_tasks t
LEFT JOIN novels n ON n.id = t.novel_id
WHERE n.id IS NULL
LIMIT 50;

-- 5) task_events 无对应任务
SELECT e.task_id, COUNT(*) AS cnt
FROM task_events e
LEFT JOIN split_tasks t ON t.task_id = e.task_id
WHERE t.task_id IS NULL
GROUP BY e.task_id
LIMIT 50;
