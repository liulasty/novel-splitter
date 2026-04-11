-- Optional: 若历史库中存在 parent_scene_id 列，可执行以下语句。
-- 当前 JPA 实体未映射该列；新写入的 metadata_json 不再包含 parentSceneId / chunkType。

ALTER TABLE public.scenes DROP COLUMN IF EXISTS parent_scene_id;
