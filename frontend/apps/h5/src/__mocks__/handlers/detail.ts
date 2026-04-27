import { http, HttpResponse } from 'msw';
import fixture from '../fixtures/wrongbook-list.json';

export const detailHandlers = [
  http.patch('/api/v1/wrongbook/items/:id/tags', async ({ request }) => {
    const body = await request.json() as string[];
    return HttpResponse.json({ tags: body }, { status: 200 });
  }),

  http.get('/api/v1/analysis/:itemId/similar', () => {
    return HttpResponse.json({
      items: fixture.listResponse.items.slice(1, 3).map((item) => ({
        id: item.id,
        stem_text: item.stem_text,
        subject: item.subject,
        distance: 0.18,
      })),
    });
  }),
];
