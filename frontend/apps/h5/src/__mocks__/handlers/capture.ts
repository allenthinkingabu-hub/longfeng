import { http, HttpResponse } from 'msw';

const FAKE_FILE_KEY = 'mock-file-key-001';

export const captureHandlers = [
  http.post('/api/v1/files/presign', () => {
    return HttpResponse.json({
      file_key: FAKE_FILE_KEY,
      upload_url: 'https://mock-oss.example.com/upload',
    });
  }),

  // Mock OSS direct-upload · SC-01 directUpload step
  http.put('https://mock-oss.example.com/upload', () => new HttpResponse(null, { status: 200 })),

  http.post(`/api/v1/files/complete/${FAKE_FILE_KEY}`, () => {
    return HttpResponse.json({ file_key: FAKE_FILE_KEY, status: 'READY' });
  }),

  http.post('/api/v1/wrongbook/items', () => {
    // SC-01: 同时 push 到共享 WRONGBOOK_LIST · 让 GET /items list +1
    const newItem = {
      id: `mock-item-created-${Date.now()}`,
      subject: 'math',
      stem_text: '（MSW mock）已知函数 f(x) = x² + 2x，求 f(1) 的值。',
      tags: [] as string[],
      status: 'analyzing',
      mastery: 0,
      image_url: null,
      created_at: new Date().toISOString(),
      version: 1,
    };
    // 动态 import 避免循环依赖
    void import('./wrongbook').then(({ pushWrongbookItem }) => {
      pushWrongbookItem(newItem);
    });
    return HttpResponse.json(newItem, { status: 201 });
  }),
];
