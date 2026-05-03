/**
 * useEventSource.ts
 * S7 · FE-02-capture-flow
 *
 * SSE 订阅 hook，对接 /api/ai/stream/{taskId}
 * D-AI-Cancel: 关闭连接时 POST /api/ai/cancel/{taskId}
 *
 * 设计约束（STYLE-TRUTH §3 Mood C / P03 spec §5）：
 *  - 支持 4 步 AnalyzeStreamEvent: STEP_START / STEP_DONE / PARTIAL_JSON / DONE / FAIL / CANCELLED
 *  - a11y: aria-live="polite" 由调用方挂载；prefers-reduced-motion 由 CSS 层处理
 *  - 超时 10s 无首字节 → onSlow 回调（切备用模型）
 *  - 网络断连 → 最多重试 3 次后 onFail
 *
 * 实现说明（重要）：
 *  - 使用 fetch + ReadableStream 手动解析 SSE，而非浏览器原生 EventSource。
 *    原因：MSW (B 轨 e2e) 通过 Service Worker 拦截 fetch，但无法拦截 EventSource
 *    （EventSource 是独立的浏览器原生 API，不走 fetch）。改用 fetch-based SSE
 *    后，MSW handler 返回的 ReadableStream SSE 响应才能被消费。
 *  - 命名保留 useEventSource 不变（对外 API 不变）。
 */

import { useCallback, useEffect, useRef, useState } from 'react';

// ─── Types ─────────────────────────────────────────────────────────────────

export type StreamStep = 1 | 2 | 3 | 4;
export type StreamStatus = 'QUEUED' | `STEP_${StreamStep}` | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'SLOW';

export interface AnalyzeStreamEvent {
  type: 'STEP_START' | 'STEP_DONE' | 'PARTIAL_JSON' | 'DONE' | 'FAIL' | 'CANCELLED';
  step?: StreamStep;
  durationMs?: number;
  partialJson?: string;
  errorCode?: string;
}

export interface UseEventSourceOptions {
  taskId: string;
  /** Base URL prefix; defaults to '' (same origin) */
  baseUrl?: string;
  /** ms before treating first byte as SLOW; default 10_000 */
  slowThresholdMs?: number;
  /** Max SSE reconnect attempts on error; default 3 */
  maxRetries?: number;
  onStep?: (step: StreamStep, status: 'start' | 'done', durationMs?: number) => void;
  onPartialJson?: (fragment: string) => void;
  onDone?: () => void;
  onFail?: (errorCode?: string) => void;
  onSlow?: () => void;
  onCancelled?: () => void;
}

export interface UseEventSourceReturn {
  status: StreamStatus;
  stepStatuses: Record<StreamStep, 'wait' | 'now' | 'done' | 'fail'>;
  stepDurations: Partial<Record<StreamStep, number>>;
  partialJson: string;
  cancel: () => Promise<void>;
}

const STEP_LABELS: Record<StreamStep, string> = {
  1: '图像预处理',
  2: 'OCR 题干',
  3: '错因诊断',
  4: '生成解法',
};
export { STEP_LABELS };

const DEFAULT_SLOW_MS = 10_000;
const DEFAULT_MAX_RETRIES = 3;

// ─── Hook ──────────────────────────────────────────────────────────────────

export function useEventSource({
  taskId,
  baseUrl = '',
  slowThresholdMs = DEFAULT_SLOW_MS,
  maxRetries = DEFAULT_MAX_RETRIES,
  onStep,
  onPartialJson,
  onDone,
  onFail,
  onSlow,
  onCancelled,
}: UseEventSourceOptions): UseEventSourceReturn {
  const [status, setStatus] = useState<StreamStatus>('QUEUED');
  const [stepStatuses, setStepStatuses] = useState<Record<StreamStep, 'wait' | 'now' | 'done' | 'fail'>>({
    1: 'wait', 2: 'wait', 3: 'wait', 4: 'wait',
  });
  const [stepDurations, setStepDurations] = useState<Partial<Record<StreamStep, number>>>({});
  const [partialJson, setPartialJson] = useState('');

  const abortRef = useRef<AbortController | null>(null);
  const slowTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const retriesRef = useRef(0);
  const cancelledRef = useRef(false);
  const firstByteRef = useRef(false);

  const cleanup = useCallback(() => {
    if (abortRef.current) {
      abortRef.current.abort();
      abortRef.current = null;
    }
    if (slowTimerRef.current) {
      clearTimeout(slowTimerRef.current);
      slowTimerRef.current = null;
    }
  }, []);

  const cancel = useCallback(async () => {
    cancelledRef.current = true;
    cleanup();
    setStatus('CANCELLED');
    try {
      await fetch(`${baseUrl}/api/ai/cancel/${taskId}`, { method: 'POST' });
    } catch {
      // cancel best-effort; don't block UI
    }
    onCancelled?.();
  }, [baseUrl, cleanup, onCancelled, taskId]);

  const handleEvent = useCallback((event: AnalyzeStreamEvent): boolean => {
    // 返回 true 表示已收到终结事件 (DONE/FAIL/CANCELLED)
    switch (event.type) {
      case 'STEP_START':
        if (event.step) {
          const s = event.step;
          setStatus(`STEP_${s}` as StreamStatus);
          setStepStatuses((prev) => ({ ...prev, [s]: 'now' }));
          onStep?.(s, 'start');
        }
        return false;

      case 'STEP_DONE':
        if (event.step) {
          const s = event.step;
          setStepStatuses((prev) => ({ ...prev, [s]: 'done' }));
          if (event.durationMs) {
            setStepDurations((prev) => ({ ...prev, [s]: event.durationMs }));
          }
          onStep?.(s, 'done', event.durationMs);
        }
        return false;

      case 'PARTIAL_JSON':
        if (event.partialJson) {
          setPartialJson((prev) => prev + event.partialJson!);
          onPartialJson?.(event.partialJson);
        }
        return false;

      case 'DONE':
        setStatus('SUCCEEDED');
        setStepStatuses({ 1: 'done', 2: 'done', 3: 'done', 4: 'done' });
        onDone?.();
        return true;

      case 'FAIL':
        setStatus('FAILED');
        if (event.step) {
          const s = event.step;
          setStepStatuses((prev) => ({ ...prev, [s]: 'fail' }));
        }
        onFail?.(event.errorCode);
        return true;

      case 'CANCELLED':
        cancelledRef.current = true;
        setStatus('CANCELLED');
        onCancelled?.();
        return true;
    }
    return false;
  }, [onStep, onPartialJson, onDone, onFail, onCancelled]);

  const connect = useCallback(() => {
    if (cancelledRef.current) return;

    const url = `${baseUrl}/api/ai/stream/${taskId}`;
    const ac = new AbortController();
    abortRef.current = ac;

    // Slow first-byte timer
    slowTimerRef.current = setTimeout(() => {
      if (!firstByteRef.current && !cancelledRef.current) {
        setStatus('SLOW');
        onSlow?.();
      }
    }, slowThresholdMs);

    const onError = () => {
      if (cancelledRef.current) return;
      cleanup();
      if (retriesRef.current < maxRetries) {
        retriesRef.current += 1;
        const delay = 500 * Math.pow(2, retriesRef.current - 1);
        setTimeout(connect, delay);
      } else {
        setStatus('FAILED');
        onFail?.('NETWORK_ERROR');
      }
    };

    // 启动 fetch + 流式解析（IIFE 内部 async）
    void (async () => {
      try {
        const resp = await fetch(url, {
          method: 'GET',
          headers: { Accept: 'text/event-stream' },
          signal: ac.signal,
          cache: 'no-store',
        });
        if (cancelledRef.current) return;
        if (!resp.ok || !resp.body) {
          onError();
          return;
        }

        const reader = resp.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';
        let terminated = false;

        while (true) {
          if (cancelledRef.current) {
            try { await reader.cancel(); } catch { /* noop */ }
            return;
          }
          const { value, done } = await reader.read();
          if (done) break;

          firstByteRef.current = true;
          if (slowTimerRef.current) {
            clearTimeout(slowTimerRef.current);
            slowTimerRef.current = null;
          }
          retriesRef.current = 0; // reset on success

          buffer += decoder.decode(value, { stream: true });

          // SSE: events delimited by 双换行（\n\n 或 \r\n\r\n）
          let sepIdx: number;
          // eslint-disable-next-line no-cond-assign
          while ((sepIdx = buffer.search(/\r?\n\r?\n/)) !== -1) {
            const rawEvent = buffer.slice(0, sepIdx);
            const matchLen = buffer.slice(sepIdx).match(/^\r?\n\r?\n/)?.[0].length ?? 2;
            buffer = buffer.slice(sepIdx + matchLen);
            // 一个 event 可能包含多行 "data: ..."
            const dataLines = rawEvent
              .split(/\r?\n/)
              .filter((l) => l.startsWith('data:'))
              .map((l) => l.replace(/^data:\s?/, ''));
            if (dataLines.length === 0) continue;
            const dataStr = dataLines.join('\n');
            let evt: AnalyzeStreamEvent | null = null;
            try {
              evt = JSON.parse(dataStr) as AnalyzeStreamEvent;
            } catch {
              continue;
            }
            if (cancelledRef.current) return;
            const isTerminal = handleEvent(evt);
            if (isTerminal) {
              terminated = true;
              break;
            }
          }
          if (terminated) {
            cleanup();
            return;
          }
        }
        // 流自然结束（无 DONE 事件）→ 视为完成（不触发 onError）
        cleanup();
      } catch (err) {
        // AbortError = 主动取消 / cleanup，不算失败
        if ((err as { name?: string })?.name === 'AbortError') return;
        if (cancelledRef.current) return;
        onError();
      }
    })();
  }, [baseUrl, cleanup, handleEvent, maxRetries, onFail, onSlow, slowThresholdMs, taskId]);

  useEffect(() => {
    cancelledRef.current = false;
    firstByteRef.current = false;
    retriesRef.current = 0;
    connect();
    return () => {
      cleanup();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taskId]);

  return { status, stepStatuses, stepDurations, partialJson, cancel };
}
