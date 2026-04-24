// S7 · file-service typed client
import type { PresignRequest, PresignResponse, FileCompleteResponse } from '../types';
import { httpClient } from '../http';

export const filesClient = {
  presign(req: PresignRequest): Promise<PresignResponse> {
    return httpClient.post('/files/presign', req);
  },

  complete(fileKey: string): Promise<FileCompleteResponse> {
    return httpClient.post(`/files/complete/${fileKey}`);
  },

  /** 前端直传 OSS · 不过网关 · 只需 PUT 到 presign 返回的 uploadUrl */
  async directUpload(uploadUrl: string, blob: Blob): Promise<void> {
    const res = await fetch(uploadUrl, {
      method: 'PUT',
      body: blob,
      headers: { 'Content-Type': blob.type || 'application/octet-stream' },
    });
    if (!res.ok) {
      throw new Error(`direct upload failed: ${res.status}`);
    }
  },
};
