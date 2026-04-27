import { http, HttpResponse } from 'msw';
import fixture from '../fixtures/wrongbook-list.json';

export const wrongbookHandlers = [
  http.get('/api/v1/wrongbook/items', () => {
    return HttpResponse.json(fixture.listResponse);
  }),

  http.get('/api/v1/wrongbook/tags', () => {
    return HttpResponse.json(fixture.tagsResponse);
  }),

  http.get('/api/v1/wrongbook/items/:id', ({ params }) => {
    const item = fixture.listResponse.items.find((i) => i.id === params.id);
    if (!item) return new HttpResponse(null, { status: 404 });
    return HttpResponse.json(item);
  }),
];
