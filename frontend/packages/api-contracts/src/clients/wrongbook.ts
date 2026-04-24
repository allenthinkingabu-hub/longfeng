// S7 · wrongbook typed client · re-export 唯一 API 调用入口（禁 fetch）
import type {
  WrongItemVO,
  WrongItemCreate,
  WrongItemListParams,
  WrongItemListResponse,
  TagUpdatePayload,
} from '../types';
import { httpClient } from '../http';

export const wrongbookClient = {
  list(params: WrongItemListParams): Promise<WrongItemListResponse> {
    return httpClient.get('/wrongbook/items', { params });
  },

  get(id: string): Promise<WrongItemVO> {
    return httpClient.get(`/wrongbook/items/${id}`);
  },

  create(payload: WrongItemCreate): Promise<WrongItemVO> {
    return httpClient.post('/wrongbook/items', payload);
  },

  updateTags(id: string, payload: TagUpdatePayload): Promise<void> {
    return httpClient.patch(`/wrongbook/items/${id}/tags`, payload.tags, {
      headers: { 'If-Match': String(payload.version) },
    });
  },

  getTags(): Promise<{ tags: string[] }> {
    return httpClient.get('/wrongbook/tags');
  },
};
