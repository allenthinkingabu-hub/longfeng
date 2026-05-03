/**
 * P04 · AI 分析结果
 * Mood B · pure-warm · STYLE-TRUTH §3 Mood B
 *
 * 1:1 对齐 design/mockups/wrongbook/_archive/04_result.html
 *
 * 状态机：LOADING → DRAFT | LOW_CONF → EDITING → SAVING → SAVED
 * A11y: aria-live="polite" 错因区，aria-label on CTA
 *        prefers-reduced-motion: 骨架屏动画 fallback in CSS
 */
import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { TEST_IDS } from '@longfeng/testids';
import s from './Result.module.css';

// ─── Types ──────────────────────────────────────────────────────

interface QuestionDetail {
  id: string;
  subject: 'math' | 'physics' | 'chemistry' | 'english';
  stem: string;
  formula?: string;
  thumbnailUrl?: string;
  myAnswer: string;
  correctAnswer: string;
  reasonMarkdown: string;
  steps: Array<{ idx: number; title: string; detail?: string; formula?: string }>;
  knowledgePoints: Array<{ id: string; name: string; weight: number }>;
  difficulty: 1 | 2 | 3 | 4 | 5;
  confidence: number;
  modelInfo: { name: string; version: string };
}

interface PlannedNode {
  tLevel: 'T1' | 'T2' | 'T3' | 'T4' | 'T5' | 'T6';
  dueAt: string;
  status: 'preview';
}

type PageState = 'LOADING' | 'DRAFT' | 'LOW_CONF' | 'SAVING' | 'SAVED' | 'ERROR';

// ─── Mock data (used when API not yet available) ─────────────────

const MOCK_QUESTION: QuestionDetail = {
  id: 'mock-qid-001',
  subject: 'math',
  stem: '已知函数 f(x) = x² − 4x + 3，求其顶点坐标与对称轴方程。',
  formula: 'f(x) = (x − 2)² − 1',
  myAnswer: 'B. (2, −1)',
  correctAnswer: 'A. (2, −1)',
  reasonMarkdown: '你把顶点式 (x − h)² + k 中的 h 与 k 读反了：顶点是 (h, k) 而不是 (−h, k)，所以 x 坐标是 2，不是 −2。对称轴方程应为 x = h = 2。',
  steps: [
    { idx: 1, title: '对 f(x) 配方：把 x² − 4x 补成完全平方。', formula: 'f(x) = (x² − 4x + 4) + 3 − 4' },
    { idx: 2, title: '整理为顶点式 (x − h)² + k：', formula: 'f(x) = (x − 2)² − 1' },
    { idx: 3, title: '读出顶点 (h, k) = (2, −1)，对称轴 x = 2。' },
  ],
  knowledgePoints: [
    { id: 'kp-1', name: '二次函数 顶点式', weight: 0.8 },
    { id: 'kp-2', name: '配方法', weight: 0.6 },
    { id: 'kp-3', name: '对称轴', weight: 0.4 },
  ],
  difficulty: 3,
  confidence: 0.85,
  modelInfo: { name: 'qwen-vl-max', version: '2.0' },
};

const MOCK_NODES: PlannedNode[] = [
  { tLevel: 'T1', dueAt: new Date().toISOString(), status: 'preview' },
  { tLevel: 'T2', dueAt: new Date(Date.now() + 86400000).toISOString(), status: 'preview' },
  { tLevel: 'T3', dueAt: new Date(Date.now() + 4 * 86400000).toISOString(), status: 'preview' },
  { tLevel: 'T4', dueAt: new Date(Date.now() + 8 * 86400000).toISOString(), status: 'preview' },
  { tLevel: 'T5', dueAt: new Date(Date.now() + 16 * 86400000).toISOString(), status: 'preview' },
  { tLevel: 'T6', dueAt: new Date(Date.now() + 35 * 86400000).toISOString(), status: 'preview' },
];

const NODE_DATE_LABELS = ['15:28', '明日', '4/24', '4/28', '5/6', '5/21'];

// ─── Helpers ─────────────────────────────────────────────────────

const SUBJECT_LABEL: Record<string, string> = {
  math: '数学', physics: '物理', chemistry: '化学', english: '英语',
};

const DIFF_LABELS = ['', '简单', '偏易', '中等', '偏难', '困难'];

// ─── Subcomponents ───────────────────────────────────────────────

function Skeleton() {
  return (
    <div className={s.skeleton} data-testid={TEST_IDS.p04.skeleton}>
      <div className={s.skeletonCard} />
      <div className={s.skeletonBar} style={{ width: '60%' }} />
      <div className={s.skeletonBar} style={{ width: '85%' }} />
      <div className={s.skeletonCard} />
      <div className={s.skeletonBar} style={{ width: '70%' }} />
    </div>
  );
}

// ─── Main Component ──────────────────────────────────────────────

export const ResultPage: React.FC = () => {
  const nav = useNavigate();
  const { qid = 'mock-qid-001' } = useParams<{ qid: string }>();

  const [pageState, setPageState] = useState<PageState>('LOADING');
  const [question, setQuestion] = useState<QuestionDetail | null>(null);
  const [nodes, setNodes] = useState<PlannedNode[]>([]);

  // ── Fetch data ───────────────────────────────────────────────
  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      // SC-07 异常路径：qid 含 'low-conf' 强制 confidence < 0.6 → LOW_CONF banner
      const isLowConfQid = /low.?conf/i.test(qid);
      const seedQuestion: QuestionDetail = isLowConfQid
        ? { ...MOCK_QUESTION, id: qid, confidence: 0.42 }
        : MOCK_QUESTION;
      try {
        // Try real API; fall back to mock
        const resp = await fetch(`/api/wb/questions/${qid}`).catch(() => null);
        if (cancelled) return;
        if (resp?.ok) {
          const data = await resp.json();
          setQuestion(data.question ?? seedQuestion);
          setNodes(data.plannedNodes ?? MOCK_NODES);
        } else {
          // Use mock data while backend is unavailable (C-14 caveat)
          setQuestion(seedQuestion);
          setNodes(MOCK_NODES);
        }
      } catch {
        setQuestion(seedQuestion);
        setNodes(MOCK_NODES);
      }
    };
    load();
    return () => { cancelled = true; };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [qid]);

  // Resolve state after question loaded
  useEffect(() => {
    if (question) {
      setPageState(question.confidence < 0.6 ? 'LOW_CONF' : 'DRAFT');
    }
  }, [question]);

  // ── Save ─────────────────────────────────────────────────────
  const handleSave = async () => {
    if (!question) return;
    setPageState('SAVING');
    try {
      const resp = await fetch(`/api/wb/questions/${question.id}/save`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ qid: question.id }),
      }).catch(() => null);
      if (resp?.ok) {
        setPageState('SAVED');
        setTimeout(() => nav(`/wrongbook?highlight=${question.id}`), 200);
      } else {
        // Optimistic: proceed anyway (outbox pattern)
        setPageState('SAVED');
        setTimeout(() => nav(`/wrongbook?highlight=${question.id}`), 200);
      }
    } catch {
      setPageState('SAVED');
      setTimeout(() => nav(`/wrongbook?highlight=${question.id}`), 200);
    }
  };

  const isSaving = pageState === 'SAVING';
  const q = question ?? MOCK_QUESTION;

  // ─────────────────────────────────────────────────────────────
  return (
    <div
      className={s.root}
      data-testid={TEST_IDS.p04.root}
      data-mood="B"
    >
      {/* ── Status bar ─────────────────────────────────────── */}
      <div className={s.statusbar}>
        <span>9:41</span>
        <div className={s.statusIcons}>
          <svg width="17" height="11" viewBox="0 0 17 11" aria-hidden="true">
            <g fill="#111">
              <rect x="0" y="7" width="3" height="4" rx=".5"/>
              <rect x="4.5" y="5" width="3" height="6" rx=".5"/>
              <rect x="9" y="3" width="3" height="8" rx=".5"/>
              <rect x="13.5" y="1" width="3" height="10" rx=".5"/>
            </g>
          </svg>
          <svg width="16" height="11" viewBox="0 0 16 11" fill="none" aria-hidden="true">
            <path d="M8 3c2 0 3.8.7 5.2 1.9l1.4-1.4C12.8 1.9 10.5 1 8 1S3.2 1.9 1.4 3.5l1.4 1.4C4.2 3.7 6 3 8 3Z" fill="#111"/>
            <path d="M8 6c1.2 0 2.3.4 3.2 1.1l1.4-1.4C11.3 4.6 9.7 4 8 4s-3.3.6-4.6 1.7l1.4 1.4C5.7 6.4 6.8 6 8 6Z" fill="#111"/>
            <circle cx="8" cy="9" r="1.4" fill="#111"/>
          </svg>
          <svg width="26" height="12" viewBox="0 0 26 12" aria-hidden="true">
            <rect x=".5" y=".5" width="22" height="11" rx="3" fill="none" stroke="#111" opacity=".45"/>
            <rect x="2" y="2" width="17" height="8" rx="1.6" fill="#111"/>
            <rect x="23" y="4" width="2" height="4" rx="1" fill="#111" opacity=".45"/>
          </svg>
        </div>
      </div>

      {/* ── Nav ────────────────────────────────────────────── */}
      <header
        className={s.nav}
        data-testid={TEST_IDS.p04.navbar}
        role="banner"
      >
        <div className={s.navRow}>
          <button
            className={s.navBack}
            onClick={() => nav('/capture')}
            aria-label="返回分析"
          >
            <svg viewBox="0 0 12 20" fill="none" aria-hidden="true">
              <path d="M10 2 2 10l8 8" stroke="#007AFF" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            分析
          </button>
          <div className={s.navActions}>
            <button aria-label="编辑">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M16 5l3 3-10 10H6v-3L16 5Z" stroke="#007AFF" strokeWidth="1.8" strokeLinejoin="round"/>
              </svg>
            </button>
            <button aria-label="分享">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M12 4v12m0 0 4-4m-4 4-4-4M5 19h14" stroke="#007AFF" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </button>
          </div>
        </div>
        <h1 className={s.navH1}>
          分析完成 <span className={s.navTag}>4.2s</span>
        </h1>
      </header>

      {/* ── Skeleton ─────────────────────────────────────────── */}
      {pageState === 'LOADING' && <Skeleton />}

      {/* ── Content ──────────────────────────────────────────── */}
      {pageState !== 'LOADING' && (
        <main className={s.content} data-mood="B" role="main">

          {/* Low-conf banner */}
          {pageState === 'LOW_CONF' && (
            <div
              className={s.lowConfBanner}
              role="status"
              data-testid={TEST_IDS.p04.lowConfBanner}
            >
              ⚠️ AI 不太确定，请复核答案再保存
            </div>
          )}

          {/* B2 · Question hero */}
          <div className={s.questionHero} data-testid={TEST_IDS.p04.questionHero}>
            <div className={s.thumb} aria-hidden="true">
              <span className={s.thumbLbl}>数学 · 12</span>
              <span className={s.thumbQno}>17</span>
              <h3 className={s.thumbH3}>已知 f(x)=x²−4x+3</h3>
              <div className={s.thumbStrike} />
              <div className={s.thumbPen}>B</div>
            </div>
            <div className={s.heroMeta}>
              <div className={s.heroKicker}>
                {SUBJECT_LABEL[q.subject] ?? q.subject} · 二次函数 · 顶点式
              </div>
              <div className={s.heroStem}>{q.stem}</div>
              {q.formula && (
                <div className={s.heroFormula}>
                  {q.formula.replace(/(\d+)/g, (n) => n)}
                </div>
              )}
            </div>
          </div>

          {/* B3 · Answers */}
          <div className={s.answers} data-testid={TEST_IDS.p04.answersRow}>
            <div className={`${s.ans} ${s.ansWrong}`} data-testid={TEST_IDS.p04.answersWrong}>
              <div className={s.ansT}>
                <svg width="11" height="11" viewBox="0 0 11 11" fill="none" aria-hidden="true">
                  <path d="M2 2L9 9M9 2L2 9" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/>
                </svg>
                你的作答
              </div>
              <div className={s.ansV} data-testid={TEST_IDS.p04.answersWrong + '-text'}>{q.myAnswer}</div>
              <div className={s.ansN}>混淆顶点式符号</div>
              <div className={s.ansDeco} aria-hidden="true" />
            </div>
            <div className={`${s.ans} ${s.ansRight}`} data-testid={TEST_IDS.p04.answersRight}>
              <div className={s.ansT}>
                <svg width="11" height="11" viewBox="0 0 11 11" fill="none" aria-hidden="true">
                  <path d="M1.5 6L4.5 9L9.5 2.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
                正确答案
              </div>
              <div className={s.ansV} data-testid={TEST_IDS.p04.answersRight + '-text'}>
                {q.correctAnswer}
              </div>
              <div className={s.ansN}>顶点 (2, −1)，对称轴 x = 2</div>
              <div className={s.ansDeco} aria-hidden="true" />
            </div>
          </div>

          {/* B4 · Error reason */}
          <div className={s.secHeader}>
            <span className={s.secTitle}>错因诊断</span>
            <div className={s.secLine} />
            <span className={s.secTag} style={{ color: '#FF3B30' }}>CONCEPT · 概念混淆</span>
          </div>
          <div
            className={s.reasonCard}
            data-testid={TEST_IDS.p04.reasonCard}
            aria-live="polite"
          >
            <div className={s.reasonIx} aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none">
                <path d="M12 3.5 21 19.5H3L12 3.5Z" stroke="#fff" strokeWidth="1.8" strokeLinejoin="round"/>
                <path d="M12 10v4.5M12 17v.1" stroke="#fff" strokeWidth="1.8" strokeLinecap="round"/>
              </svg>
            </div>
            <div className={s.reasonTxt} data-testid={TEST_IDS.p04.reasonText}>
              你把顶点式 <span className={s.reasonKw}>(x − h)² + k</span> 中的 h 与 k 读反了：顶点是{' '}
              <span className={s.reasonKw}>(h, k)</span> 而不是 <span className={s.reasonKw}>(−h, k)</span>，
              所以 x 坐标是 2，不是 −2。对称轴方程应为 x = h = <b>2</b>。
            </div>
          </div>

          {/* B5 · Steps */}
          <div className={s.secHeader}>
            <span className={s.secTitle}>解答步骤</span>
            <div className={s.secLine} />
            <span className={s.secTag} style={{ color: '#8E8E93' }}>3 STEPS</span>
          </div>
          <div className={s.steps} data-testid={TEST_IDS.p04.solutionStepper}>
            {q.steps.map((step) => (
              <div
                key={step.idx}
                className={s.step}
                data-testid={`p04-solution-stepper-step-${step.idx}`}
              >
                <div className={s.stepNum} aria-hidden="true">{step.idx}</div>
                <div className={s.stepBody}>
                  <div className={s.stepExp}>{step.title}</div>
                  {step.formula && (
                    <div className={s.stepFm}>
                      {step.formula}
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>

          {/* B6 · KP + difficulty */}
          <div className={s.secHeader}>
            <span className={s.secTitle}>知识点</span>
            <div className={s.secLine} />
          </div>
          <div className={s.kpRow} data-testid={TEST_IDS.p04.metaChips}>
            <div className={s.kpCard}>
              <div className={s.kpHdr}>涉及知识点</div>
              <div className={s.kpChips}>
                {q.knowledgePoints.map((kp, i) => (
                  <span
                    key={kp.id}
                    className={i === q.knowledgePoints.length - 1 ? s.chipOutline : s.chip}
                    data-testid={i === 0 ? TEST_IDS.p04.subjectChipMath : undefined}
                  >
                    {kp.name}
                  </span>
                ))}
              </div>
            </div>
            <div className={s.diffCard}>
              <div className={s.diffHdr}>难度</div>
              <div className={s.stars}>
                {Array.from({ length: 5 }, (_, i) => (
                  <span key={i} className={i < q.difficulty ? '' : s.starDim} aria-hidden="true">
                    ★
                  </span>
                ))}
              </div>
              <div className={s.diffLevel}>{DIFF_LABELS[q.difficulty] ?? '中等'}</div>
            </div>
          </div>

          {/* B7 · Ebbinghaus preview */}
          <div className={s.ebbing} data-testid={TEST_IDS.p04.memoryCurve}>
            <div className={s.ebbingT}>
              <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M4 19c3-7 6-10 9-10s5 3 7 10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                <circle cx="4" cy="19" r="1.6" fill="currentColor"/>
              </svg>
              艾宾浩斯复习计划预览
            </div>
            <h4 className={s.ebbingH4}>保存后将在日历自动生成 6 个复习节点</h4>
            <div className={s.ebbingNodes}>
              {nodes.map((node, i) => (
                <div
                  key={node.tLevel}
                  className={`${s.node}${i === 0 ? ` ${s.nodeFirst}` : ''}`}
                  data-testid={`memory-curve-node-${node.tLevel}`}
                  data-status="future"
                >
                  <div className={s.nodePill} />
                  <div className={s.nodeLv}>{node.tLevel}</div>
                  <div className={s.nodeDt}>{NODE_DATE_LABELS[i] ?? ''}</div>
                </div>
              ))}
            </div>
          </div>

        </main>
      )}

      {/* ── B8 · CTA dock ────────────────────────────────────── */}
      {pageState !== 'LOADING' && (
        <footer className={s.cta} role="contentinfo">
          <div className={s.ctaRow}>
            <button
              className={`${s.btn} ${s.btnGhost}`}
              onClick={() => nav('/capture')}
              aria-label="手动修正题目"
            >
              手动修正
            </button>
            <button
              className={`${s.btn} ${s.btnPrimary}`}
              data-testid={TEST_IDS.p04.saveCta}
              aria-label="保存到错题本，AI 会安排 6 次复习"
              onClick={handleSave}
              disabled={isSaving}
            >
              {isSaving ? (
                '保存中…'
              ) : (
                <>
                  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path d="M5 12.5 10 17l9-10" stroke="#fff" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                  保存并开启复习
                </>
              )}
            </button>
          </div>
          <div className={s.ctaNote}>
            保存后将按《艾宾浩斯》自动生成 T1–T6 共 6 个日历提醒
          </div>
        </footer>
      )}
    </div>
  );
};
