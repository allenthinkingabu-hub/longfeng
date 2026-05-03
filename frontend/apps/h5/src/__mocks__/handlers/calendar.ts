/**
 * MSW · calendar mock（B 轨 · 给 SC-05/SC-06 用）
 */
import { http, HttpResponse } from 'msw';

const MOCK_EVENTS = [
  { id: 'ev-1',  date: '2026-05-02', kind: 'STUDY',  subject: 'math',    qid: 'qid-1' },
  { id: 'ev-2',  date: '2026-05-03', kind: 'STUDY',  subject: 'physics', qid: 'qid-2' },
  { id: 'ev-3',  date: '2026-05-15', kind: 'EXAM',   subject: 'math',    location: '一中考场', countdownDays: 13 },
  { id: 'ev-4',  date: '2026-05-08', kind: 'FAMILY', title: '家长会' },
];

export const calendarHandlers = [
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
