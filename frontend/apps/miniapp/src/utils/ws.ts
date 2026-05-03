// S7-S8 miniapp · WebSocket 封装 · 替代 H5 SSE (小程序无 EventSource)
// 与 ai-analysis-service AnalyzeWebSocketHandler /ws/analyze/{taskId} 对齐
// D-WS · ADR (落地计划 §0.9 + §S7) · 与 H5 useEventSource hook 同语义

const WS_BASE = 'wss://api.longfeng.local/ws';

export interface AnalyzeEvent {
  type: 'stage' | 'partial' | 'final' | 'error' | 'fallback';
  stage?: 'upload' | 'ocr' | 'analyze' | 'save';
  progress?: number;
  text?: string;
  data?: unknown;
  message?: string;
  provider?: string;
}

export interface AnalyzeWSHandlers {
  onStage?: (stage: string, progress: number) => void;
  onPartial?: (text: string) => void;
  onFinal?: (data: unknown) => void;
  onError?: (msg: string) => void;
  onFallback?: (provider: string) => void;
  onClose?: () => void;
}

export class AnalyzeWS {
  private socketTask: WechatMiniprogram.SocketTask | null = null;
  private handlers: AnalyzeWSHandlers;
  private taskId: string;
  private closed = false;

  constructor(taskId: string, handlers: AnalyzeWSHandlers) {
    this.taskId = taskId;
    this.handlers = handlers;
  }

  connect(): void {
    const token = wx.getStorageSync('access_token') || '';
    this.socketTask = wx.connectSocket({
      url: `${WS_BASE}/analyze/${this.taskId}`,
      header: token ? { Authorization: `Bearer ${token}` } : {},
    });

    this.socketTask.onOpen(() => {
      // open ack
    });

    this.socketTask.onMessage((res) => {
      try {
        const evt: AnalyzeEvent = JSON.parse(String(res.data));
        switch (evt.type) {
          case 'stage':
            this.handlers.onStage?.(evt.stage || 'upload', evt.progress ?? 0);
            break;
          case 'partial':
            this.handlers.onPartial?.(evt.text || '');
            break;
          case 'final':
            this.handlers.onFinal?.(evt.data);
            break;
          case 'error':
            this.handlers.onError?.(evt.message || 'unknown');
            break;
          case 'fallback':
            this.handlers.onFallback?.(evt.provider || 'backup');
            break;
        }
      } catch (e) {
        this.handlers.onError?.(String(e));
      }
    });

    this.socketTask.onError((e) => {
      if (!this.closed) this.handlers.onError?.(e.errMsg || 'ws error');
    });

    this.socketTask.onClose(() => {
      this.closed = true;
      this.handlers.onClose?.();
    });
  }

  cancel(): void {
    if (this.socketTask && !this.closed) {
      this.closed = true;
      try {
        this.socketTask.send({ data: JSON.stringify({ type: 'cancel' }) });
      } catch {
        // ignore
      }
      this.socketTask.close({ code: 1000 });
    }
  }
}
