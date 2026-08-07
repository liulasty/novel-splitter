# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Quick Start

Reference: `DOCKER_COMPOSE_GUIDE.md` for the complete Docker operations manual.

### Start the full stack (recommended)

**One-click script:**
```bash
.\scripts\start-all.ps1          # quick start (no build)
.\scripts\start-all.ps1 -Build   # build backend first, then start
```

**Or directly with Docker Compose:**
```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d
```

All 5 services (PostgreSQL, RabbitMQ, ChromaDB, backend, frontend) start in one command. Prerequisite: Docker Desktop running.

**IMPORTANT**: If backend code changed, run `mvn clean package -DskipTests` first (or use `start-all.ps1 -Build`) — the Dockerfile copies a pre-built jar, it does NOT run Maven inside the container.

### Local IDE dev (hot-reload)

```bash
# Start only infrastructure
.\scripts\start-infra.ps1

# Then run backend + frontend manually:
# Backend: main class NovelSplitApplication in the interfaces module
# CRITICAL: when running locally, DB_HOST must be localhost (not postgres).
# config/.env.dev already has DB_HOST=localhost.
```

### Convenience scripts (all under `scripts/`)

| Script | Does |
|---|---|
| `start-all.ps1` / `.bat` / `.sh` | Start all 5 services (add `-Build` / `--build` to rebuild backend first) |
| `svc.ps1` / `.bat` / `.sh` | Manage any service: `svc.ps1 restart backend`, `svc.ps1 logs frontend`, `svc.ps1 ps` |
| `start-infra.ps1` / `.bat` / `.sh` | Start only PostgreSQL + RabbitMQ + ChromaDB |
| `debug.ps1` / `.bat` / `.sh` | Rebuild & restart backend+frontend containers, tail logs |
| `deploy.ps1 dev` / `prod` | Full deploy to specified environment |
| `stop.ps1 dev` / `prod` | Stop all containers |
| `build.ps1` | `mvn clean package -DskipTests` + `docker compose build` |
| `reset-infra-data.ps1` | Wipe all persisted data |
| `version.ps1` | Print current Maven project version |

All scripts auto-read `config/.env.dev` or `config/.env.prod`. No manual env var setup needed.

### Testing

```bash
mvn test                                               # all tests
mvn test -pl text-processing -Dtest="ChapterRecognizerTest"
mvn test -pl text-processing -Dtest="ChapterRecognizerTest#testMethodName"
```

Test classes exist in: `application`, `interfaces`, `text-processing`, `embedding`, `llm-client`, `context-assembler`, `domain`.

### Build for Docker

```bash
mvn clean package -DskipTests                          # build fat jar (interfaces/target/interfaces-*.jar)
docker compose --env-file config/.env.dev build         # build all Docker images
docker compose --env-file config/.env.dev up -d --build # rebuild & restart changed services only
```

## Project Architecture

**Novel Splitter** converts Chinese web-novels into RAG-ready semantic scenes. It cleans, structurally parses, semantically splits, enriches, vector-embeds, and provides a 5-stage context assembly pipeline for LLM Q&A.

### Module Layout (Maven multi-module, DDD-style)

```
domain/            — Entities (Novel, Scene, Chapter, SplitTask), repository interfaces, enums, strategy contracts
application/       — Facade (NovelFacadeServiceImpl), MQ consumers (SplitWorker, EmbedWorker), DTO mapping, application.yml
infrastructure/    — JPA repository impls, entity↔domain mappers (MapStruct), FileUtils, JsonUtils
interfaces/        — REST controllers, GlobalExceptionHandler, ApiResponse, AuthInterceptor, Spring Boot entry point
batch-processing/  — Pipeline: Load/Split/Embed use cases
text-processing/   — NLP: ChapterRecognizer, MarkdownParagraphSplitter, ContextAwareSegmentBuilder, SceneAssembler
validation/        — Quality: SemanticSegmentBuilder, dialogue/length strategies
embedding/         — ONNX Runtime (BGE-Small-ZH), Tokenizer/Vocabulary, ChromaVectorStore
retrieval/         — RAG: RagFacade, VectorRetrievalService, AnswerPolicyClassifier, RetrievalQueryBuilder
context-assembler/ — 5-stage: ReScorer → Deduplicator → Merger → TokenBudgetAllocator → FinalAssembly
llm-client/        — LlmClient interface, DeepSeek/Gemini/Coze/Ollama clients, RobustLlmClient (retry+circuit)
novel-splitter-web/— React 19 + Vite + Zustand + TanStack Query + TailwindCSS 4
```

### Design Pattern (Hexagonal / Ports & Adapters)

Core architecture is **六边形架构（Ports & Adapters，端口与适配器）** 叠加 DDD 分层。依赖方向严格由外向内，无反向依赖：

```
interfaces ──→ application ──→ domain
infrastructure ──→ domain        (application 不依赖 infrastructure)
```

- **出站端口**（定义在 application，只依赖 domain）：`application/port/out/` → `FileStoragePort`、`TaskQueuePort`、`TaskCachePort`
- **出站适配器**（位于外围的 interfaces）：`interfaces/infra/` → `LocalFileStorageAdapter`、`RabbitTaskQueueAdapter`、`NoOpTaskCacheAdapter`
- **仓储端口**：`domain/repository/` 接口即端口；`infrastructure/persistence/repository/impl/*JpaImpl` 为 JPA 适配器
- **入站侧**：REST Controller（`interfaces/api`）为驱动适配器，`NovelFacadeService` 为入站端口

叠加的其它模式：

- **Facade 门面**：`NovelFacadeServiceImpl`
- **Repository 仓储**：domain 接口 + infrastructure JPA 实现
- **Strategy 策略**：`ChapterRecognitionStrategy`、`DialogueStrategy`、`LengthLimitStrategy`、`ChunkingStrategy`、`AnswerPolicyClassifier`
- **Use Case 用例**：无状态的 `LoadNovelUseCase` / `SplitNovelUseCase` / `EmbedNovelUseCase`
- **事件/消息驱动**：RabbitMQ workers，编排只留在 application 层

注意：`retrieval`、`context-assembler`、`llm-client` 等处理引擎模块被 application 直接依赖，作为被编排的领域服务——这是**六边形为主、带模块化分层**的务实混合，而非教科书式纯净六边形。

### Data Flow (MQ-driven async pipeline)

```
Upload → POST /api/novels/upload → file saved, task PENDING
  → RabbitMQ novel.task.load    → LoadWorker: read TXT, clean, parse chapters
  → RabbitMQ novel.task.split   → SplitWorker: ChapterRecognizer → ParagraphSplitter → ContextSegmentBuilder → SceneAssembler
  → RabbitMQ novel.task.embed   → EmbedWorker: ONNX embedding → ChromaDB
  → RabbitMQ novel.task.cleanup → CleanupWorker: cascade delete files + vectors
```

### Key Architectural Rules

From `.cursor/rules/pipeline-orchestration.mdc` (always applied):

- **UseCases are stateless and independently callable.** Each reads from DB by novelId/taskId, writes back. No large object passing between stages.
- **Orchestration lives ONLY in application layer.** `NovelFacadeServiceImpl` decides which stage runs next. Workers NEVER dispatch the next stage.
- **PipelineContext carries only IDs** (novelId, taskId, Map<String,Object> params). Stages read/write through persistent storage.
- **Workers are forbidden from chaining.** A Worker finishes its queue, updates DB, then stops. It does NOT publish the next stage's message.
- **Task state machine**: `PENDING → LOADING → LOAD_DONE/LOAD_FAILED → SPLITTING → SPLIT_DONE/SPLIT_FAILED → EMBEDDING → EMBED_DONE/EMBED_FAILED`
- **MQ message shape**: `{ novelId, taskId, stageType, autoAdvance }`
- **Scene versioning**: `(novelId, version)` partitions scenes/vectors. If different chunking params must coexist, callers provide distinct version strings.

### Context Assembly (5-Stage RAG Pipeline)

1. **SceneReScorer** — relevance re-ranking of retrieved scenes
2. **SceneDeduplicator** — remove overlapping/duplicate scenes
3. **SceneMerger** — merge adjacent scenes preserving narrative flow
4. **TokenBudgetAllocator** — enforce max-context-tokens cap
5. **FinalAssembly** — serialize into prompt-ready context string

### Key Design Decisions

From `docs/decisions.md`:
- **Polling, not SSE** for progress — 2-3s polling sufficient for long-running tasks
- **Static Bearer token auth** — sufficient for admin tooling
- **ONNX local embedding** — zero API cost, no network dependency (BGE-Small-ZH)
- **ChromaDB + local file dual storage** — vectors in Chroma for fast search, full scene metadata on disk for hydration
- **JPA batch_size=500 + order_inserts=true** — optimized bulk Scene inserts

### Infrastructure Stack

- **PostgreSQL** (Hibernate ddl-auto:update, batch_size=500)
- **RabbitMQ** (DirectExchange for task routing, FanoutExchange for progress notifications)
- **ChromaDB** (HTTP, hnsw:space=cosine)
- **ONNX Runtime** (BGE-Small-ZH v1.5 embedding, bge-reranker-base for Ask pipeline re-ranking)
- **LLM providers**: DeepSeek (default), Gemini, Coze, Ollama — via `LlmClient` + `RobustLlmClient`

## Known Issues & Current Focus

From `docs/todo.md`:
- SSE streaming for Chat responses is NOT implemented (current: synchronous blocking)
- DLQ coverage incomplete — DlqService/DlqController exist but not all core queues have `x-dead-letter-exchange`
- `ChatPage.tsx` (16KB) and `SystemPage.tsx` (14KB) need component decomposition
- `ragApi.ts` uses standalone axios instance (missing shared token injection)
- PostgreSQL ↔ ChromaDB cross-store transaction not handled
- No performance profiling for 1000+ chapter novels
- Mobile frontend (docs/plan/main/) designed but not implemented

## Config & Environment

- **`config/.env.dev`** — development env vars (DB_HOST=localhost, exposed ports for debugging)
- **`config/.env.prod`** — production env vars (no external ports except :80, resource limits, auto-restart)
- **`config/.env.example`** — template for new deployments
- **Root `.env`** — deprecated, redirects to `config/.env.*`
- **`config/settings.json`** — system settings (created on first run)

When running backend in Docker: `DB_HOST` is overridden to `postgres` inside `docker-compose.yml`.
When running backend locally: `DB_HOST` must be `localhost` (already set in `config/.env.dev`).
