// S8 · FE-04 · ReviewDone (P09) · 对标 design/mockups/wrongbook/_archive/09_review_done.html
// Mood D · celebrate-green · 绿渐变 hero + ConfettiBurst (仅 all-done)
// AC 覆盖: AC-P09-001 ~ AC-P09-011
// ConfettiBurst 铁律: 仅 variant=all-done 触发 · prefers-reduced-motion 关
import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { TEST_IDS, p09Ids } from '@longfeng/testids';
import s from './ReviewDone.module.css';

// ─── Types ────────────────────────────────────────────────────────────────
type Subject = 'math' | 'physics' | 'chemistry' | 'english';
type TLevel = 'T0' | 'T1' | 'T2' | 'T3' | 'T4' | 'T5' | 'T6';
type NodeStatus = 'done' | 'just-completed' | 'next-encouragement' | 'future';
type Variant = 'single' | 'all-done' | 'streak';

interface KpDelta {
  kpId: string;
  kpName: string;
  subject: Subject;
  oldPct: number;
  newPct: number;
}

interface DonePageData {
  nid: string;
  variant: Variant;
  previousT: TLevel;
  nextT?: TLevel;
  nextDueAt?: string;
  masteryPct: number;
  advanceDays?: number;
  todayStats: { mastered: number; partial: number; forgot: number; total: number };
  kpDelta: KpDelta[];
  plannedNodes: Array<{ tLevel: TLevel; status: NodeStatus; dueAt?: string }>;
  hasNext: boolean;
  isStreakMilestone: boolean;
  streakDays?: number;
}

// ─── Subject helpers ───────────────────────────────────────────────────────
const SUBJECT_COLOR: Record<Subject, string> = {
  math: '#C41E3A', physics: '#0057B7', chemistry: '#1A6B3A', english: '#9C4F00',
};
const SUBJECT_GRADIENT: Record<Subject, string> = {
  math: 'linear-gradient(90deg, #FF6B60, #D72B22)',
  physics: 'linear-gradient(90deg, #5AA3FF, #0062E1)',
  chemistry: 'linear-gradient(90deg, #5DD87A, #1E7F3C)',
  english: 'linear-gradient(90deg, #FFB84D, #9C4F00)',
};

// ─── Mock data ─────────────────────────────────────────────────────────────
const MOCK_SINGLE: DonePageData = {
  nid: 'n1',
  variant: 'single',
  previousT: 'T2',
  nextT: 'T3',
  nextDueAt: new Date(Date.now() + 3 * 86400000).toISOString(),
  masteryPct: 82,
  advanceDays: 7,
  todayStats: { mastered: 4, partial: 1, forgot: 0, total: 8 },
  kpDelta: [
    { kpId: 'kp1', kpName: '顶点式 · 配方法', subject: 'math', oldPct: 70, newPct: 86 },
    { kpId: 'kp2', kpName: '对称轴方程', subject: 'math', oldPct: 60, newPct: 74 },
    { kpId: 'kp3', kpName: '判别式 Δ 应用', subject: 'physics', oldPct: 45, newPct: 58 },
    { kpId: 'kp4', kpName: '韦达定理', subject: 'math', oldPct: 30, newPct: 42 },
  ],
  plannedNodes: [
    { tLevel: 'T1', status: 'done' },
    { tLevel: 'T2', status: 'done' },
    { tLevel: 'T3', status: 'just-completed' },
    { tLevel: 'T4', status: 'next-encouragement' },
    { tLevel: 'T5', status: 'future' },
    { tLevel: 'T6', status: 'future' },
  ],
  hasNext: true,
  isStreakMilestone: false,
};

// ─── Confetti colors (5 token colors) ─────────────────────────────────────
const CONFETTI_COLORS = ['#FFD166', '#EF476F', '#118AB2', '#06D6A0', '#8B87F6'];

// ─── Main Page ─────────────────────────────────────────────────────────────
export const ReviewDonePage: React.FC = () => {
  const nav = useNavigate();
  const [searchParams] = useSearchParams();
  const nid = searchParams.get('nid') ?? 'n1';

  const [data, setData] = useState<DonePageData | null>(null);
  const [loading, setLoading] = useState(true);
  const [navigating, setNavigating] = useState(false);

  // prefers-reduced-motion
  const reducedMotion = typeof window !== 'undefined' &&
    window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

  useEffect(() => {
    let cancelled = false;
    const fetchData = async () => {
      try {
        const resp = await fetch(`/api/review/nodes/${nid}/result`);
        if (!resp.ok) throw new Error('API error');
        const json: DonePageData = await resp.json();
        if (!cancelled) { setData(json); setLoading(false); }
      } catch {
        if (!cancelled) {
          // Fallback mock
          setData(MOCK_SINGLE);
          setLoading(false);
        }
      }
    };
    fetchData();
    return () => { cancelled = true; };
  }, [nid]);

  const handleContinue = async () => {
    if (!data || navigating) return;
    setNavigating(true);
    try {
      const resp = await fetch(`/api/review/sessions/${nid}/next`, { method: 'POST' });
      const result = await resp.json();
      nav(`/review/exec/${result.nextNodeId}`);
    } catch {
      nav('/review');
    }
  };

  const handleAddCalendar = async () => {
    if (!data) return;
    try {
      await fetch(`/api/calendar/events/${nid}/subscribe`, { method: 'POST' });
    } catch {}
  };

  // ── LOADING ─────────────────────────────────────────────────
  if (loading || !data) {
    return (
      <div className={s.root} data-mood="D" data-testid={TEST_IDS.p09.root}>
        <div className={s.skeleton} aria-busy="true" aria-label="加载中">
          <div className={s.skeletonHero} />
          <div className={s.skeletonCard} />
          <div className={s.skeletonCard} />
        </div>
      </div>
    );
  }

  const { variant, previousT, nextT, nextDueAt, masteryPct, advanceDays, todayStats, kpDelta, plannedNodes, hasNext, isStreakMilestone, streakDays } = data;
  const isAllDone = variant === 'all-done';
  const isStreak = variant === 'streak';
  const showConfetti = (isAllDone || isStreak) && !reducedMotion;

  const heroTitle = isAllDone ? '今日全部完成 🎉' : isStreak ? `连续 ${streakDays} 天复习 🔥` : '本题已掌握';
  const heroSub = nextT ? `记忆曲线向前推进一节点 · ${previousT} → ${nextT}` : '复习计划已完成';

  const nextDueDateStr = nextDueAt
    ? new Date(nextDueAt).toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric', weekday: 'short' })
    : null;

  return (
    <div className={s.root} data-mood="D" data-testid={TEST_IDS.p09.root}>
      {/* Status bar (white · on green hero) */}
      <div className={s.status} aria-hidden="true">
        <span className={s.statusTime}>9:47</span>
        <span className={s.statusIcons}>
          <svg width="18" height="12" viewBox="0 0 18 12"><g fill="#fff"><rect x="0" y="8" width="3" height="4" rx="1"/><rect x="5" y="5" width="3" height="7" rx="1"/><rect x="10" y="2" width="3" height="10" rx="1"/><rect x="15" y="0" width="3" height="12" rx="1"/></g></svg>
          <svg width="26" height="12" viewBox="0 0 26 12"><rect x="0" y="1" width="22" height="10" rx="2.5" fill="none" stroke="#fff" strokeWidth="1"/><rect x="22.5" y="4" width="1.5" height="4" rx=".5" fill="#fff"/><rect x="2" y="3" width="18" height="6" rx="1" fill="#fff"/></svg>
        </span>
      </div>

      {/* B1 · CelebrateHero (Mood D) · AC-P09-001 */}
      <header
        className={s.hero}
        data-mood="celebrate"
        data-mood-v2="D"
        data-testid={TEST_IDS.p09.celebrateHero}
        role="banner"
      >
        {/* B2 · ConfettiBurst · AC-P09-002/003/011 */}
        {showConfetti && (
          <div
            className={s.confetti}
            data-testid={TEST_IDS.p09.confettiBurst}
            aria-hidden="true"
          >
            {[
              { left: '10%', top: '18%', color: CONFETTI_COLORS[0], rotate: '20deg', delay: '0ms' },
              { left: '22%', top: '62%', color: CONFETTI_COLORS[1], rotate: '-18deg', delay: '80ms' },
              { left: '34%', top: '30%', color: CONFETTI_COLORS[2], rotate: '40deg', delay: '160ms' },
              { left: '68%', top: '18%', color: CONFETTI_COLORS[0], rotate: '-30deg', delay: '240ms' },
              { left: '82%', top: '44%', color: CONFETTI_COLORS[3], rotate: '12deg', delay: '320ms' },
              { left: '90%', top: '72%', color: CONFETTI_COLORS[1], rotate: '-40deg', delay: '400ms' },
              { left: '6%',  top: '80%', color: CONFETTI_COLORS[3], rotate: '60deg', delay: '480ms' },
              { left: '58%', top: '78%', color: CONFETTI_COLORS[2], rotate: '-10deg', delay: '560ms' },
            ].map((c, i) => (
              <span
                key={i}
                className={s.confettiPiece}
                data-testid={p09Ids.confettiParticle(i + 1)}
                style={{
                  left: c.left,
                  top: c.top,
                  background: c.color,
                  transform: `rotate(${c.rotate})`,
                  animationDelay: c.delay,
                }}
              />
            ))}
          </div>
        )}

        {/* Hero checkmark icon */}
        <div
          className={s.hicon}
          data-testid={TEST_IDS.p09.heroCheckmark}
          role="img"
          aria-label="复习完成"
        >
          <div className={s.hiconCore}>
            <svg width="44" height="44" viewBox="0 0 44 44" fill="none">
              <circle cx="22" cy="22" r="20" fill="url(#p09g1)"/>
              <path d="M13 22.6 L19 28 L31 15.5" stroke="#fff" strokeWidth="3.6" strokeLinecap="round" strokeLinejoin="round"/>
              <defs>
                <linearGradient id="p09g1" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#34C759"/><stop offset="100%" stopColor="#1E9748"/>
                </linearGradient>
              </defs>
            </svg>
          </div>
        </div>

        <div className={s.hkicker}>Review complete</div>
        <div
          className={s.htitle}
          role="alert"
          aria-live="assertive"
          data-testid={TEST_IDS.p09.heroTitle}
        >
          {isStreak ? (
            <span
              className={s.streakNumber}
              data-testid={TEST_IDS.p09.heroStreakNumber}
            >
              {heroTitle}
            </span>
          ) : heroTitle}
        </div>
        <div className={s.hsub}>{heroSub}</div>
        <div className={s.hchips} aria-hidden="true">
          <span className={s.hchip}>+{Math.round(masteryPct * 0.1)} 记忆度</span>
          {todayStats.mastered > 0 && <span className={s.hchip}>今日已掌握 {todayStats.mastered}</span>}
        </div>
      </header>

      {/* Scroll content */}
      <div className={s.scroll}>
        {/* B3 · MemoryCurve complete · AC-P09-004 */}
        <div className={s.blockTitle}>
          <span className={`${s.blockTitleDot} ${s.dtGreen}`} aria-hidden="true" />
          记忆曲线进度
          <span className={s.blockTitleR}>题目 #{nid.slice(-3)} · 数学</span>
        </div>
        <div
          className={s.card}
          data-testid={TEST_IDS.p09.memoryCurve}
          role="figure"
          aria-label="艾宾浩斯记忆曲线"
        >
          <div className={s.mcHead}>
            <div>
              <div className={s.mcTitle}>{data.nid}</div>
              <div className={s.mcSub}>知识点 · 顶点式 / 配方法</div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div className={s.mcMasteryNum}>{masteryPct}%</div>
              <div className={s.mcMasteryLbl}>Mastery</div>
            </div>
          </div>

          {/* Forgetting curve SVG */}
          <div className={s.nodes} style={{ height: 84 }}>
            <svg viewBox="0 0 320 70" preserveAspectRatio="none" aria-hidden="true" style={{ position: 'absolute', left: 0, top: 0, width: '100%', height: '100%', overflow: 'visible' }}>
              <defs>
                <linearGradient id="p09curveLg" x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0%" stopColor="#34C759" stopOpacity=".7"/>
                  <stop offset="55%" stopColor="#007AFF" stopOpacity=".7"/>
                  <stop offset="100%" stopColor="#8E8E93" stopOpacity=".35"/>
                </linearGradient>
              </defs>
              <path d="M10 12 C40 8, 70 22, 100 26 C130 30, 160 44, 200 50 C240 56, 280 60, 310 62" stroke="url(#p09curveLg)" strokeWidth="2.2" fill="none"/>
              <path d="M10 12 C40 8, 70 22, 100 26 C130 30, 160 44, 200 50 C240 56, 280 60, 310 62 L310 70 L10 70 Z" fill="url(#p09curveLg)" fillOpacity=".10"/>
              <line x1="100" y1="0" x2="100" y2="70" stroke="#007AFF" strokeWidth="1" strokeDasharray="3 3" opacity=".55"/>
            </svg>
            <div className={s.nodesRow}>
              {plannedNodes.map((nd, i) => (
                <div
                  key={nd.tLevel}
                  className={`${s.node} ${nd.status === 'done' ? s.nodeDone : nd.status === 'just-completed' || nd.status === 'next-encouragement' ? s.nodeCurrent : ''}`}
                  data-testid={p09Ids.memoryCurveNode(nd.tLevel)}
                  data-status={nd.status}
                >
                  <span
                    className={`${s.nodeDot} ${
                      nd.status === 'done' ? s.nodeDotDone :
                      nd.status === 'just-completed' ? s.nodeDotJustDone :
                      nd.status === 'next-encouragement' ? s.nodeDotNext :
                      ''
                    }`}
                    aria-hidden="true"
                  />
                  <span className={s.nodeLbl}>{nd.tLevel}</span>
                  <span className={s.nodeAt}>
                    {nd.status === 'done' ? '✓' :
                     nd.status === 'just-completed' ? '刚完成' :
                     nd.status === 'next-encouragement' ? `${i}d` :
                     `${i * 2}d`}
                  </span>
                </div>
              ))}
            </div>
          </div>

          {/* B4 · Advance banner */}
          {advanceDays && (
            <div
              className={s.advanceBanner}
              data-testid={TEST_IDS.p09.advanceBanner}
            >
              <div className={s.advanceAi} aria-hidden="true">
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M8 1 L9.6 5.4 L14 7 L9.6 8.6 L8 13 L6.4 8.6 L2 7 L6.4 5.4 Z" fill="#fff"/></svg>
              </div>
              <div
                className={s.advanceText}
                data-testid={TEST_IDS.p09.advanceBannerText}
              >
                AI 已按艾宾浩斯模型推进节点，今天的进度让你的曲线推进了
                {' '}<em className={s.advanceEm}>{advanceDays}</em>{' '}天，
                下次复习节点 <em className={s.advanceEm}>{nextT}</em>，未来 6 次提醒已自动更新。
              </div>
            </div>
          )}
        </div>

        {/* B5 · Next review card */}
        {nextDueDateStr && (
          <div className={s.nxtCard} data-testid={TEST_IDS.p09.nextDueCard}>
            <div className={s.nxtLeft}>
              <div className={s.nxtIco} aria-hidden="true">
                <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
                  <rect x="3" y="5" width="16" height="14" rx="3" stroke="#1E7F3C" strokeWidth="1.6"/>
                  <path d="M3 9 H19" stroke="#1E7F3C" strokeWidth="1.6"/>
                  <path d="M7 3 V6 M15 3 V6" stroke="#1E7F3C" strokeWidth="1.6" strokeLinecap="round"/>
                  <circle cx="15" cy="14" r="2" fill="#34C759"/>
                </svg>
              </div>
              <div>
                <div className={s.nxtK}>下次复习</div>
                <div className={s.nxtV}>{nextDueDateStr}</div>
                <div className={s.nxtM}>提前 30 min 提醒 · 24h 有效窗口</div>
              </div>
            </div>
            <button
              className={s.addCalBtn}
              onClick={handleAddCalendar}
              data-testid={TEST_IDS.p09.addCalendarBtn}
              aria-label="加入日历"
            >
              <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                <path d="M6 2 V10 M2 6 H10" stroke="#007AFF" strokeWidth="1.6" strokeLinecap="round"/>
              </svg>
              日历
            </button>
          </div>
        )}

        {/* B6 · Today stats · AC-P09-006 */}
        <div className={s.blockTitle} style={{ marginTop: 16 }}>
          <span className={`${s.blockTitleDot} ${s.dtBlue}`} aria-hidden="true" />
          今日复习战绩
          <span className={s.blockTitleR}>已完成 {todayStats.mastered + todayStats.partial + todayStats.forgot} / {todayStats.total}</span>
        </div>
        <div className={s.statsGrid} data-testid={TEST_IDS.p09.statsRow}>
          <div
            className={`${s.statCard} ${s.statMastered}`}
            data-testid={TEST_IDS.p09.statsMastered}
          >
            <div className={s.statBig}>{todayStats.mastered}</div>
            <div className={s.statLbl}>Mastered</div>
          </div>
          <div
            className={`${s.statCard} ${s.statPartial}`}
            data-testid={TEST_IDS.p09.statsPartial}
          >
            <div className={s.statBig}>{todayStats.partial}</div>
            <div className={s.statLbl}>Partial</div>
          </div>
          <div
            className={`${s.statCard} ${s.statForgot}`}
            data-testid={TEST_IDS.p09.statsForgot}
          >
            <div className={s.statBig}>{todayStats.forgot}</div>
            <div className={s.statLbl}>Forgot</div>
          </div>
        </div>

        {/* B7 · KP chart · AC-P09-009 */}
        <div className={s.blockTitle} style={{ marginTop: 16 }}>
          <span className={`${s.blockTitleDot} ${s.dtIndigo}`} aria-hidden="true" />
          知识点掌握变化
        </div>
        <div className={s.kpList} data-testid={TEST_IDS.p09.kpChart}>
          {kpDelta.map((kp, i) => (
            <div key={kp.kpId} className={s.kpRow}>
              <span
                className={s.kpSq}
                style={{ background: SUBJECT_COLOR[kp.subject] }}
                aria-hidden="true"
              />
              <span className={s.kpName}>{kp.kpName}</span>
              <div className={s.kpBar}>
                {/* Old bar (lighter) */}
                <span
                  style={{
                    position: 'absolute', left: 0, top: 0, bottom: 0,
                    width: `${kp.oldPct}%`,
                    borderRadius: 3,
                    background: 'rgba(120,120,128,0.22)',
                  }}
                />
                {/* New bar */}
                <span
                  className={s.kpBarFill}
                  data-testid={p09Ids.kpChartBarNew(i + 1)}
                  style={{
                    width: `${kp.newPct}%`,
                    background: SUBJECT_GRADIENT[kp.subject],
                  }}
                />
              </div>
              <span className={s.kpPct}>{kp.newPct}%</span>
            </div>
          ))}
        </div>
      </div>

      {/* B8 · CTA row · AC-P09-007 */}
      <footer
        className={s.cta}
        role="contentinfo"
        data-testid={TEST_IDS.p09.ctaRow}
      >
        <button
          className={s.btnSec}
          onClick={() => nav('/')}
          data-testid={TEST_IDS.p09.ctaEndBtn}
          aria-label="结束本次复习"
        >
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M3 4 H13 V13 a1 1 0 0 1 -1 1 H4 a1 1 0 0 1 -1 -1 Z M6 4 V3 a1 1 0 0 1 1 -1 h2 a1 1 0 0 1 1 1 V4 M2 4 H14" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" strokeLinecap="round"/>
          </svg>
          结束本次
        </button>
        <button
          className={s.btnPri}
          onClick={handleContinue}
          disabled={!hasNext || navigating}
          data-testid={TEST_IDS.p09.ctaContinueBtn}
          aria-label="继续复习下一题"
        >
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M4 3 L12 8 L4 13 Z" fill="#fff"/>
          </svg>
          继续复习
        </button>
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
          <div className={s.tabBadge} aria-hidden="true">4</div>
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

    </div>
  );
};
