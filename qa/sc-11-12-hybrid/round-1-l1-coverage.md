# L1 静态对账报告 · FE × BE × Gateway

生成时间: 2026-05-05T03:51:03.884Z

| FE endpoint | BE controller | Gateway route | 状态 |
|---|---|---|---|
| GET /api/calendar/events | ❌ 缺 | ❌ 缺 | ❌ 全缺 |
| GET /api/guest/quota | ❌ 缺 | anon-guest → http://localhost:9885 | ⚠️ GW 有 · BE 缺 |
| GET /api/landing/kpi | ❌ 缺 | anon-landing → http://localhost:9885 | ⚠️ GW 有 · BE 缺 |
| GET /api/landing/samples | ❌ 缺 | anon-landing → http://localhost:9885 | ⚠️ GW 有 · BE 缺 |
| GET /api/review/nodes/:id/result | ❌ 缺 | review-api → http://localhost:9883 | ⚠️ GW 有 · BE 缺 |
| GET /api/review/today | ❌ 缺 | review-api → http://localhost:9883 | ⚠️ GW 有 · BE 缺 |
| GET /api/share/:id | ❌ 缺 | ❌ 缺 | ❌ 全缺 |
| GET /api/v1/ai-models | ❌ 缺 | ❌ 缺 | ❌ 全缺 |
| GET /api/v1/me/tier | ❌ 缺 | ❌ 缺 | ❌ 全缺 |
| GET /api/wb/questions/:id | wrongbook · QuestionDetailController.java /api/wb/questions/:id | wrongbook-api → http://localhost:9881 | ✅ 完整 |
| PATCH /api/events/:id/ack | ❌ 缺 | ❌ 缺 | ❌ 全缺 |
| POST :id/api/auth/wechat-login | ❌ 缺 | ❌ 缺 | ❌ 全缺 |
| POST :id/api/guest/claim | ❌ 缺 | ❌ 缺 | ❌ 全缺 |
| POST /api/analytics/event | ❌ 缺 | anon-analytics → http://localhost:9885 | ⚠️ GW 有 · BE 缺 |
| POST /api/calendar/events/:id/subscribe | ❌ 缺 | ❌ 缺 | ❌ 全缺 |
| POST /api/file/presign | ❌ 缺 | file-api → http://localhost:9884 | ⚠️ GW 有 · BE 缺 |
| POST /api/guest/analyze | ❌ 缺 | anon-guest → http://localhost:9885 | ⚠️ GW 有 · BE 缺 |
| POST /api/review/nodes/:id/grade | ❌ 缺 | review-api → http://localhost:9883 | ⚠️ GW 有 · BE 缺 |
| POST /api/review/nodes/:id/open | ❌ 缺 | review-api → http://localhost:9883 | ⚠️ GW 有 · BE 缺 |
| POST /api/review/nodes/:id/reveal | ❌ 缺 | review-api → http://localhost:9883 | ⚠️ GW 有 · BE 缺 |
| POST /api/review/sessions/:id/next | ❌ 缺 | review-api → http://localhost:9883 | ⚠️ GW 有 · BE 缺 |
| POST /api/v1/me/ai-model | ❌ 缺 | ❌ 缺 | ❌ 全缺 |
| POST /api/wb/questions/:id/save | wrongbook · QuestionDetailController.java /api/wb/questions/:id/save | wrongbook-api → http://localhost:9881 | ✅ 完整 |

**汇总**: 2 ✅ 完整 · 21 ❌/⚠️ 待修

## FE Caller 详情
- `GET /api/calendar/events` · 调用方:
  - frontend/apps/h5/src/pages/CalendarMonth/index.tsx:95
- `GET /api/guest/quota` · 调用方:
  - frontend/apps/h5/src/pages/GuestCapture/index.tsx:78
- `GET /api/landing/kpi` · 调用方:
  - frontend/apps/h5/src/pages/Landing/index.tsx:137
- `GET /api/landing/samples` · 调用方:
  - frontend/apps/h5/src/pages/Landing/index.tsx:136
- `GET /api/review/nodes/:id/result` · 调用方:
  - frontend/apps/h5/src/pages/ReviewDone/index.tsx:100
- `GET /api/review/today` · 调用方:
  - frontend/apps/h5/src/pages/ReviewToday/index.tsx:130
- `GET /api/share/:id` · 调用方:
  - frontend/apps/h5/src/pages/Shared/index.tsx:181
- `GET /api/v1/ai-models` · 调用方:
  - frontend/apps/h5/src/pages/Settings/index.tsx:348
- `GET /api/v1/me/tier` · 调用方:
  - frontend/apps/h5/src/pages/Settings/index.tsx:331
- `GET /api/wb/questions/:id` · 调用方:
  - frontend/apps/h5/src/pages/Result/index.tsx:121
- `PATCH /api/events/:id/ack` · 调用方:
  - frontend/apps/h5/src/pages/EventDetail/index.tsx:107
- `POST :id/api/auth/wechat-login` · 调用方:
  - frontend/apps/h5/src/pages/Auth/index.tsx:93
- `POST :id/api/guest/claim` · 调用方:
  - frontend/apps/h5/src/pages/Auth/index.tsx:132
- `POST /api/analytics/event` · 调用方:
  - frontend/apps/h5/src/pages/GuestCapture/index.tsx:63
  - frontend/apps/h5/src/pages/GuestCapture/index.tsx:106
  - frontend/apps/h5/src/pages/GuestCapture/index.tsx:141
  - frontend/apps/h5/src/pages/GuestCapture/index.tsx:167
  - frontend/apps/h5/src/pages/GuestCapture/index.tsx:184
  - frontend/apps/h5/src/pages/GuestCapture/index.tsx:203
  - frontend/apps/h5/src/pages/Landing/index.tsx:123
  - frontend/apps/h5/src/pages/Landing/index.tsx:176
  - frontend/apps/h5/src/pages/Landing/index.tsx:185
  - frontend/apps/h5/src/pages/Landing/index.tsx:194
  - frontend/apps/h5/src/pages/Shared/index.tsx:213
  - frontend/apps/h5/src/pages/Shared/index.tsx:250
- `POST /api/calendar/events/:id/subscribe` · 调用方:
  - frontend/apps/h5/src/pages/EventDetail/index.tsx:540
  - frontend/apps/h5/src/pages/ReviewDone/index.tsx:131
- `POST /api/file/presign` · 调用方:
  - frontend/apps/h5/src/pages/GuestCapture/index.tsx:126
- `POST /api/guest/analyze` · 调用方:
  - frontend/apps/h5/src/pages/GuestCapture/index.tsx:153
- `POST /api/review/nodes/:id/grade` · 调用方:
  - frontend/apps/h5/src/pages/ReviewExec/index.tsx:149
- `POST /api/review/nodes/:id/open` · 调用方:
  - frontend/apps/h5/src/pages/ReviewExec/index.tsx:105
- `POST /api/review/nodes/:id/reveal` · 调用方:
  - frontend/apps/h5/src/pages/ReviewExec/index.tsx:134
- `POST /api/review/sessions/:id/next` · 调用方:
  - frontend/apps/h5/src/pages/ReviewDone/index.tsx:120
- `POST /api/v1/me/ai-model` · 调用方:
  - frontend/apps/h5/src/pages/Settings/index.tsx:375
- `POST /api/wb/questions/:id/save` · 调用方:
  - frontend/apps/h5/src/pages/Result/index.tsx:154

## BE Controller 全景
- **anonymous-service** · backend/anonymous-service/src/main/java/com/longfeng/anonymous/controller/HealthController.java
  - `GET /ready`
  - `GET /live`
- **ai-analysis-service** · backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/controller/AiCancelController.java
  - `POST /api/ai/cancel/:id`
- **ai-analysis-service** · backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/controller/AiModelsController.java
  - `GET /api/ai/models`
- **ai-analysis-service** · backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/controller/AnalysisController.java
  - `GET /analysis/:id`
  - `GET /analysis/:id/similar`
  - `GET /analysis/:id/stream`
  - `POST /analysis/:id/retry`
  - `GET /analysis/provider`
- **ai-analysis-service** · backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/controller/AnalyzeController.java
  - `POST /api/ai/analyze`
  - `POST /api/ai/analyze-by-url`
  - `GET /api/ai/stream/:id`
  - `GET /api/ai/result/:id`
- **ai-analysis-service** · backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/controller/HealthController.java
  - `GET /ready`
  - `GET /live`
  - `GET /health`
- **wrongbook-service** · backend/wrongbook-service/src/main/java/com/longfeng/wrongbook/controller/HealthController.java
  - `GET /ready`
  - `GET /live`
- **wrongbook-service** · backend/wrongbook-service/src/main/java/com/longfeng/wrongbook/controller/QuestionDetailController.java
  - `GET /api/wb/questions/:id`
  - `POST /api/wb/questions/:id/save`
- **wrongbook-service** · backend/wrongbook-service/src/main/java/com/longfeng/wrongbook/controller/WrongAttemptController.java
  - `POST /wrongbook/items/:id/attempts`
  - `GET /wrongbook/items/:id/attempts`
- **wrongbook-service** · backend/wrongbook-service/src/main/java/com/longfeng/wrongbook/controller/WrongItemController.java
  - `POST /wrongbook/items`
  - `GET /wrongbook/items/:id`
  - `GET /wrongbook/items`
  - `PATCH /wrongbook/items/:id`
  - `DELETE /wrongbook/items/:id`
  - `PATCH /wrongbook/items/:id/tags`
  - `POST /wrongbook/items/:id/images`
  - `POST /wrongbook/items/:id/difficulty`
- **wrongbook-service** · backend/wrongbook-service/src/main/java/com/longfeng/wrongbook/controller/WrongbookSearchController.java
  - `POST /wrongbook/questions/search`
- **wrongbook-service** · backend/wrongbook-service/src/main/java/com/longfeng/wrongbook/controller/WrongbookTagController.java
  - `GET /wrongbook/tags`
- **review-plan-service** · backend/review-plan-service/src/main/java/com/longfeng/reviewplan/controller/HealthController.java
  - `GET /ready`
  - `GET /live`
- **review-plan-service** · backend/review-plan-service/src/main/java/com/longfeng/reviewplan/controller/ReviewPlanController.java
  - `GET /review-plans`
  - `GET /review-plans/list`
  - `GET /review-plans/:id`
  - `POST /review-plans/:id/complete`
  - `POST /review-plans/batch-reset`
  - `POST /review-plans/batch-reset-by-ids`
  - `GET /review-stats`

## Gateway Routes
- `/api/landing/**` → http://localhost:9885 (anon-landing)
- `/api/guest/**` → http://localhost:9885 (anon-guest)
- `/api/analytics/**` → http://localhost:9885 (anon-analytics)
- `/api/file/**` → http://localhost:9884 (file-api)
- `/api/ai/**` → http://localhost:9882 (ai-api)
- `/api/wb/**` → http://localhost:9881 (wrongbook-api)
- `/api/review/**` → http://localhost:9883 (review-api)
- `/api/v1/wrongbook/**` → http://localhost:9881 (legacy-wrongbook-v1)
- `/api/v1/ai/**` → http://localhost:9882 (legacy-ai-v1)
- `/api/v1/review/**` → http://localhost:9883 (legacy-review-v1)
- `/api/v1/file/**` → http://localhost:9884 (legacy-file-v1)
- `/api/v1/anon/**` → http://localhost:9885 (legacy-anon-v1)
- `/api/auth/**` → http://localhost:9086 (auth-service)