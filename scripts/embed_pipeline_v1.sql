-- Optional manual migration when not relying on Hibernate ddl-auto (PostgreSQL).
-- Align defaults with your environment: legacy rows may be marked SUCCESS if vectors already exist.

ALTER TABLE split_tasks ADD COLUMN IF NOT EXISTS current_embed_run_id VARCHAR(36);

ALTER TABLE scenes ADD COLUMN IF NOT EXISTS embed_status VARCHAR(32);
ALTER TABLE scenes ADD COLUMN IF NOT EXISTS embed_error TEXT;
ALTER TABLE scenes ADD COLUMN IF NOT EXISTS embed_run_id VARCHAR(36);

-- Existing deployments: assume scenes were already embedded → SUCCESS; adjust if re-embedding is required.
UPDATE scenes SET embed_status = 'SUCCESS' WHERE embed_status IS NULL;

COMMENT ON COLUMN split_tasks.current_embed_run_id IS 'UUID of current embed orchestration run; stale MQ messages must match.';
COMMENT ON COLUMN scenes.embed_status IS 'PENDING | SUCCESS | FAILED';
COMMENT ON COLUMN scenes.embed_error IS 'Last embed failure message (truncated at application layer if needed).';
COMMENT ON COLUMN scenes.embed_run_id IS 'Embed run id for this row; pairs with split_tasks.current_embed_run_id for a run.';
