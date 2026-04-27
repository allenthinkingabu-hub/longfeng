import { http, HttpResponse } from 'msw';

const FAKE_FILE_KEY = 'mock-file-key-001';

export const captureHandlers = [
  http.post('/api/v1/files/presign', () => {
    return HttpResponse.json({
      file_key: FAKE_FILE_KEY,
      upload_url: 'https://mock-oss.example.com/upload',
    });
  }),

  http.post(`/api/v1/files/complete/${FAKE_FILE_KEY}`, () => {
    return HttpResponse.json({ file_key: FAKE_FILE_KEY, status: 'READY' });
  }),

  http.post('/api/v1/wrongbook/items', () => {
    return HttpResponse.json({
      id: 'mock-item-created-001',
      subject: 'math',
      stem_text: '（MSW mock）已知函数 f(x) = x² + 2x，求 f(1) 的值。',
      tags: [],
      status: 'analyzing',
      mastery: 0,
      image_url: null,
      created_at: new Date().toISOString(),
      version: 1,
    }, { status: 201 });
  }),
];
