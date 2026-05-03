// S8 · FE-04 · ReviewToday (P07) · 对标 design/mockups/wrongbook/_archive/07_review_today.html
// Mood B · pure-warm · 米白底 + 白卡 + iOS glass nav
// AC 覆盖: AC-REVIEW-TODAY-001 ~ AC-REVIEW-TODAY-010
import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { TEST_IDS, p07Ids } from '@longfeng/testids';
import s from './ReviewToday.module.css';

// ─── Types ────────────────────────────────────────────────────────────────
type Subject = 'math' | 'physics' | 'chemistry' | 'english';
type Countdown = 'now' | 'soon' | 'wait';
type SlotKey = 'now-morning' | 'afternoon' | 'evening';
type ItemStatus = 'pending' | 'in-progress' | 'done';

interface ReviewItem {
  nid: string;
  qid: string;
  subject: Subject;
  tLevel: string;
  hhmm: string;
  stemSnippet: string;
  kpName?: string;
  countdown: Countdown;
  countdownLabel: string;
  status: ItemStatus;
}

interface SlotGroup {
  slotKey: SlotKey;
  slotLabel: string;
  items: ReviewItem[];
}

interface ReviewTodayResp {
  date: string;
  totalCount: number;
  doneCount: number;
  inProgressCount: number;
  waitCount: number;
  estMinutes: number;
  progressPct: number;
  masteryPct: number;
  slots: SlotGroup[];
}

// ─── Subject helpers ───────────────────────────────────────────────────────
const SUBJECT_LABEL: Record<Subject, string> = {
  math: '数学', physics: '物理', chemistry: '化学', english: '英语',
};
const SUBJECT_CSS: Record<Subject, string> = {
  math: s.sideBarMath, physics: s.sideBarPhysics, chemistry: s.sideBarChemistry, english: s.sideBarEnglish,
};
const SUBJ_COLOR_CSS: Record<Subject, string> = {
  math: s.subjMath, physics: s.subjPhysics, chemistry: s.subjChemistry, english: s.subjEnglish,
};

// ─── Mock data (placeholder until API ready) ──────────────────────────────
const MOCK_DATA: ReviewTodayResp = {
  date: new Date().toISOString().slice(0, 10),
  totalCount: 8,
  doneCount: 3,
  inProgressCount: 1,
  waitCount: 4,
  estMinutes: 25,
  progressPct: 38,
  masteryPct: 72,
  slots: [
    {
      slotKey: 'now-morning',
      slotLabel: '现在·上午',
      items: [
        {
          nid: 'n1', qid: 'q1', subject: 'math', tLevel: 'T1', hhmm: '09:45',
          stemSnippet: '已知 f(x)=x²−4x+3，求顶点坐标与对称轴。错因：h k 混淆。',
          kpName: '顶点式', countdown: 'now', countdownLabel: '4 分钟', status: 'pending',
        },
        {
          nid: 'n2', qid: 'q2', subject: 'physics', tLevel: 'T3', hhmm: '11:00',
          stemSnippet: 'R₁=4Ω, R₂=6Ω 并联接 12V，求总电流。公式错误。',
          kpName: '并联电路', countdown: 'soon', countdownLabel: '1 h', status: 'pending',
        },
      ],
    },
    {
      slotKey: 'afternoon',
      slotLabel: '下午',
      items: [
        {
          nid: 'n3', qid: 'q3', subject: 'chemistry', tLevel: 'T4', hhmm: '14:30',
          stemSnippet: 'Al + HCl → AlCl₃ + H₂，系数 2:6:2:3。',
          kpName: '化学方程', countdown: 'wait', countdownLabel: '5 h', status: 'pending',
        },
        {
          nid: 'n4', qid: 'q4', subject: 'english', tLevel: 'T2', hhmm: '16:00',
          stemSnippet: 'By the time he arrived, the meeting ___ already started.',
          kpName: '时态一致', countdown: 'wait', countdownLabel: '6 h 15 m', status: 'pending',
        },
      ],
    },
    {
      slotKey: 'evening',
      slotLabel: '晚上',
      items: [
        {
          nid: 'n5', qid: 'q5', subject: 'math', tLevel: 'T2', hhmm: '19:30',
          stemSnippet: '设 α, β 是方程 x²+px+q=0 的两根，利用韦达定理求 α²+β²。',
          kpName: '韦达定理', countdown: 'wait', countdownLabel: '晚上 19:30', status: 'pending',
        },
      ],
    },
  ],
};

// ─── Main Page ─────────────────────────────────────────────────────────────
export const ReviewTodayPage: React.FC = () => {
  const nav = useNavigate();
  const [data, setData] = useState<ReviewTodayResp | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const allDoneJumpedRef = useRef(false);

  // Load data (API or mock)
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(false);

    const fetchData = async () => {
      try {
        const resp = await fetch('/api/review/today?tz=Asia/Shanghai');
        if (!resp.ok) throw new Error('API error');
        const json: ReviewTodayResp = await resp.json();
        if (!cancelled) { setData(json); setLoading(false); }
      } catch {
        if (!cancelled) {
          // Fallback to mock data for development
          setData(MOCK_DATA);
          setLoading(false);
        }
      }
    };
    fetchData();
    return () => { cancelled = true; };
  }, []);

  // AC-REVIEW-TODAY-010 · ALL_DONE one-shot navigate to P09
  useEffect(() => {
    if (!data) return;
    const { doneCount, totalCount } = data;
    if (totalCount > 0 && doneCount === totalCount && !allDoneJumpedRef.current) {
      const sessionKey = `p07_all_done_${data.date}`;
      if (!sessionStorage.getItem(sessionKey)) {
        sessionStorage.setItem(sessionKey, '1');
        allDoneJumpedRef.current = true;
        nav('/review/done', { replace: false });
      }
    }
  }, [data, nav]);

  // ── LOADING ─────────────────────────────────────────────────
  if (loading) {
    return (
      <div className={s.root} data-mood="B">
        <StatusBar dark />
        <div className={s.skeleton} aria-busy="true" aria-label="加载中">
          <div className={s.skeletonHero} />
          <div className={s.skeletonSlot} />
          <div className={s.skeletonCard} />
          <div className={s.skeletonCard} />
        </div>
      </div>
    );
  }

  // ── ERROR ────────────────────────────────────────────────────
  if (error) {
    return (
      <div className={s.root} data-mood="B">
        <StatusBar dark />
        <div className={s.errorBanner} role="alert">
          <span>加载失败，请重试</span>
          <button className={s.retryBtn} onClick={() => window.location.reload()}>重试</button>
        </div>
      </div>
    );
  }

  if (!data) return null;

  const { totalCount, doneCount, estMinutes, progressPct, masteryPct, slots } = data;
  const pendingCount = totalCount - doneCount;
  const allDone = totalCount > 0 && doneCount === totalCount;
  const isEmpty = totalCount === 0;

  return (
    <div className={s.root} data-mood="B">
      <StatusBar dark />

      {/* NavBar */}
      <header className={s.nav} role="banner">
        <div className={s.navRow}>
          <button className={s.back} onClick={() => nav('/')} aria-label="返回首页">
            <svg width="12" height="20" viewBox="0 0 12 20" fill="none" aria-hidden="true">
              <path d="M10 2 2 10l8 8" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            首页
          </button>
          <button className={s.navRight} aria-label="排序">排序 · 时间</button>
        </div>
        <h1 className={s.navTitle}>今日复习</h1>
      </header>

      <main className={s.content} role="main">
        {/* B1 · TodayReviewCard hero · AC-REVIEW-TODAY-001/002 */}
        <div
          data-testid={TEST_IDS.p07.todayReviewCard}
          data-mood="B"
          data-state={allDone ? 'all-done' : isEmpty ? 'empty' : 'list'}
        >
          <div className={s.hero} role="banner" aria-label="今日复习摘要">
            {/* AC-REVIEW-TODAY-009 · particles animation */}
            <div className={s.particles} data-testid={TEST_IDS.p07.heroParticles} aria-hidden="true">
              {[1, 2, 3, 4, 5].map(i => (
                <div key={i} className={s.particle} />
              ))}
            </div>
            <div className={s.heroBlob1} aria-hidden="true" />
            <div className={s.heroBlob2} aria-hidden="true" />

            <div className={s.heroKicker}>
              <span className={s.liveBar} aria-hidden="true" />
              {data.date} · 今日待复习
            </div>

            {isEmpty ? (
              <h2 className={s.heroTitle}>今天没有复习安排，恭喜 🎉</h2>
            ) : allDone ? (
              <h2 className={s.heroTitle}>今日全部完成 ✓</h2>
            ) : (
              <h2 className={s.heroTitle}>
                <span data-testid={TEST_IDS.p07.heroTotal}>{totalCount} 题</span>
                <span className={s.heroTitleSub}>
                  预计 <span data-testid={TEST_IDS.p07.heroEstMin}>{estMinutes}</span> 分钟
                </span>
              </h2>
            )}

            {!isEmpty && (
              <>
                <div className={s.statsRow}>
                  <div className={s.stat}>
                    <div className={s.statV} data-testid={TEST_IDS.p07.heroDone}>{doneCount}</div>
                    <div className={s.statL}>已完成</div>
                  </div>
                  <div className={s.stat}>
                    <div className={s.statV}>{data.inProgressCount}</div>
                    <div className={s.statL}>进行中</div>
                  </div>
                  <div className={s.stat}>
                    <div className={s.statV}>{data.waitCount}</div>
                    <div className={s.statL}>未开始</div>
                  </div>
                </div>

                {/* AC-REVIEW-TODAY-002 · progress bar */}
                <div className={s.progressBar}>
                  <div
                    className={s.progressFill}
                    role="progressbar"
                    aria-valuenow={doneCount}
                    aria-valuemax={totalCount}
                    aria-valuetext={`${doneCount} of ${totalCount} completed`}
                    style={{ width: `${progressPct}%` }}
                    data-testid={TEST_IDS.p07.heroProgressBar}
                  />
                </div>
                <div className={s.progressText}>
                  <span>进度 <span data-testid={TEST_IDS.p07.heroProgressPct}>{progressPct}%</span></span>
                  <span>掌握度 <span data-testid={TEST_IDS.p07.heroMasteryPct}>{masteryPct}%</span></span>
                </div>
              </>
            )}
          </div>
        </div>

        {/* EMPTY state · AC-REVIEW-TODAY-008 */}
        {isEmpty && (
          <div className={s.emptyState} data-testid={TEST_IDS.p07.emptyState} role="status">
            <div className={s.emptyIcon} aria-hidden="true">🎉</div>
            <div className={s.emptyText}>今天没有复习安排</div>
            <div className={s.emptyHint}>去拍一道新题，开启错题积累之旅</div>
          </div>
        )}

        {/* B2/B3/B4 · SlotGroups · AC-REVIEW-TODAY-003/004/005 */}
        {!isEmpty && slots.map((slot) => (
          <section
            key={slot.slotKey}
            role="region"
            aria-label={slot.slotLabel}
          >
            {/* Slot header */}
            <div
              className={s.slotHeader}
              data-testid={p07Ids.slotHeader(slot.slotKey)}
            >
              <span className={`${s.slotIcon} ${slot.slotKey === 'afternoon' ? s.afternoon : slot.slotKey === 'evening' ? s.evening : ''}`} aria-hidden="true">
                <SlotIcon slotKey={slot.slotKey} />
              </span>
              <h3
                className={s.slotTitle}
                data-testid={p07Ids.slotTitle(slot.slotKey)}
              >
                {slot.slotLabel}
              </h3>
              <span className={s.slotLine} aria-hidden="true" />
              <span className={s.slotCount}>{slot.items.length} 题</span>
            </div>

            {/* Items */}
            {slot.items.map((item, idx) => (
              <ReviewItemCard
                key={item.nid}
                item={item}
                slotKey={slot.slotKey}
                index={idx}
                onOpen={() => nav(`/review/exec/${item.nid}`)}
              />
            ))}
          </section>
        ))}

        <div className={s.bottomSpacer} />
      </main>

      {/* B5 · BottomCTA sticky · AC-REVIEW-TODAY-006 */}
      {!isEmpty && (
        <footer
          className={s.cta}
          role="contentinfo"
          data-testid={TEST_IDS.p07.bottomCta}
        >
          <button
            className={s.ctaBtn}
            onClick={() => nav('/review/exec/session')}
            data-testid={TEST_IDS.p07.bottomCtaStartAllBtn}
            aria-label={`全部开始 ${pendingCount} 题`}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M8 5v14l11-7L8 5Z" fill="#fff"/>
            </svg>
            全部开始
            <span className={s.ctaCount}>{pendingCount} 题 · {estMinutes} min</span>
          </button>
        </footer>
      )}

      {/* TabBar */}
      <nav className={s.tabbar} role="navigation" aria-label="底部导航">
        <button className={s.tab} onClick={() => nav('/')} aria-label="首页">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M3 11 L12 3 L21 11 V20 a1 1 0 0 1 -1 1 H14 V14 H10 V21 H4 a1 1 0 0 1 -1 -1 Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" strokeLinecap="round"/>
          </svg>
          <span>首页</span>
        </button>
        <button className={s.tab} onClick={() => nav('/wrongbook')} aria-label="错题本">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M5 4h11l3 3v13H5V4Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round"/>
            <path d="M8 11h8M8 14h6M8 17h5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/>
          </svg>
          <span>错题本</span>
        </button>
        <button className={s.tab} onClick={() => nav('/capture')} aria-label="拍题">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="12" cy="13" r="4.5" stroke="currentColor" strokeWidth="1.8"/>
            <path d="M5 8h3l1.5-2h5L16 8h3a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round"/>
          </svg>
          <span>拍题</span>
        </button>
        <button className={`${s.tab} ${s.tabActive}`} aria-current="page" aria-label="复习（当前）">
          <div className={s.tabBadge} aria-hidden="true">{pendingCount}</div>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M12 3.5c-3.6 0-6.2 2.6-6.2 6.2v3.4L4 15.5v1.3h16v-1.3l-1.8-2.4V9.7c0-3.6-2.6-6.2-6.2-6.2Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round"/>
            <path d="M10 19.5a2 2 0 0 0 4 0" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/>
          </svg>
          <span>复习</span>
        </button>
        <button className={s.tab} onClick={() => nav('/me')} aria-label="我的">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="12" cy="8.5" r="3.8" stroke="currentColor" strokeWidth="1.8"/>
            <path d="M4.5 20c1.2-3.8 4.2-5.6 7.5-5.6s6.3 1.8 7.5 5.6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/>
          </svg>
          <span>我的</span>
        </button>
      </nav>

      <div className={s.homebar} aria-hidden="true" />
    </div>
  );
};

// ─── Sub-components ────────────────────────────────────────────────────────

const StatusBar: React.FC<{ dark?: boolean }> = ({ dark }) => (
  <div className={s.status} aria-hidden="true" style={{ color: dark ? '#111' : '#fff' }}>
    <span>9:41</span>
    <span className={s.statusIcons}>
      <svg width="17" height="11" viewBox="0 0 17 11"><g fill={dark ? '#111' : '#fff'}><rect x="0" y="7" width="3" height="4" rx=".5"/><rect x="4.5" y="5" width="3" height="6" rx=".5"/><rect x="9" y="3" width="3" height="8" rx=".5"/><rect x="13.5" y="1" width="3" height="10" rx=".5"/></g></svg>
      <svg width="26" height="12" viewBox="0 0 26 12"><rect x=".5" y=".5" width="22" height="11" rx="3" fill="none" stroke={dark ? '#111' : '#fff'} opacity=".45"/><rect x="2" y="2" width="17" height="8" rx="1.6" fill={dark ? '#111' : '#fff'}/><rect x="23" y="4" width="2" height="4" rx="1" fill={dark ? '#111' : '#fff'} opacity=".45"/></svg>
    </span>
  </div>
);

const SlotIcon: React.FC<{ slotKey: SlotKey }> = ({ slotKey }) => {
  if (slotKey === 'now-morning') return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2"/>
      <path d="M12 7v5l3 2" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
  );
  if (slotKey === 'afternoon') return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="5" stroke="currentColor" strokeWidth="2"/>
      <g stroke="currentColor" strokeWidth="2" strokeLinecap="round">
        <path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4 7 17M17 7l1.4-1.4"/>
      </g>
    </svg>
  );
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
      <path d="M12 3c0 0-6 3-6 9v3l-1 2v1h14v-1l-1-2v-3c0-6-6-9-6-9Z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/>
      <path d="M10 19.5a2 2 0 0 0 4 0" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
  );
};

interface ReviewItemCardProps {
  item: ReviewItem;
  slotKey: SlotKey;
  index: number;
  onOpen: () => void;
}

const ReviewItemCard: React.FC<ReviewItemCardProps> = ({ item, slotKey, index, onOpen }) => {
  const sideClass = SUBJECT_CSS[item.subject] ?? s.sideBarDefault;
  const subjClass = SUBJ_COLOR_CSS[item.subject] ?? '';
  const isDone = item.status === 'done';

  return (
    <article
      role="article"
      aria-label={`${SUBJECT_LABEL[item.subject]} ${item.hhmm} ${item.stemSnippet.slice(0, 20)} ${item.countdownLabel}`}
      className={`${s.item} ${isDone ? s.itemDone : ''}`}
      onClick={onOpen}
      onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') onOpen(); }}
      tabIndex={0}
      data-testid={p07Ids.slotItem(slotKey, index)}
    >
      <span className={`${s.sideBar} ${sideClass}`} aria-hidden="true" />

      {/* Time + Level */}
      <div className={s.timeCol}>
        <div
          className={s.timeHH}
          data-testid={p07Ids.slotItemTime(slotKey, index)}
        >
          {item.hhmm}
        </div>
        <div
          className={s.timeLv}
          data-testid={p07Ids.slotItemTLevel(slotKey, index)}
        >
          {item.tLevel}
        </div>
      </div>

      {/* Body */}
      <div className={s.itemBody}>
        <div className={s.itemSubject}>
          <span className={subjClass}>{SUBJECT_LABEL[item.subject]}</span>
          {item.kpName && ` · ${item.kpName}`}
        </div>
        <div className={s.itemStem}>{item.stemSnippet}</div>
        {item.kpName && (
          <div className={s.itemTags}>
            <span className={s.tag}>{item.kpName}</span>
          </div>
        )}
      </div>

      {/* Countdown / done badge */}
      <div className={s.itemRight}>
        {isDone ? (
          <span className={s.doneBadge}>已完成</span>
        ) : (
          <span
            className={`${s.countdown} ${
              item.countdown === 'now' ? s.cdNow : item.countdown === 'soon' ? s.cdSoon : s.cdWait
            }`}
            data-testid={p07Ids.slotItemCountdown(slotKey, index)}
            data-countdown={item.countdown}
          >
            {item.countdownLabel}
          </span>
        )}
        <div className={s.arrow} aria-hidden="true">
          <svg width="8" height="13" viewBox="0 0 8 13" fill="none">
            <path d="M1 1l6 5.5L1 12" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </div>
      </div>
    </article>
  );
};
