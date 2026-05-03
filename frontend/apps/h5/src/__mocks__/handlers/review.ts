/**
 * MSW · review-plan-service mock（B 轨 · 给 SC-02/SC-03/SC-04/SC-05 用）
 *
 * 端点（plan §S5 + 前端实际调用）：
 *   - GET    /api/v1/review-plans              · day view + cursor
 *   - GET    /api/v1/review-plans/:id          · 单 plan 详情
 *   - POST   /api/v1/review-plans/batch-reset  · FORGOT 重排
 *   - POST   /api/v1/review-plans/:id/complete · 完成节点（grade）
 *   - GET    /api/v1/review-today              · P07 hero
 *   - POST   /api/review/nodes/:nid/open       · P08 打开节点（计时开始）
 *   - POST   /api/review/nodes/:nid/reveal     · P08 揭示答案
 *   - POST   /api/review/nodes/:nid/grade      · P08 提交自评（累加 SESSION_STATS）
 *   - GET    /api/review/nodes/:nid/result     · P09 结果页数据
 *   - POST   /api/review/sessions/:nid/next    · P09 下一题
 *   - POST   /api/calendar/events/:nid/subscribe · P09 加日历
 */
import { http, HttpResponse } from 'msw';

const NOW_ISO = () => new Date().toISOString();
const DAY_MS = 86_400_000;
const inDays = (n: number) => new Date(Date.now() + n * DAY_MS).toISOString();

// ─── Module-level session stats（P08 grade → 累加 → P09 读） ────
const SESSION_STATS = { mastered: 0, partial: 0, forgot: 0, total: 8 };

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

  // ─── P08 / P09 节点级 API（前端实际调用路径） ────────────────────

  // P08 打开节点（计时开始）
  http.post('/api/review/nodes/:nid/open', ({ params }) => HttpResponse.json({
    nid: params.nid,
    openedAt: NOW_ISO(),
    timeBudgetSec: 120,
  })),

  // P08 揭示答案
  http.post('/api/review/nodes/:nid/reveal', ({ params }) => HttpResponse.json({
    nid: params.nid,
    revealedAt: NOW_ISO(),
  })),

  /**
   * P08 提交自评 · 累加 SESSION_STATS（B 轨 SC-02 关键）
   * - grade='mastered' / 'partial' / 'forgot' 各自 +1
   * - 返回 nextNodeId=null → 让前端跳转到 /review/done
   */
  http.post('/api/review/nodes/:nid/grade', async ({ request, params }) => {
    const body = await request.json() as { grade?: string };
    // SC-02: 前端发大写 'FORGOT' / 'PARTIAL' / 'MASTERED' · 这里小写归一兼容
    const g = String(body?.grade ?? '').toLowerCase();
    if (g === 'mastered') SESSION_STATS.mastered += 1;
    else if (g === 'partial') SESSION_STATS.partial += 1;
    else if (g === 'forgot') SESSION_STATS.forgot += 1;
    return HttpResponse.json({
      nid: params.nid,
      grade: g,
      nextNodeId: null, // 触发 P09
      stats: { ...SESSION_STATS },
    });
  }),

  /**
   * P09 结果页数据 · 读 SESSION_STATS（B 轨 SC-02 关键）
   * - todayStats.mastered 必须 > 0 当 P08 grade='mastered' 后
   */
  http.get('/api/review/nodes/:nid/result', ({ params }) => {
    const nid = String(params.nid);
    return HttpResponse.json({
      nid,
      variant: 'single',
      previousT: 'T2',
      nextT: 'T3',
      nextDueAt: inDays(3),
      masteryPct: 82,
      advanceDays: 7,
      todayStats: { ...SESSION_STATS },
      kpDelta: [
        { kpId: 'kp1', kpName: '顶点式 · 配方法', subject: 'math', oldPct: 70, newPct: 86 },
        { kpId: 'kp2', kpName: '对称轴方程', subject: 'math', oldPct: 60, newPct: 74 },
      ],
      plannedNodes: [
        { tLevel: 'T1', status: 'done' },
        { tLevel: 'T2', status: 'done' },
        { tLevel: 'T3', status: 'just-completed' },
        { tLevel: 'T4', status: 'next-encouragement' },
        { tLevel: 'T5', status: 'future' },
        { tLevel: 'T6', status: 'future' },
      ],
      hasNext: true,
      isStreakMilestone: false,
    });
  }),

  // P09 下一题
  http.post('/api/review/sessions/:nid/next', ({ params }) => HttpResponse.json({
    nextNodeId: `${params.nid}-next`,
  })),

  // P09 加日历
  http.post('/api/calendar/events/:nid/subscribe', ({ params }) => HttpResponse.json({
    nid: params.nid,
    subscribed: true,
  })),
];
