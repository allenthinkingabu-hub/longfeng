/**
 * MSW · review-plan-service mock（B 轨 · 给 SC-02/SC-03/SC-04/SC-05 用）
 *
 * 端点（plan §S5）：
 *   - GET    /api/v1/review-plans              · day view + cursor
 *   - GET    /api/v1/review-plans/:id          · 单 plan 详情
 *   - POST   /api/v1/review-plans/batch-reset  · FORGOT 重排
 *   - POST   /api/v1/review-plans/:id/complete · 完成节点（grade）
 *   - GET    /api/v1/review-today              · P07 hero
 */
import { http, HttpResponse } from 'msw';

const NOW_ISO = () => new Date().toISOString();
const DAY_MS = 86_400_000;
const inDays = (n: number) => new Date(Date.now() + n * DAY_MS).toISOString();

const NODES_T1_T6 = (planId: string) => [
  { nid: `${planId}-n1`, tLevel: 'T1', dueAt: inDays(0),  status: 'SCHEDULED', mastery: 0   },
  { nid: `${planId}-n2`, tLevel: 'T2', dueAt: inDays(1),  status: 'SCHEDULED', mastery: 0   },
  { nid: `${planId}-n3`, tLevel: 'T3', dueAt: inDays(4),  status: 'SCHEDULED', mastery: 0   },
  { nid: `${planId}-n4`, tLevel: 'T4', dueAt: inDays(8),  status: 'SCHEDULED', mastery: 0   },
  { nid: `${planId}-n5`, tLevel: 'T5', dueAt: inDays(16), status: 'SCHEDULED', mastery: 0   },
  { nid: `${planId}-n6`, tLevel: 'T6', dueAt: inDays(35), status: 'SCHEDULED', mastery: 0   },
];

export const reviewHandlers = [
  // P07 today hero
  http.get('/api/v1/review-today', () => HttpResponse.json({
    total: 12,
    done: 4,
    estMin: 18,
    masteryPct: 67,
    streak: 5,
  })),

  // List plans (day view)
  http.get('/api/v1/review-plans', ({ request }) => {
    const url = new URL(request.url);
    const limit = Number(url.searchParams.get('limit') ?? '20');
    const items = Array.from({ length: Math.min(limit, 12) }, (_, i) => ({
      planId: `plan-${i + 1}`,
      qid: `qid-${i + 1}`,
      stemPreview: `这是第 ${i + 1} 道错题的题干前 50 字预览…`,
      subject: ['math', 'physics', 'chemistry', 'english'][i % 4],
      next_due_at: inDays(i % 7),
      user_id: 'student-001',
      mastery: 20 + (i * 7) % 80,
      interval: [1, 3, 7, 15, 30][i % 5],
    }));
    return HttpResponse.json({
      items,
      next_cursor: limit < 100 ? null : 'cursor-next-page',
      has_more: false,
    });
  }),

  http.get('/api/v1/review-plans/:planId', ({ params }) => HttpResponse.json({
    planId: params.planId,
    qid: 'qid-001',
    user_id: 'student-001',
    nodes: NODES_T1_T6(String(params.planId)),
    interval: 7,
    mastery: 45,
  })),

  // Complete node (grade)
  http.post('/api/v1/review-plans/:planId/complete', async ({ request, params }) => {
    const body = await request.json() as { nid?: string; grade?: 'forgot' | 'partial' | 'mastered' };
    const isForgot = body?.grade === 'forgot';
    return HttpResponse.json({
      planId: params.planId,
      nodeUpdated: body?.nid,
      newStatus: isForgot ? 'CANCELLED_AND_RESEED' : 'COMPLETED',
      advanceBanner: isForgot,
      cascadeReseededNodes: isForgot ? NODES_T1_T6(String(params.planId)) : [],
      stats: { mastered: isForgot ? 0 : 1, partial: 0, forgot: isForgot ? 1 : 0 },
    });
  }),

  // FORGOT batch-reset
  http.post('/api/v1/review-plans/batch-reset', async ({ request }) => {
    const body = await request.json() as { planIds?: string[] };
    return HttpResponse.json({
      reseeded: body?.planIds ?? [],
      newScheduledCount: (body?.planIds?.length ?? 0) * 6,
    });
  }),
];
