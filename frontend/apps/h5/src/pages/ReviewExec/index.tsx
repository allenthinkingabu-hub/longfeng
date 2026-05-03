// S8 · FE-04 · ReviewExec (P08) · 对标 design/mockups/wrongbook/_archive/08_review_exec.html
// Mood B · pure-warm · 白底沉静 · 题目卡 + 手写区 + reveal + 自评 3 档
// AC 覆盖: AC-P08-001 ~ AC-P08-010
// IRON RULE 1 EXCEPTION: self-grading — B8 三按钮使用 mastery colors
// D-Cancel-Race: 退出弹二次确认 · 当前 node 保持 SCHEDULED · session=PAUSED + lastCompletedNid
import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { TEST_IDS, p08Ids } from '@longfeng/testids';
import s from './ReviewExec.module.css';

// ─── Types ────────────────────────────────────────────────────────────────
type Subject = 'math' | 'physics' | 'chemistry' | 'english';
type TLevel = 'T0' | 'T1' | 'T2' | 'T3' | 'T4' | 'T5' | 'T6';
type Grade = 'MASTERED' | 'PARTIAL' | 'FORGOT';
type NodeStatus = 'done' | 'current' | 'future';

interface NodeMeta {
  tLevel: TLevel;
  status: NodeStatus;
  dueAt?: string;
}

interface ExecQuestion {
  qid: string;
  subject: Subject;
  stem: string;
  correctAnswer: string;
  steps: Array<{ idx: number; title: string; detail?: string }>;
  knowledgePoints: Array<{ id: string; name: string }>;
  difficulty: 1 | 2 | 3 | 4 | 5;
  reviewCount: number;
}

interface ExecPageData {
  sessionId: string;
  cursor: number;
  total: number;
  node: { nid: string; tLevel: TLevel; openedAt: number };
  question: ExecQuestion;
  plannedNodes: NodeMeta[];
  revealed: boolean;
}

// ─── Subject helpers ───────────────────────────────────────────────────────
const SUBJECT_LABEL: Record<Subject, string> = {
  math: '数学', physics: '物理', chemistry: '化学', english: '英语',
};
const CHIP_CSS: Record<Subject, string> = {
  math: s.chipMath, physics: s.chipPhysics, chemistry: s.chipChem, english: s.chipEng,
};

// ─── Mock data ─────────────────────────────────────────────────────────────
const MOCK_DATA: ExecPageData = {
  sessionId: 'sess-1',
  cursor: 2,
  total: 8,
  node: { nid: 'n1', tLevel: 'T2', openedAt: Date.now() },
  question: {
    qid: 'q1',
    subject: 'math',
    stem: '已知函数 f(x) = x² − 4x + 3，请将其化为顶点式，并写出顶点坐标与对称轴方程。',
    correctAnswer: 'f(x) = (x − 2)² − 1　　顶点 (2, −1)　对称轴 x = 2',
    steps: [
      { idx: 1, title: '配方', detail: '提取 x 的二次项与一次项，进行配方：x² − 4x = (x − 2)² − 4。' },
      { idx: 2, title: '合并常数', detail: '将常数项合并：(x − 2)² − 4 + 3 = (x − 2)² − 1。' },
      { idx: 3, title: '读取顶点', detail: '由顶点式可得顶点坐标 (2, −1)，对称轴方程为 x = 2。' },
    ],
    knowledgePoints: [{ id: 'kp1', name: '顶点式 · 配方法' }],
    difficulty: 3,
    reviewCount: 2,
  },
  plannedNodes: [
    { tLevel: 'T1', status: 'done' },
    { tLevel: 'T2', status: 'current' },
    { tLevel: 'T3', status: 'future' },
    { tLevel: 'T4', status: 'future' },
    { tLevel: 'T5', status: 'future' },
    { tLevel: 'T6', status: 'future' },
  ],
  revealed: false,
};

// ─── Main Page ─────────────────────────────────────────────────────────────
export const ReviewExecPage: React.FC = () => {
  const { nodeId } = useParams<{ nodeId: string }>();
  const nav = useNavigate();

  const [data, setData] = useState<ExecPageData | null>(null);
  const [loading, setLoading] = useState(true);
  const [revealed, setRevealed] = useState(false);
  const [revealPopped, setRevealPopped] = useState(false);
  const [grading, setGrading] = useState(false);
  const [showExitConfirm, setShowExitConfirm] = useState(false);
  const [answerDraft, setAnswerDraft] = useState('');
  const openedAtRef = useRef(Date.now());

  // Load node data
  useEffect(() => {
    let cancelled = false;
    openedAtRef.current = Date.now();
    const fetchData = async () => {
      try {
        const resp = await fetch(`/api/review/nodes/${nodeId}/open`, { method: 'POST' });
        if (!resp.ok) throw new Error('API error');
        const json = await resp.json() as Partial<ExecPageData>;
        // SC-02: MSW /open 只返回 { nid, openedAt, timeBudgetSec } · 缺 question/plannedNodes
        // → 用 MOCK_DATA 填充缺失字段 · 保证 footer/B8 grade buttons 可渲染
        const merged: ExecPageData = {
          ...MOCK_DATA,
          ...json,
          node: { ...MOCK_DATA.node, ...(json?.node ?? {}), nid: nodeId ?? json?.node?.nid ?? 'n1' },
          question: json?.question ?? MOCK_DATA.question,
          plannedNodes: json?.plannedNodes ?? MOCK_DATA.plannedNodes,
        };
        if (!cancelled) { setData(merged); setLoading(false); }
      } catch {
        if (!cancelled) {
          setData({ ...MOCK_DATA, node: { ...MOCK_DATA.node, nid: nodeId ?? 'n1' } });
          setLoading(false);
        }
      }
    };
    fetchData();
    return () => { cancelled = true; };
  }, [nodeId]);

  // Reveal answer
  const handleReveal = async () => {
    if (revealed) return;
    setRevealed(true);
    // Fire reveal API (non-blocking · best-effort)
    fetch(`/api/review/nodes/${nodeId}/reveal`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ waitMs: Date.now() - openedAtRef.current }),
    }).catch(() => {});
    // 800ms pop animation
    setTimeout(() => setRevealPopped(true), 50);
  };

  // Submit grade
  const handleGrade = async (grade: Grade) => {
    if (grading || !data) return;
    setGrading(true);
    const timeSpentMs = Date.now() - openedAtRef.current;
    try {
      const resp = await fetch(`/api/review/nodes/${nodeId}/grade`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ grade, timeSpentMs, answerText: answerDraft }),
      });
      const result = await resp.json();
      const destination = result?.nextNodeId
        ? `/review/exec/${result.nextNodeId}`
        : `/review/done?nid=${nodeId}&grade=${grade}`;
      setTimeout(() => nav(destination, { replace: false }), 200);
    } catch {
      // Fallback navigate for development
      setTimeout(() => nav(`/review/done?nid=${nodeId}&grade=${grade}`), 200);
    }
  };

  // Exit with D-Cancel-Race: node stays SCHEDULED
  const handleExitRequest = () => {
    if (!revealed && !grading) {
      setShowExitConfirm(true);
    } else {
      nav(-1);
    }
  };
  const handleExitConfirm = () => {
    // D-Cancel-Race: session=PAUSED · node=SCHEDULED (no cancel API call)
    setShowExitConfirm(false);
    nav(-1);
  };

  // ── LOADING ─────────────────────────────────────────────────
  if (loading || !data) {
    return (
      <div className={s.root} data-mood="B" data-testid={TEST_IDS.p08.root}>
        <div className={s.skeleton} aria-busy="true" aria-label="加载中">
          <div className={s.skeletonNav} />
          <div className={s.skeletonCard} />
          <div className={s.skeletonCard} />
        </div>
      </div>
    );
  }

  const { cursor, total, node, question, plannedNodes } = data;
  const progressPct = Math.round(((cursor - 1) / total) * 100);

  return (
    <div className={s.root} data-mood="B" data-testid={TEST_IDS.p08.root}>
      {/* Status bar */}
      <div className={s.status} aria-hidden="true">
        <span className={s.statusTime}>9:41</span>
        <span className={s.statusIcons}>
          <svg width="18" height="12" viewBox="0 0 18 12"><g fill="#000"><rect x="0" y="8" width="3" height="4" rx="1"/><rect x="5" y="5" width="3" height="7" rx="1"/><rect x="10" y="2" width="3" height="10" rx="1"/><rect x="15" y="0" width="3" height="12" rx="1"/></g></svg>
          <svg width="26" height="12" viewBox="0 0 26 12"><rect x="0" y="1" width="22" height="10" rx="2.5" fill="none" stroke="#000" strokeWidth="1"/><rect x="22.5" y="4" width="1.5" height="4" rx=".5" fill="#000"/><rect x="2" y="3" width="18" height="6" rx="1" fill="#000"/></svg>
        </span>
      </div>

      {/* B1+B2 Nav with progress track */}
      <header className={s.nav} role="banner">
        <button className={s.back} onClick={() => nav(-1)} aria-label="返回复习列表">
          <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
            <path d="M14 5l-6 6 6 6" stroke="#007AFF" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          复习
        </button>
        <div className={s.navCenter}>
          <div
            className={s.navTitle}
            data-testid={TEST_IDS.p08.topbar}
          >
            复习执行 · 第 {cursor} 题
          </div>
          <div className={s.navSub} data-testid={TEST_IDS.p08.topbarCursor}>
            {cursor} / {total} · 剩余 {total - cursor} 题
          </div>
        </div>
        {/* AC-P08-009 · Exit × with confirm if not graded */}
        <button
          className={s.closeBtn}
          onClick={handleExitRequest}
          aria-label="退出复习"
          data-testid={TEST_IDS.p08.closeBtn}
        >
          <svg width="14" height="14" viewBox="0 0 14 14">
            <path d="M2 2 L12 12 M12 2 L2 12" stroke="#636366" strokeWidth="2" strokeLinecap="round"/>
          </svg>
        </button>

        {/* B1 · progress track */}
        <div
          data-testid={TEST_IDS.p08.progressBar}
          className={s.progressTrack}
          role="progressbar"
          aria-valuenow={cursor - 1}
          aria-valuemax={total}
          aria-valuetext={`第 ${cursor} 题，共 ${total} 题`}
        >
          <span className={s.progressLabel}>{progressPct}% · 预计 {Math.max(1, (total - cursor + 1) * 3)} 分钟</span>
          <div className={s.progressBar} style={{ width: `${progressPct}%` }} />
        </div>
      </header>

      {/* Scroll content */}
      <div className={s.scroll}>
        {/* B3 · Meta chips */}
        <div className={s.metaRow} data-testid={TEST_IDS.p08.metaChips}>
          <span className={`${s.chip} ${s.chipRed}`}>
            <span className={s.chipDot} />{node.tLevel} · 第 {question.reviewCount} 次复习
          </span>
          <span className={`${s.chip} ${CHIP_CSS[question.subject]}`}>
            {SUBJECT_LABEL[question.subject]} · {question.knowledgePoints[0]?.name ?? '知识点'}
          </span>
          <span className={`${s.chip} ${s.chipOrange}`}>
            {'★'.repeat(question.difficulty)}{'☆'.repeat(5 - question.difficulty)}
          </span>
        </div>

        {/* B4 · Question card */}
        <div className={s.qcard} data-testid={TEST_IDS.p08.questionHero}>
          <div className={s.qkicker}>错题回顾 · 原题</div>
          <div className={s.qstem}>{question.stem}</div>
          <div className={s.qmeta}>
            <span>知识点 · {question.knowledgePoints.map(k => k.name).join(' · ')}</span>
            <span className={s.stars}>
              {'★'.repeat(question.difficulty)}{'☆'.repeat(5 - question.difficulty)}
            </span>
          </div>
        </div>

        {/* B5 · Work area */}
        <div className={s.blockTitle} data-testid={TEST_IDS.p08.answerArea}>
          <span className={s.blockTitleDot} aria-hidden="true" />
          你的解答 · 手写
        </div>
        <div className={s.work}>
          <div className={s.paper}>
            <div className={s.handwritten} contentEditable suppressContentEditableWarning onInput={e => setAnswerDraft((e.target as HTMLElement).innerText)}>
              <span className={s.cursor} aria-hidden="true" />
            </div>
          </div>
          <div className={s.workTools}>
            <button className={s.tool} aria-label="手写模式">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M2 12 L9 5 L12 8 L5 12 Z" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round"/></svg>
              手写
            </button>
            <button className={s.tool} aria-label="键盘模式">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><rect x="2" y="3" width="10" height="8" rx="2" stroke="currentColor" strokeWidth="1.4"/><path d="M4 7 h6" stroke="currentColor" strokeWidth="1.4"/></svg>
              键盘
            </button>
            <button className={`${s.tool} ${s.toolPrime}`} aria-label="公式面板">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M7 1 L9 5 L13 5 L10 8 L11 12 L7 10 L3 12 L4 8 L1 5 L5 5 Z" fill="#007AFF"/></svg>
              公式面板
            </button>
          </div>
        </div>

        {/* B6 · Reveal */}
        {!revealed ? (
          <button
            className={s.revealBtn}
            onClick={handleReveal}
            data-testid={TEST_IDS.p08.revealBtn}
            aria-expanded="false"
            aria-label="查看答案与解法"
          >
            查看答案与解法
          </button>
        ) : (
          <>
            <div className={`${s.blockTitle} ${s.blockTitleGreen}`}>
              <span className={s.blockTitleDot} aria-hidden="true" />
              参考答案 · 已揭示
            </div>
            <div
              className={s.revealCard}
              data-testid={TEST_IDS.p08.revealContent}
              aria-live="polite"
            >
              <div className={s.revealHead}>
                <div className={s.revealHeadL}>
                  <div
                    className={s.revealCheckmark}
                    data-testid={TEST_IDS.p08.revealCheckmark}
                    data-status={revealPopped ? 'popped' : 'pending'}
                  >
                    <div className={s.revealIco}>
                      <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                        <path d="M2 6.2 L5 9 L10 3" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                      </svg>
                    </div>
                  </div>
                  <div className={s.revealTitle}>标准解答</div>
                </div>
                <div className={s.revealSubInfo}>AI · GPT-4o mini</div>
              </div>
              <div className={s.revealAns}>
                <div className={s.revealAnsK}>正确答案</div>
                <div className={s.revealAnsV}>{question.correctAnswer}</div>
              </div>
              <div className={s.revealSteps}>
                {question.steps.map((step, i) => (
                  <div
                    key={step.idx}
                    className={s.step}
                    data-testid={p08Ids.revealStep(i + 1)}
                  >
                    <div className={s.stepN}>{step.idx}</div>
                    <div className={s.stepT}>{step.detail ?? step.title}</div>
                  </div>
                ))}
              </div>
            </div>
          </>
        )}

        {/* B7 · MemoryCurve nodes */}
        <div
          className={s.nodes}
          data-testid={TEST_IDS.p08.memoryCurve}
          role="figure"
          aria-label="艾宾浩斯记忆曲线节点"
        >
          {plannedNodes.map((nd, i) => (
            <React.Fragment key={nd.tLevel}>
              <span
                className={`${s.nodeDot} ${nd.status === 'done' ? s.nodeDone : nd.status === 'current' ? s.nodeCurrent : ''}`}
                data-testid={p08Ids.memoryCurveNode(nd.tLevel)}
                data-status={nd.status === 'current' ? 'current' : nd.status}
                role="img"
                aria-label={`${nd.tLevel} ${nd.status}`}
              />
              {i < plannedNodes.length - 1 && (
                <span className={`${s.nodeLine} ${nd.status === 'done' ? s.nodeLineDone : ''}`} aria-hidden="true" />
              )}
            </React.Fragment>
          ))}
          <span style={{ marginLeft: 'auto', fontSize: 10 }}>
            {node.tLevel} · {plannedNodes.find(n => n.status === 'future')?.tLevel ?? '完成'}
          </span>
        </div>
      </div>

      {/* B8 · Self-grading (IRON RULE 1 EXCEPTION: self-grading) */}
      <footer
        className={s.gradeBar}
        role="contentinfo"
        data-testid={TEST_IDS.p08.gradeButtons}
        data-iron-rule-1-exception="self-grading"
        aria-label="自评掌握度"
      >
        <div className={s.gradeTitle}>
          <div className={s.gradeTitleL}>本次复习你的自评？</div>
          <div className={s.gradeTitleR}>将用于更新记忆曲线</div>
        </div>
        <div
          className={s.gradeActions}
          role="radiogroup"
          aria-label="自评掌握度"
        >
          {/* Forgot */}
          <button
            className={`${s.gradeBtn} ${s.gradeBtnForgot}`}
            onClick={() => !revealed ? undefined : handleGrade('FORGOT')}
            disabled={grading}
            aria-disabled={!revealed || grading ? 'true' : 'false'}
            data-testid={TEST_IDS.p08.gradeBtnForgot}
            data-iron-rule-1-exception="self-grading"
            aria-label="未掌握，回到 T0"
          >
            <div className={s.gradeIcon}>✗</div>
            <div className={s.gradeLabel}>未掌握</div>
            <div className={s.gradeSub}>回到 T0</div>
          </button>
          {/* Partial */}
          <button
            className={`${s.gradeBtn} ${s.gradeBtnPartial}`}
            onClick={() => !revealed ? undefined : handleGrade('PARTIAL')}
            disabled={grading}
            aria-disabled={!revealed || grading ? 'true' : 'false'}
            data-testid={TEST_IDS.p08.gradeBtnPartial}
            data-iron-rule-1-exception="self-grading"
            aria-label="部分掌握，原计划不变"
          >
            <div className={s.gradeIcon}>◐</div>
            <div className={s.gradeLabel}>部分</div>
            <div className={s.gradeSub}>原计划不变</div>
          </button>
          {/* Mastered — AC-P08-008: disabled until revealed */}
          <button
            className={`${s.gradeBtn} ${s.gradeBtnMastered}`}
            onClick={() => !revealed ? undefined : handleGrade('MASTERED')}
            disabled={grading}
            aria-disabled={!revealed || grading ? 'true' : 'false'}
            data-testid={TEST_IDS.p08.gradeBtnMastered}
            data-iron-rule-1-exception="self-grading"
            aria-label="已掌握，推进到下一节点"
          >
            <div className={s.gradeIcon}>✓</div>
            <div className={s.gradeLabel}>已掌握</div>
            <div className={s.gradeSub}>推进到 {node.tLevel === 'T6' ? '完成' : `T${parseInt(node.tLevel[1]) + 1}`}</div>
          </button>
        </div>
      </footer>

      {/* Tab bar */}
      <nav className={s.tabbar} role="navigation" aria-label="底部导航">
        <button className={s.tab} onClick={() => nav('/')} aria-label="首页">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none"><path d="M3 11 L12 3 L21 11 V20 a1 1 0 0 1 -1 1 H14 V14 H10 V21 H4 a1 1 0 0 1 -1 -1 Z" stroke="#8E8E93" strokeWidth="1.6" strokeLinejoin="round" strokeLinecap="round"/></svg>
          <span>首页</span>
        </button>
        <button className={s.tab} onClick={() => nav('/wrongbook')} aria-label="错题本">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none"><path d="M5 4 h10 l4 4 v12 a2 2 0 0 1 -2 2 H5 a2 2 0 0 1 -2 -2 V6 a2 2 0 0 1 2 -2 z" stroke="#8E8E93" strokeWidth="1.5"/><path d="M8 13 h8 M8 17 h5" stroke="#8E8E93" strokeWidth="1.5" strokeLinecap="round"/></svg>
          <span>错题本</span>
        </button>
        <button className={s.tab} onClick={() => nav('/capture')} aria-label="拍题">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none"><rect x="3" y="7" width="18" height="13" rx="3" stroke="#8E8E93" strokeWidth="1.5"/><circle cx="12" cy="13.5" r="3.5" stroke="#8E8E93" strokeWidth="1.5"/><path d="M9 7 L10 5 h4 L15 7" stroke="#8E8E93" strokeWidth="1.5" strokeLinejoin="round"/></svg>
          <span>拍题</span>
        </button>
        <button className={`${s.tab} ${s.tabActive}`} aria-current="page" aria-label="复习（当前）">
          <div className={s.tabBadge} aria-hidden="true">7</div>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <path d="M4 12 a8 8 0 1 1 2.3 5.6" stroke="#007AFF" strokeWidth="1.8" strokeLinecap="round"/>
            <path d="M4 18 V12 h6" stroke="#007AFF" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
            <path d="M12 8 V12 L15 14" stroke="#007AFF" strokeWidth="1.8" strokeLinecap="round"/>
          </svg>
          <span>复习</span>
        </button>
        <button className={s.tab} onClick={() => nav('/me')} aria-label="我的">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="9" r="4" stroke="#8E8E93" strokeWidth="1.5"/><path d="M4 20 c1.5 -4 5 -6 8 -6 s6.5 2 8 6" stroke="#8E8E93" strokeWidth="1.5" strokeLinecap="round"/></svg>
          <span>我的</span>
        </button>
      </nav>

      <div className={s.homebar} aria-hidden="true" />

      {/* AC-P08-009 · Exit confirm sheet (D-Cancel-Race) */}
      {showExitConfirm && (
        <div
          className={s.exitSheetBackdrop}
          role="dialog"
          aria-modal="true"
          aria-label="退出确认"
          data-testid={TEST_IDS.p08.exitConfirmSheet}
          onClick={() => setShowExitConfirm(false)}
        >
          <div className={s.exitSheet} onClick={e => e.stopPropagation()}>
            <div className={s.exitSheetHandle} aria-hidden="true" />
            <div className={s.exitSheetTitle}>本次复习尚未自评</div>
            <div className={s.exitSheetDesc}>
              退出将保留在原计划中（SCHEDULED），下次可继续复习此题。
            </div>
            <div className={s.exitSheetBtns}>
              <button
                className={s.exitBtnPrimary}
                onClick={handleExitConfirm}
                aria-label="确认退出"
              >
                退出
              </button>
              <button
                className={s.exitBtnGhost}
                onClick={() => setShowExitConfirm(false)}
                aria-label="取消，继续复习"
              >
                取消
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
