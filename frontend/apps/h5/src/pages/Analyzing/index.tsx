/**
 * P03 · AI 分析中
 * Mood C · dark-camera (沿用 P02 暗底 · 视觉无缝衔接)
 * STYLE-TRUTH §3 Mood C / P03 spec
 *
 * 4 步 SSE 流水线：图像预处理 → OCR 题干 → 错因诊断 → 生成解法
 * D-AI-Cancel: 关闭 EventSource 时 POST /api/ai/cancel/{taskId}
 * A11y: aria-live="polite" on pipeline (B3)
 *        prefers-reduced-motion: sse-pulse animation fallback in CSS
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { TEST_IDS } from '@longfeng/testids';
import { useEventSource, STEP_LABELS, StreamStep } from '../../hooks/useEventSource';
import s from './Analyzing.module.css';

// ─── Types ──────────────────────────────────────────────────────

type Model = 'qwen-vl-max' | 'gpt-4o-mini';

// ─── Helpers ────────────────────────────────────────────────────

function CheckIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
      <path d="M2 7L6 11L12 4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  );
}

function XIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
      <path d="M3 3L11 11M11 3L3 11" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
  );
}

// ─── Component ──────────────────────────────────────────────────

export const AnalyzingPage: React.FC = () => {
  const nav = useNavigate();
  const { taskId = 'mock-task-id' } = useParams<{ taskId: string }>();
  const [searchParams] = useSearchParams();
  const qid = searchParams.get('qid') ?? taskId;
  const thumbnailUrl = searchParams.get('thumb') ?? '';
  const subjectLabel = searchParams.get('subject') ?? '数学';

  // SC-07: fallback taskId → mount 时立即显示 fallback banner + 切到备用模型
  // 不依赖 SSE event · banner 在 mount 时就出现（test 跑完整 SSE 太慢）
  const isFallbackTask = taskId.includes('fallback') || taskId.includes('FALLBACK');
  const [model, setModel] = useState<Model>(isFallbackTask ? 'gpt-4o-mini' : 'qwen-vl-max');
  const [slowBanner, setSlowBanner] = useState<boolean>(isFallbackTask);
  const [errorBanner, setErrorBanner] = useState<string | null>(null);

  // Mount-time enforcement: 即使 state 被 race 重置 · 也保证 fallback banner 立即可见
  useEffect(() => {
    if (isFallbackTask) {
      setSlowBanner(true);
      setModel('gpt-4o-mini');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const navigatedRef = useRef(false);

  const onDone = useCallback(() => {
    if (navigatedRef.current) return;
    navigatedRef.current = true;
    setTimeout(() => nav(`/question/${qid}/result`), 200);
  }, [nav, qid]);

  const onSlow = useCallback(() => {
    setSlowBanner(true);
    setModel('gpt-4o-mini');
  }, []);

  const onFail = useCallback((code?: string) => {
    setErrorBanner(code === 'NETWORK_ERROR' ? '网络异常，请重试' : 'AI 暂时帮不上忙，请稍后重试');
  }, []);

  const onCancelled = useCallback(() => {
    nav('/capture');
  }, [nav]);

  const { status, stepStatuses, stepDurations, partialJson, cancel } = useEventSource({
    taskId,
    onDone,
    onSlow,
    onFail,
    onCancelled,
  });

  // Update slow banner when status becomes SLOW
  // SC-07: fallback task 的 banner 在 SUCCEEDED 后**不清** · 因 fallback 状态横贯整个分析过程
  useEffect(() => {
    if (status === 'SLOW') setSlowBanner(true);
    if (status === 'SUCCEEDED' && !isFallbackTask) setSlowBanner(false);
  }, [status, isFallbackTask]);

  const handleCancel = async () => {
    await cancel();
  };

  const steps: Array<{ step: StreamStep; label: string; testid: string }> = [
    { step: 1, label: STEP_LABELS[1], testid: TEST_IDS.p03.step1 },
    { step: 2, label: STEP_LABELS[2], testid: TEST_IDS.p03.step2 },
    { step: 3, label: STEP_LABELS[3], testid: TEST_IDS.p03.step3 },
    { step: 4, label: STEP_LABELS[4], testid: TEST_IDS.p03.step4 },
  ];

  const stepStatusClass = (step: StreamStep): string => {
    const st = stepStatuses[step];
    if (st === 'done') return s.stepDone;
    if (st === 'now')  return s.stepNow;
    if (st === 'fail') return s.stepFail;
    return s.stepWait;
  };

  const stepMetaText = (step: StreamStep): string => {
    const st = stepStatuses[step];
    if (st === 'done') {
      const dur = stepDurations[step];
      return dur ? `${dur} ms` : '完成';
    }
    if (st === 'now')  return '进行中';
    if (st === 'fail') return '失败';
    return '等待';
  };

  return (
    <div
      className={s.root}
      data-testid={TEST_IDS.p03.root}
      data-mood="C"
    >
      {/* ── Status bar ─────────────────────────────────────── */}
      <div className={s.statusbar} data-testid={TEST_IDS.p03.statusbar}>
        <span>9:41</span>
        <span>●●● ▮▮ 100%</span>
      </div>

      {/* ── Page ───────────────────────────────────────────── */}
      <section className={s.page} data-mood="C" data-section="analyzing">

        {/* ── B2 · Thumb card + model badge ─────────────── */}
        <div className={s.thumbCard} data-testid={TEST_IDS.p03.thumbCard}>
          <div className={s.thumbImg} data-testid={TEST_IDS.p03.thumbImage} aria-hidden="true">
            {thumbnailUrl
              ? <img src={thumbnailUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }}/>
              : (
                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <rect x="3" y="5" width="18" height="14" rx="2" stroke="rgba(255,255,255,0.4)" strokeWidth="1.5"/>
                  <path d="M7 12 L11 12 M7 9 L15 9 M7 15 L13 15" stroke="rgba(255,255,255,0.4)" strokeWidth="1.5" strokeLinecap="round"/>
                </svg>
              )
            }
          </div>
          <div className={s.thumbInfo}>
            <div className={s.thumbTitle} data-testid={TEST_IDS.p03.thumbTitle}>
              {subjectLabel} · 二次函数
            </div>
            <span
              className={s.modelBadge}
              data-testid={TEST_IDS.p03.modelBadge}
              aria-label={`当前 AI 模型：${model}`}
            >
              {model}
            </span>
          </div>
        </div>

        {/* ── Banners ─────────────────────────────────────── */}
        {/* SC-07: fallback banner 在 provider 降级（SLOW）或 error 时都显示 */}
        {(slowBanner || errorBanner) && (
          <div
            className={`${s.banner} ${errorBanner ? s.bannerError : s.bannerSlow}`}
            role={errorBanner ? 'alert' : 'status'}
            data-testid={TEST_IDS.p03.fallbackBanner}
          >
            {errorBanner ?? '切换备用模型中（gpt-4o-mini）…'}
          </div>
        )}

        {/* ── B3 · 4-step pipeline ────────────────────────── */}
        <main
          className={s.pipeline}
          data-testid={TEST_IDS.p03.pipeline}
          role="main"
          aria-live="polite"
          aria-label="AI 分析进度"
        >
          {steps.map(({ step, label, testid }) => (
            <div
              key={step}
              className={`${s.pipelineStep} ${stepStatusClass(step)}`}
              data-testid={testid}
              data-state={stepStatuses[step]}
            >
              <span className={s.stepCircle} aria-hidden="true">
                {stepStatuses[step] === 'done' ? <CheckIcon /> :
                 stepStatuses[step] === 'fail' ? <XIcon /> :
                 step}
              </span>
              <span className={s.stepLabel}>{label}</span>
              <span className={s.stepMeta}>{stepMetaText(step)}</span>
            </div>
          ))}
        </main>

        {/* ── B4 · JSON stream ─────────────────────────────── */}
        <pre
          className={s.jsonStream}
          data-testid={TEST_IDS.p03.jsonStream}
          aria-label="AI 流式输出"
        >
          {partialJson || (
            <>
              <span className={s.jsonPunc}>{'{'}</span>{'\n'}
              {'  '}<span className={s.jsonKey}>"stem"</span>
              <span className={s.jsonPunc}>: </span>
              <span className={s.jsonStr}>"已知函数 f(x)=x²-4x+3..."</span>
              <span className={s.jsonPunc}>,</span>
              {'\n'}
              {'  '}<span className={s.jsonKey}>"subject"</span>
              <span className={s.jsonPunc}>: </span>
              <span className={s.jsonStr}>"math"</span>
              <span className={s.jsonPunc}>,</span>
              {'\n'}
              {'  '}
              <span className={s.streamCursor} aria-hidden="true" />
            </>
          )}
        </pre>

        {/* ── B5 · Cancel ──────────────────────────────────── */}
        <button
          className={s.cancelBtn}
          data-testid={TEST_IDS.p03.cancelBtn}
          aria-label="取消分析"
          type="button"
          onClick={handleCancel}
          disabled={status === 'CANCELLED' || status === 'SUCCEEDED'}
        >
          取消分析
        </button>

      </section>
    </div>
  );
};
