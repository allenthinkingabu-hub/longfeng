/**
 * MSW · calendar mock（B 轨 · 给 SC-05/SC-06 用）
 *
 * 端点：
 *   - GET /api/v1/calendar/month     · 旧端点（兼容 P11/P12 路径）
 *   - GET /api/calendar/events       · P10 实际 fetch 的端点（month + tz）
 *   - GET /api/v1/event/:eventId     · P11 事件详情
 */
import { http, HttpResponse } from 'msw';

const MOCK_EVENTS = [
  { id: 'ev-1',  date: '2026-05-02', kind: 'STUDY',  subject: 'math',    qid: 'qid-1' },
  { id: 'ev-2',  date: '2026-05-03', kind: 'STUDY',  subject: 'physics', qid: 'qid-2' },
  { id: 'ev-3',  date: '2026-05-15', kind: 'EXAM',   subject: 'math',    location: '一中考场', countdownDays: 13 },
  { id: 'ev-4',  date: '2026-05-08', kind: 'FAMILY', title: '家长会' },
];

/**
 * 生成 P10 月视图 42 cell 数据。
 * - 周一为首列（Mon-first）
 * - cells.length = 42（6 周 × 7 列）
 * - 含上月末 + 当月 + 下月初
 * - 当月内日期 inMonth=true · 当天 isToday=true
 */
function buildMonthCells(monthStr: string, todayStr: string) {
  const [y, m] = monthStr.split('-').map(Number);
  const firstOfMonth = new Date(y, m - 1, 1);
  // JS getDay: 0=Sun..6=Sat → Mon-first 列号: (getDay+6) % 7
  const firstCol = (firstOfMonth.getDay() + 6) % 7;
  const startDate = new Date(y, m - 1, 1 - firstCol);

  const eventsByDate = new Map<string, Array<{
    eventId: string; relationType: 'STUDY' | 'EXAM' | 'FAMILY';
    subject?: string; tLevel?: string; startAt: string;
  }>>();
  for (const e of MOCK_EVENTS) {
    if (!eventsByDate.has(e.date)) eventsByDate.set(e.date, []);
    eventsByDate.get(e.date)!.push({
      eventId: e.id,
      relationType: e.kind as 'STUDY' | 'EXAM' | 'FAMILY',
      subject: 'subject' in e ? (e as { subject?: string }).subject : undefined,
      tLevel: e.kind === 'STUDY' ? 'T1' : undefined,
      startAt: `${e.date}T09:00:00+08:00`,
    });
  }

  const cells = Array.from({ length: 42 }, (_, i) => {
    const d = new Date(startDate.getFullYear(), startDate.getMonth(), startDate.getDate() + i);
    const yy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    const date = `${yy}-${mm}-${dd}`;
    return {
      date,
      inMonth: d.getMonth() === m - 1,
      isToday: date === todayStr,
      events: eventsByDate.get(date) ?? [],
    };
  });

  return cells;
}

export const calendarHandlers = [
  /**
   * P10 实际调用端点 · 必须返回 cells[42]（包含 cell-15）
   * SC-05: e2e openCell(15) 依赖此 handler 注入数据
   */
  http.get('/api/calendar/events', ({ request }) => {
    const url = new URL(request.url);
    const month = url.searchParams.get('month') ?? '2026-05';
    const today = new Date().toISOString().slice(0, 10);
    return HttpResponse.json({
      month,
      tzOffset: url.searchParams.get('tz') ?? 'Asia/Shanghai',
      today,
      cells: buildMonthCells(month, today),
    });
  }),

  // 旧端点（保留向后兼容）
  http.get('/api/v1/calendar/month', ({ request }) => {
    const url = new URL(request.url);
    const month = url.searchParams.get('month'); // "2026-05"
    return HttpResponse.json({
      month: month ?? '2026-05',
      events: MOCK_EVENTS,
      legendCounts: {
        math: 8, physics: 5, chemistry: 3, english: 2, exam: 1, family: 1,
      },
    });
  }),

  http.get('/api/v1/event/:eventId', ({ params }) => {
    const ev = MOCK_EVENTS.find((e) => e.id === params.eventId);
    if (!ev) {
      // SC-06 通用事件路径：返回 mock GENERAL form
      if (String(params.eventId).startsWith('test-event-general')) {
        return HttpResponse.json({
          id: params.eventId,
          kind: 'FAMILY',
          title: '家长会',
          date: '2026-05-08',
          form: 'GENERAL',
        });
      }
      return new HttpResponse(null, { status: 404 });
    }
    return HttpResponse.json({
      ...ev,
      form: ev.kind === 'STUDY' ? 'STUDY' : 'GENERAL',
      relatedQuestion: ev.kind === 'STUDY' ? { qid: ev.qid, stemPreview: '题干前 50 字…' } : undefined,
      memoryCurve: ev.kind === 'STUDY' ? {
        nodes: ['T0', 'T1', 'T2', 'T3', 'T4', 'T5', 'T6'].map((t, i) => ({ tLevel: t, status: i < 2 ? 'COMPLETED' : 'SCHEDULED' })),
      } : undefined,
    });
  }),
];
