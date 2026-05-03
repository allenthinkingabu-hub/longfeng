/**
 * useEventSource.ts
 * S7 · FE-02-capture-flow
 *
 * SSE 订阅 hook，对接 /api/ai/stream/{taskId}
 * D-AI-Cancel: 关闭 EventSource 时 POST /api/ai/cancel/{taskId}
 *
 * 设计约束（STYLE-TRUTH §3 Mood C / P03 spec §5）：
 *  - 支持 4 步 AnalyzeStreamEvent: STEP_START / STEP_DONE / PARTIAL_JSON / DONE / FAIL / CANCELLED
 *  - a11y: aria-live="polite" 由调用方挂载；prefers-reduced-motion 由 CSS 层处理
 *  - 超时 10s 无首字节 → onSlow 回调（切备用模型）
 *  - 网络断连 → 最多重试 3 次后 onFail
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

  const esRef = useRef<EventSource | null>(null);
  const slowTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const retriesRef = useRef(0);
  const cancelledRef = useRef(false);
  const firstByteRef = useRef(false);

  const cleanup = useCallback(() => {
    if (esRef.current) {
      esRef.current.close();
      esRef.current = null;
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

  const connect = useCallback(() => {
    if (cancelledRef.current) return;

    const url = `${baseUrl}/api/ai/stream/${taskId}`;
    const es = new EventSource(url);
    esRef.current = es;

    // Slow first-byte timer
    slowTimerRef.current = setTimeout(() => {
      if (!firstByteRef.current && !cancelledRef.current) {
        setStatus('SLOW');
        onSlow?.();
      }
    }, slowThresholdMs);

    es.onmessage = (e: MessageEvent) => {
      if (cancelledRef.current) return;
      firstByteRef.current = true;
      if (slowTimerRef.current) {
        clearTimeout(slowTimerRef.current);
        slowTimerRef.current = null;
      }
      retriesRef.current = 0; // reset on success

      let event: AnalyzeStreamEvent;
      try {
        event = JSON.parse(e.data) as AnalyzeStreamEvent;
      } catch {
        return;
      }

      switch (event.type) {
        case 'STEP_START':
          if (event.step) {
            const s = event.step;
            setStatus(`STEP_${s}` as StreamStatus);
            setStepStatuses((prev) => ({ ...prev, [s]: 'now' }));
            onStep?.(s, 'start');
          }
          break;

        case 'STEP_DONE':
          if (event.step) {
            const s = event.step;
            setStepStatuses((prev) => ({ ...prev, [s]: 'done' }));
            if (event.durationMs) {
              setStepDurations((prev) => ({ ...prev, [s]: event.durationMs }));
            }
            onStep?.(s, 'done', event.durationMs);
          }
          break;

        case 'PARTIAL_JSON':
          if (event.partialJson) {
            setPartialJson((prev) => prev + event.partialJson);
            onPartialJson?.(event.partialJson!);
          }
          break;

        case 'DONE':
          setStatus('SUCCEEDED');
          setStepStatuses({ 1: 'done', 2: 'done', 3: 'done', 4: 'done' });
          cleanup();
          onDone?.();
          break;

        case 'FAIL':
          setStatus('FAILED');
          if (event.step) {
            const s = event.step;
            setStepStatuses((prev) => ({ ...prev, [s]: 'fail' }));
          }
          cleanup();
          onFail?.(event.errorCode);
          break;

        case 'CANCELLED':
          cancelledRef.current = true;
          setStatus('CANCELLED');
          cleanup();
          onCancelled?.();
          break;
      }
    };

    es.onerror = () => {
      if (cancelledRef.current) return;
      cleanup();
      if (retriesRef.current < maxRetries) {
        retriesRef.current += 1;
        // exponential back-off: 500ms * 2^n
        const delay = 500 * Math.pow(2, retriesRef.current - 1);
        setTimeout(connect, delay);
      } else {
        setStatus('FAILED');
        onFail?.('NETWORK_ERROR');
      }
    };
  }, [baseUrl, cleanup, maxRetries, onCancelled, onDone, onFail, onPartialJson, onSlow, onStep, slowThresholdMs, taskId]);

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
