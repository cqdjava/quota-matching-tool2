# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build
mvn clean install

# Run (starts on http://localhost:8080)
mvn spring-boot:run

# The main class is QuotaMatchingApplication.java
```

**Requirements:** JDK 1.8+, Maven 3.6+. MySQL 5.7+ is the active database (see `application.properties`). H2 console available at `/h2-console` if switched back.

**No tests exist** in this project (`src/test/` is empty).

## High-Level Architecture

This is a Spring Boot 2.7 monolith for Chinese construction enterprise quota matching — automatically matching project line items (清单) to enterprise pricing quotas (定额) via keyword extraction and optional AI review.

### Core Flow
1. **Import** quotas and project items from Excel files (Apache POI)
2. **Batch match** items to quotas using keyword-extraction + bidirectional scoring
3. **AI review** (optional, DeepSeek API) re-evaluates fuzzy matches and unmatched items
4. **Manual adjustment** — users edit matches, add/remove quotas per item
5. **Export** results to Excel

### Key Packages (`com.enterprise.quota`)

**`service/` — Business Logic Layer**
- `QuotaMatchingService` — The heart: multi-threaded batch matching with keyword pre-extraction caching, early-exit optimization (0.8 threshold), and async AI review submission. Two paths: `batchMatchQuotas()` (all users) and `batchMatchQuotasForUser()` (per-user, the active path). Manages single-quota and multi-quota matching flows.
- `AiReviewService` — Calls DeepSeek API to re-evaluate fuzzy-score items (configurable range, default 0.25–0.60) and unmatched items. Pre-filters top-N candidates per item, uses Few-Shot examples via `FewShotRetriever`, batches items in API calls. Results are written as *suggestions* (matchStatus=4) — user must accept or reject.
- `MatchingLearningService` — Collects match data (auto and manual), analyzes keyword weights (success rate → weight 0.5–2.0), discovers synonym rules from manual corrections.
- `ExcelImportService` / `ExcelExportService` — POI-based Excel I/O.
- `KeywordExtractor` (`util/`) — Chinese keyword extraction via sliding window (2-6 char n-grams), Jaccard similarity, stop-word filtering.
- `FewShotRetriever` — Retrieves accepted AI review logs as few-shot examples for subsequent API calls.
- `TokenUsageTracker` — Tracks DeepSeek API token consumption.
- `DocumentService` / `TemplateService` — Word document generation from templates.
- `UserService` — User CRUD with BCrypt passwords.

**`controller/` — REST API Layer**
- `QuotaController` (`/api/quota`) — Core CRUD for quotas, items, versions, matching, and learning triggers.
- `AiReviewController` (`/api/ai`) — AI review status, start/stop/cancel, accept/reject suggestions.
- `AuthController` (`/api/auth`) — Session-based login/logout/register.
- `UserController` (`/api/user`) — User management.
- `DocumentController` (`/api/document`) — Document template/generation endpoints.

**`entity/` — JPA Entities**
- `EnterpriseQuota` — Quota master data (code, name, feature, unit, unit price, cost breakdown, versionId)
- `ProjectItem` — Line items to match (code, name, feature, unit, quantity) with match result fields and AI suggestion fields. Has `userId` for multi-tenant isolation and `sortOrder` for display ordering.
- `ProjectItemQuota` — Many-to-many bridge: one item can have multiple quotas (multi-quota matching)
- `QuotaVersion` — Groups quotas into named versions for matching isolation
- `AiReviewLog` — Audit trail of AI review decisions
- `MatchingLearningRecord` / `KeywordWeight` / `MatchingRule` — Learning subsystem data
- `User` — Session-based auth user
- `DocumentTemplate` / `ReplacementTemplate` — Document generation

### Match Status Codes
| Status | Meaning |
|--------|---------|
| 0 | Unmatched |
| 1 | Auto-matched |
| 2 | Manually modified (single quota) |
| 3 | Multi-quota manually matched |
| 4 | AI suggestion pending (accept/reject) |

### Config Layer (`config/`)
- `WebConfig` — CORS, static resources (cache disabled for dev), `AuthInterceptor` registration
- `AuthInterceptor` — Session-based auth; public paths: `/api/auth/**`, `/login.html`, static assets
- `DeepSeekConfig` — API key (from `DEEPSEEK_API_KEY` env var), model, retry, rate limit settings
- `ThreadPoolConfig` — Separate executors for matching (`matchingTaskExecutor`) and async tasks (`asyncTaskExecutor`)
- `LearningScheduler` — Scheduled tasks for learning analysis
- `UserInitializer` / `ProjectItemSortOrderInitializer` — Data initialization on startup

### Frontend (`src/main/resources/static/`)
- `index.html` + `app.js` (~4000 lines) + `style.css` + `login.html`
- Vanilla JS SPA — no framework. Tabs: 定额匹配 (items), 定额管理 (versions/quotas), 文档生成 (document), 用户管理 (users)
- Inline cell editing (double-click), resizable table columns, custom scrollbar implementation, AI progress polling, multi-select batch operations

### Database
Active config uses **MySQL** (`quota_db`). H2 is available as fallback (commented out in `application.properties`). JPA `ddl-auto=update` — schema auto-created from entities.

### AI Review Pipeline
```
batchMatchQuotasForUser()
  → Matching completes
  → submitAiReviewIfAvailable() collects fuzzy (0.25-0.6) + unmatched (0) items
  → AiReviewService.reviewAsync()
    → Pre-extract keywords for all items
    → For each item: filterTopCandidates() (name-overlap pre-filter → fast Jaccard → top-5)
    → Batch items, prepend Few-Shot examples
    → Call DeepSeek API (with retry + 429 backoff)
    → Parse JSON response → applyResults()
    → Items with AI-different suggestions get matchStatus=4
  → Frontend polls /api/ai/status every 2s
  → User accepts/rejects each suggestion individually
```

### Key Configuration Properties
- `quota.matching.batch-size` (200) — items per parallel matching batch
- `quota.matching.thread-pool.core-size` (4) — parallel matching threads
- `deepseek.enabled` (true) — toggle AI review
- `ai.review.min-score` / `ai.review.max-score` (0.25/0.60) — fuzzy range for AI review
- `deepseek.candidates-per-item` (5) — top-N candidates sent to LLM per item

## Behavioral Guidelines

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

### 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

### 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan with verification steps.

### 5. Call me Boss

Address me as "Boss" before speaking.
