// S7 · ai-analysis typed client · 含 SSE 订阅
import type { SimilarResponse, ExplainChunk } from '../types';
import { httpClient } from '../http';

export const analysisClient = {
  similar(itemId: string, k = 3): Promise<SimilarResponse> {
    return httpClient.get(`/analysis/${itemId}/similar`, { params: { k } });
  },

  /** SSE 订阅 · onChunk 流式回调 · 返回 close 函数 */
  explainStream(itemId: string, onChunk: (chunk: ExplainChunk) => void, onError?: (e: unknown) => void): () => void {
    const es = new EventSource(`/api/v1/analysis/${itemId}`);
    es.onmessage = (e) => {
      try {
        const data = JSON.parse(e.data) as ExplainChunk;
        onChunk(data);
        if (data.done) es.close();
      } catch (err) {
        onError?.(err);
      }
    };
    es.onerror = (e) => {
      onError?.(e);
      es.close();
    };
    return () => es.close();
  },
};
