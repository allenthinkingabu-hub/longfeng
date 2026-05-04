/**
 * P-HOME · 今日聚合首页 · HomePage
 * Mood A (hero+overlap) · archive ref: _archive/01_home.html (1:1)
 * spec: design/system/pages/P-HOME.spec.md
 * AC 覆盖: AC-HOME-001 ~ AC-HOME-010
 */
import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import s from './Home.module.css';

/* ── Types (spec §4) ── */
type Subject = 'math' | 'physics' | 'chemistry' | 'english';
type TLevel = 'T0' | 'T1' | 'T2' | 'T3' | 'T4' | 'T5' | 'T6';

interface WeekDay {
  date: string;
  weekLabel: string;
  dayNum: string | number;
  isToday: boolean;
  reviewCount: number;
  examHint?: boolean;
  tLevels: TLevel[];
  subjects: Subject[];
}

interface Message {
  id: string;
  type: 'review' | 'exam' | 'family' | 'system';
  title: string;
  subtitle: string;
  timeLabel: string;
}

interface HomeData {
  studentName: string;
  streak: { days: number; milestone?: 7 | 30 | 100 };
  todayReview: {
    total: number;
    done: number;
    estMin: number;
    circleProgress: number;
    subjectDist: Array<{ subject: Subject; count: number }>;
  };
  weekStats: { mastered: number; added: number; forgotten: number; masteryPct: number };
  weekStrip: WeekDay[];
  messages: Message[];
  weakKP?: { kpId: string; kpName: string; questionCount: number; masteryPct: number };
}

type PageState = 'LOADING' | 'READY' | 'EMPTY' | 'ERROR' | 'STREAK_MILESTONE' | 'ALL_DONE';

/* ── Mock data ── */
const MOCK_DATA: HomeData = {
  studentName: '小 A',
  streak: { days: 12 },
  todayReview: {
    total: 8,
    done: 3,
    estMin: 25,
    circleProgress: 38,
    subjectDist: [
      { subject: 'math', count: 3 },
      { subject: 'physics', count: 2 },
      { subject: 'english', count: 3 },
    ],
  },
  weekStats: { mastered: 23, added: 8, forgotten: 2, masteryPct: 68 },
  weekStrip: [
    { date: '2026-04-20', weekLabel: '一', dayNum: 20, isToday: false, reviewCount: 2, tLevels: ['T1', 'T3'], subjects: ['math', 'physics'] },
    { date: '2026-04-21', weekLabel: '二', dayNum: 21, isToday: true,  reviewCount: 8, tLevels: ['T1', 'T3', 'T6'], subjects: ['math', 'physics', 'english'] },
    { date: '2026-04-22', weekLabel: '三', dayNum: 22, isToday: false, reviewCount: 2, tLevels: ['T6'], subjects: ['chemistry', 'math'] },
    { date: '2026-04-23', weekLabel: '四', dayNum: 23, isToday: false, reviewCount: 3, tLevels: ['T1', 'T6'], subjects: ['math', 'english'] },
    { date: '2026-04-24', weekLabel: '五', dayNum: 24, isToday: false, reviewCount: 2, tLevels: ['T3'], subjects: ['physics'] },
    { date: '2026-04-25', weekLabel: '六', dayNum: 25, isToday: false, reviewCount: 1, tLevels: [], subjects: ['math'] },
    { date: '2026-04-26', weekLabel: '日', dayNum: 26, isToday: false, reviewCount: 3, tLevels: ['T6', 'T3'], subjects: ['chemistry', 'math', 'english'] },
  ],
  messages: [
    { id: '1', type: 'review',  title: '记忆曲线 T3 · 二次函数',   subtitle: '今晚 20:30 · 3 题即将到期', timeLabel: '10 min' },
    { id: '2', type: 'family',  title: '妈妈分享了「5 月月考安排」', subtitle: '5 月 12 日 · 周一 · 已同步到日历', timeLabel: '昨天' },
    { id: '3', type: 'system',  title: '本周免打扰时段已更新',    subtitle: '23:00 – 07:30 · 记忆曲线节奏不变', timeLabel: '周日' },
  ],
  weakKP: {
    kpId: 'kp-001',
    kpName: '韦达定理',
    questionCount: 4,
    masteryPct: 22,
  },
};

const SUBJECT_COLORS: Record<Subject, string> = {
  math:      '#FF6B6B',
  physics:   '#FFD166',
  chemistry: '#6DE895',
  english:   '#4C9BFF',
};

const SUBJECT_LABELS: Record<Subject, string> = {
  math: '数学', physics: '物理', chemistry: '化学', english: '英语',
};

function dotClass(subject: Subject, _tl: TLevel): string {
  switch (subject) {
    case 'math':      return s.dotR;
    case 'physics':   return s.dotO;
    case 'chemistry': return s.dotG;
    case 'english':   return s.dotI;
    default:          return s.dotG;
  }
}

/* ── StatusIcons ── */
const StatusIcons = () => (
  <div className={s.statusRight}>
    <svg width="18" height="12" viewBox="0 0 18 12" fill="#fff" aria-hidden="true">
      <rect x="0" y="7" width="3" height="5" rx="1"/>
      <rect x="5" y="4" width="3" height="8" rx="1"/>
      <rect x="10" y="1" width="3" height="11" rx="1"/>
      <rect x="15" y="-2" width="3" height="14" rx="1" opacity=".45"/>
    </svg>
    <svg width="16" height="12" viewBox="0 0 16 12" fill="#fff" aria-hidden="true">
      <path d="M8 2.2c2 0 3.9.7 5.4 2L15 2.8A9.6 9.6 0 0 0 8 0 9.6 9.6 0 0 0 1 2.8l1.6 1.4C4.1 2.9 6 2.2 8 2.2z"/>
      <path d="M8 5.6c1 0 2 .4 2.8 1l1.4-1.4A6 6 0 0 0 8 3.8a6 6 0 0 0-4.2 1.4l1.4 1.4C6 5.9 7 5.6 8 5.6z"/>
      <circle cx="8" cy="10" r="1.6"/>
    </svg>
    <svg width="26" height="12" viewBox="0 0 26 12" aria-hidden="true">
      <rect x="0.5" y="0.5" width="22" height="11" rx="2.5" fill="none" stroke="#fff" strokeWidth="1"/>
      <rect x="23" y="4" width="1.5" height="4" rx="0.5" fill="rgba(255,255,255,.6)"/>
      <rect x="2" y="2" width="18" height="8" rx="1" fill="#fff"/>
    </svg>
  </div>
);

/* ── Message icon by type ── */
const MsgIcon: React.FC<{ type: Message['type'] }> = ({ type }) => {
  switch (type) {
    case 'review':
      return (
        <div className={`${s.msgIc} ${s.msgIcInd}`}>
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="M8 1 C5 1 3 3 3 6 C3 9 5 9.5 5 11 H11 C11 9.5 13 9 13 6 C13 3 11 1 8 1 Z" stroke="#5856D6" strokeWidth="1.4" strokeLinejoin="round"/>
            <path d="M6 13 H10" stroke="#5856D6" strokeWidth="1.4" strokeLinecap="round"/>
            <path d="M7 15 H9" stroke="#5856D6" strokeWidth="1.4" strokeLinecap="round"/>
          </svg>
        </div>
      );
    case 'family':
    case 'exam':
      return (
        <div className={`${s.msgIc} ${s.msgIcPnk}`}>
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <rect x="2" y="3" width="12" height="11" rx="2" stroke="#FF2D55" strokeWidth="1.4"/>
            <path d="M2 6 H14 M5 1 V4 M11 1 V4" stroke="#FF2D55" strokeWidth="1.4" strokeLinecap="round"/>
            <circle cx="8" cy="10" r="1.6" fill="#FF2D55"/>
          </svg>
        </div>
      );
    default:
      return (
        <div className={`${s.msgIc} ${s.msgIcTea}`}>
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <circle cx="8" cy="8" r="6" stroke="#30B0C7" strokeWidth="1.4"/>
            <path d="M8 4 V8 L11 10" stroke="#30B0C7" strokeWidth="1.4" strokeLinecap="round"/>
          </svg>
        </div>
      );
  }
};

export const HomePage: React.FC = () => {
  const nav = useNavigate();
  const [pageState, setPageState] = useState<PageState>('LOADING');
  const [data, setData] = useState<HomeData | null>(null);

  useEffect(() => {
    // Simulate API call to GET /api/home/today
    const t = setTimeout(() => {
      setData(MOCK_DATA);
      setPageState(
        MOCK_DATA.todayReview.total === 0 ? 'EMPTY' :
        MOCK_DATA.todayReview.done === MOCK_DATA.todayReview.total ? 'ALL_DONE' :
        'READY',
      );
    }, 400);
    return () => clearTimeout(t);
  }, []);

  const handleStartAll = () => {
    if (pageState === 'EMPTY') {
      nav('/capture');
    } else {
      nav('/review');
    }
  };

  const { todayReview, streak } = data ?? MOCK_DATA;

  // SVG ring math: r=30, circumference=188.5
  const circumference = 2 * Math.PI * 30;
  const dashOffset = circumference * (1 - (todayReview.circleProgress / 100));

  return (
    <main
      className={s.page}
      role="main"
      data-testid="p-home-root"
      data-mood="A"
    >
      {/* StatusBar 已删 · iOS chrome · _archive data-mockup-chrome="iphone-statusbar" */}

      {/* ── Hero (Mood A 240px 深蓝 + 3 blob) ── */}
      <header className={s.hero} role="banner" aria-label={`${data?.studentName ?? '...'}的今日摘要`}>
        <div className={s.blob} aria-hidden="true" />
      </header>

      {/* ── HSafe ── */}
      <div className={s.hsafe}>
        <div className={s.helloRow}>
          <div>
            <p className={s.hello}>周二 · 4 月 21 日 · 早安</p>
            <h1 className={s.name} data-testid="greeting-hero">
              {data?.studentName ?? '...'}，<em className={s.nameEm}>今天继续</em>
            </h1>
          </div>
          <div
            className={s.avatar}
            role="button"
            aria-label="我的主页"
            onClick={() => nav('/me')}
            aria-hidden="true"
          >
            A
          </div>
        </div>

        {/* StreakBar */}
        <div className={s.streakbar} aria-label={`已连续打卡 ${streak.days} 天`}>
          <span className={s.flame} data-testid="streak-bar-fire-icon">
            {/* SVG flame (AC-HOME-005: 必须是 SVG 非 emoji) */}
            <svg
              width="12"
              height="12"
              viewBox="0 0 24 24"
              fill="#FFD166"
              aria-hidden="true"
              role="img"
            >
              <path d="M12 2s4 4 4 8-2 6-2 6 3 1 3 4-3 4-5 4-5-1-5-4 3-4 3-4-2-2-2-6 4-8 4-8z"/>
            </svg>
            连续{' '}
            <strong
              data-testid="streak-bar-days-number"
              style={{ fontWeight: 800 }}
            >
              {streak.days}
            </strong>{' '}天
          </span>
          <span className={s.flameDot} aria-hidden="true" />
          <span>掌握 <strong style={{ color: '#6DE895', fontWeight: 800 }}>142</strong> 题</span>
          <span className={s.flameDot} aria-hidden="true" />
          <span className={s.streakTs}>GMT+8 · 9:41</span>
        </div>
      </div>

      {/* ── Scroll area ── */}
      <div className={s.scroll}>

        {/* ── B2 TodayReviewCard ── */}
        <div
          className={s.reviewhero}
          data-testid="today-review-card"
          role="region"
          aria-label="今日复习"
        >
          <div className={s.rhTop}>
            <div className={s.rhLeft}>
              <p className={s.rhKicker}>Today's review</p>
              <h2 className={s.rhTitle}>
                <em className={s.rhTitleEm} data-testid="today-review-card-total">
                  {todayReview.total} 题
                </em>{' '}待复习
              </h2>
              <p className={s.rhSub}>
                预计{' '}
                <span data-testid="today-review-card-est-min">{todayReview.estMin}</span>{' '}分钟 · 下一次节点 10:15
              </p>
            </div>

            {/* Progress ring */}
            <div
              className={s.rhCircle}
              role="progressbar"
              aria-valuenow={todayReview.circleProgress}
              aria-valuemax={100}
              aria-valuetext={`完成 ${todayReview.circleProgress}%`}
              data-testid="today-review-card-circle-progress"
            >
              <svg className={s.rhCircleSvg} viewBox="0 0 72 72" width="72" height="72">
                <circle
                  cx="36" cy="36" r="30"
                  fill="none"
                  stroke="rgba(255,255,255,.18)"
                  strokeWidth="6"
                />
                <circle
                  cx="36" cy="36" r="30"
                  fill="none"
                  stroke="url(#rhg)"
                  strokeWidth="6"
                  strokeLinecap="round"
                  strokeDasharray={circumference}
                  strokeDashoffset={dashOffset}
                />
                <defs>
                  <linearGradient id="rhg" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0%" stopColor="#FFD166"/>
                    <stop offset="100%" stopColor="#FF6B6B"/>
                  </linearGradient>
                </defs>
              </svg>
              <div className={s.rhPct} aria-hidden="true">
                <span className={s.rhPctN}>{todayReview.circleProgress}%</span>
                <span className={s.rhPctL}>PROGRESS</span>
              </div>
            </div>
          </div>

          {/* Subject chips */}
          <div className={s.rhSplit}>
            {todayReview.subjectDist.map((sd) => (
              <div key={sd.subject} className={s.rhSubChip}>
                <span className={s.rhSubSq} style={{ background: SUBJECT_COLORS[sd.subject] }} />
                <span className={s.rhSubCt}>
                  <em>{sd.count}</em>{SUBJECT_LABELS[sd.subject]}
                </span>
              </div>
            ))}
          </div>

          {/* CTA */}
          <div className={s.rhCta}>
            <button
              className={s.rhBtn}
              data-testid="today-review-card-start-all-btn"
              onClick={handleStartAll}
              type="button"
              aria-label={pageState === 'EMPTY' ? '拍一道新题试试' : '全部开始复习'}
            >
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
                <path d="M4 2 L11 7 L4 12 Z" fill="#0F1A3D"/>
              </svg>
              {pageState === 'EMPTY' ? '拍一道新题试试' :
               pageState === 'ALL_DONE' ? '看看战绩' :
               '全部开始'}
            </button>
            <button
              className={s.rhBtn2}
              type="button"
              aria-label="更多选项"
            >
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                <path d="M2 8 H14 M8 2 V14" stroke="#fff" strokeWidth="1.6" strokeLinecap="round"/>
              </svg>
            </button>
          </div>
        </div>

        {/* ── B3 Weekly Sparkline ── */}
        <nav aria-label="本周数据概览">
          <div className={s.sec}>
            <span className={s.secT}>本周回顾</span>
            <span className={s.secM}>查看全部 ›</span>
          </div>
        </nav>
        <div
          className={s.weekly}
          data-testid="p-home-weekly-sparkline"
          role="region"
          aria-label="本周复习统计"
        >
          <div className={s.weeklyRow}>
            <div className={s.stat}>
              <div className={`${s.statN} ${s.statNGreen}`}>{data?.weekStats.mastered ?? 23}</div>
              <div className={s.statL}>掌握</div>
            </div>
            <div className={s.statSep} aria-hidden="true" />
            <div className={s.stat}>
              <div className={`${s.statN} ${s.statNBlue}`}>{data?.weekStats.added ?? 8}</div>
              <div className={s.statL}>新增</div>
            </div>
            <div className={s.statSep} aria-hidden="true" />
            <div className={s.stat}>
              <div className={`${s.statN} ${s.statNOrange}`}>{data?.weekStats.forgotten ?? 2}</div>
              <div className={s.statL}>遗忘</div>
            </div>
            <div className={s.statSep} aria-hidden="true" />
            <div className={s.stat}>
              <div className={`${s.statN} ${s.statNIndigo}`}>{data?.weekStats.masteryPct ?? 68}%</div>
              <div className={s.statL}>掌握率</div>
            </div>
          </div>
          {/* Sparkline SVG */}
          <div className={s.spark} aria-hidden="true">
            <svg className={s.sparkSvg} viewBox="0 0 300 40" preserveAspectRatio="none">
              <defs>
                <linearGradient id="sparkg" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#34C759" stopOpacity=".35"/>
                  <stop offset="100%" stopColor="#34C759" stopOpacity="0"/>
                </linearGradient>
              </defs>
              <path d="M0 28 L43 22 L86 24 L129 16 L172 12 L215 18 L258 8 L300 14 L300 40 L0 40 Z" fill="url(#sparkg)"/>
              <path d="M0 28 L43 22 L86 24 L129 16 L172 12 L215 18 L258 8 L300 14" stroke="#34C759" strokeWidth="1.8" fill="none" strokeLinecap="round" strokeLinejoin="round"/>
              <circle cx="258" cy="8" r="3.5" fill="#34C759" stroke="#fff" strokeWidth="1.5"/>
            </svg>
          </div>
          <div className={s.weeklyDays} aria-hidden="true">
            <span>周一</span><span>周二</span><span>周三</span><span>周四</span><span>周五</span><span>周六</span>
            <span className={s.weeklyDayToday}>今天</span>
          </div>
        </div>

        {/* ── B4 WeekStrip ── */}
        <nav aria-label="本周排期">
          <div className={s.sec}>
            <span className={s.secT}>本周日程</span>
            <span className={s.secM} onClick={() => nav('/calendar/month')} role="link" tabIndex={0}>月视图 ›</span>
          </div>
        </nav>
        <div
          className={s.weekcard}
          data-testid="week-strip"
          role="region"
          aria-label="本周日程条带"
        >
          <div className={s.wcHead}>
            <div className={s.wcHeadL}>
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
                <rect x="2" y="3" width="10" height="9" rx="2" stroke="#1C1C1E" strokeWidth="1.3"/>
                <path d="M2 6 H12" stroke="#1C1C1E" strokeWidth="1.3"/>
                <path d="M5 1.5 V4 M9 1.5 V4" stroke="#1C1C1E" strokeWidth="1.3" strokeLinecap="round"/>
              </svg>
              4 月 20–26 日
            </div>
            <span
              className={s.wcExpand}
              role="button"
              tabIndex={0}
              aria-label="展开月视图"
              onClick={() => nav('/calendar/month')}
            >
              展开
              <svg viewBox="0 0 10 10" fill="none" width="10" height="10" aria-hidden="true">
                <path d="M3 2 L7 5 L3 8" stroke="#007AFF" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </span>
          </div>
          <div className={s.wcRow}>
            {(data?.weekStrip ?? MOCK_DATA.weekStrip).map((day, idx) => (
              <div
                key={day.date}
                className={`${s.wd} ${day.isToday ? s.wdToday : ''}`}
                data-testid={`week-strip-day-${idx + 1}`}
                data-today={day.isToday ? 'true' : 'false'}
                role="button"
                tabIndex={0}
                aria-label={`${day.weekLabel} ${day.dayNum}${day.isToday ? ' 今天' : ''} ${day.reviewCount > 0 ? `${day.reviewCount}题` : ''}`}
                onClick={() => nav(day.isToday ? '/review' : `/calendar/month`)}
              >
                <span className={s.wdW}>{day.weekLabel}</span>
                <span className={s.wdD}>{day.dayNum}</span>
                <div className={s.wdDots}>
                  {day.subjects.slice(0, 5).map((subj, i) => (
                    <span
                      key={i}
                      className={dotClass(subj, day.tLevels[i] ?? 'T1')}
                      data-testid={`week-strip-day-${idx + 1}-tlevel-${day.tLevels[i] ?? 'T1'}`}
                    />
                  ))}
                </div>
                {day.reviewCount > 0 && day.isToday && (
                  <span className={s.wdNum} aria-label={`${day.reviewCount}题待复习`}>{day.reviewCount}</span>
                )}
              </div>
            ))}
          </div>
          <div className={s.wcFoot}>
            <div className={s.wcLegend}>
              <span className={s.wcLegendItem}><span className={s.dotR} />复习 T1</span>
              <span className={s.wcLegendItem}><span className={s.dotO} />T3</span>
              <span className={s.wcLegendItem}><span className={s.dotG} />T6</span>
              <span className={s.wcLegendItem}><span className={s.dotP} />考试</span>
              <span className={s.wcLegendItem}><span className={s.dotI} />家庭</span>
            </div>
          </div>
        </div>

        {/* ── B5 Messages ── */}
        <div className={s.sec}>
          <span className={s.secT}>最近消息</span>
          <span
            className={s.secM}
            onClick={() => nav('/notifications')}
            role="link"
            tabIndex={0}
            data-testid="p-home-messages-more-link"
          >
            全部 2 ›
          </span>
        </div>
        <div
          className={s.msgs}
          data-testid="p-home-messages"
          role="region"
          aria-label="最近消息"
          aria-live="polite"
        >
          {(data?.messages ?? MOCK_DATA.messages).slice(0, 3).map((msg, idx) => (
            <div
              key={msg.id}
              className={s.msg}
              data-testid={`p-home-messages-item-${idx + 1}`}
              role="button"
              tabIndex={0}
              aria-label={msg.title}
              onClick={() => nav('/notifications')}
            >
              <MsgIcon type={msg.type} />
              <div className={s.msgTx}>
                <div className={s.msgTxT}>{msg.title}</div>
                <div className={s.msgTxS}>{msg.subtitle}</div>
              </div>
              <div className={s.msgTm}>{msg.timeLabel}</div>
            </div>
          ))}
        </div>

        {/* ── B6 WeakKP Hint ── */}
        {data?.weakKP && (
          <>
            <div className={s.sec}>
              <span className={s.secT}>AI 发现的薄弱点</span>
              <span className={s.secM} style={{ color: '#FF9500' }}>1 条待练</span>
            </div>
            <div
              className={s.kpcard}
              data-testid="p-home-weak-kp"
              role="button"
              tabIndex={0}
              aria-label={`薄弱知识点: ${data.weakKP.kpName}`}
              onClick={() => nav(`/wrongbook?kp=${data!.weakKP!.kpId}`)}
            >
              <div className={s.kpHead}>
                <div className={s.kpHeadIc}>
                  <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
                    <path d="M7 1 L13 13 L1 13 Z" stroke="#fff" strokeWidth="1.6" strokeLinejoin="round"/>
                    <path d="M7 5 V9" stroke="#fff" strokeWidth="1.6" strokeLinecap="round"/>
                    <circle cx="7" cy="11" r="0.8" fill="#fff"/>
                  </svg>
                </div>
                <div className={s.kpHeadTtl}>
                  「<em>{data.weakKP.kpName}</em>」最近 {data.weakKP.questionCount} 次都错了
                </div>
              </div>
              <p className={s.kpBody}>
                AI 检测到你在同一知识点上的错误在<strong>加速积累</strong>。建议开启一次针对性专练（约 5 分钟），系统会同步写入你的记忆曲线。
              </p>
              <div className={s.kpActions}>
                <button className={s.kpbtnPr} type="button">
                  <svg width="12" height="12" viewBox="0 0 12 12" fill="none" aria-hidden="true">
                    <path d="M3 2 L9 6 L3 10 Z" fill="#fff"/>
                  </svg>
                  立即专练
                </button>
                <button
                  className={s.kpbtnSc}
                  type="button"
                  onClick={(e) => { e.stopPropagation(); }}
                >
                  稍后再说
                </button>
              </div>
            </div>
          </>
        )}

        {/* ── B7 Quick entries 2x2 ── */}
        <div className={s.sec} aria-hidden="true">
          <span className={s.secT} />
        </div>
        <div
          className={s.quick}
          data-testid="p-home-quick-entries"
          role="navigation"
          aria-label="快捷入口"
        >
          <div
            className={s.qcard}
            data-testid="p-home-quick-entries-item-1"
            role="button"
            tabIndex={0}
            aria-label="错题本 128题 未掌握42"
            onClick={() => nav('/wrongbook')}
          >
            <div className={`${s.qcardIc} ${s.icRed}`}>
              <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
                <path d="M3 3 h9 l3 3 v9 a1.5 1.5 0 0 1-1.5 1.5 H3 a1.5 1.5 0 0 1-1.5-1.5 V4.5 a1.5 1.5 0 0 1 1.5-1.5z" stroke="#fff" strokeWidth="1.4"/>
                <path d="M5 9 h8 M5 12 h5" stroke="#fff" strokeWidth="1.4" strokeLinecap="round"/>
              </svg>
            </div>
            <div>
              <div className={s.qcardTxT}>错题本</div>
              <div className={s.qcardTxS}>128 题 · 未掌握 42</div>
            </div>
            <div className={s.qcardArr} aria-hidden="true">
              <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                <path d="M3 2 L7 5 L3 8" stroke="#8E8E93" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </div>
          </div>

          <div
            className={s.qcard}
            data-testid="p-home-quick-entries-item-2"
            role="button"
            tabIndex={0}
            aria-label="拍一道新错题 自动识别 多学科"
            onClick={() => nav('/capture')}
          >
            <div className={`${s.qcardIc} ${s.icGrn}`}>
              <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
                <rect x="2" y="5" width="14" height="10" rx="2" stroke="#fff" strokeWidth="1.4"/>
                <circle cx="9" cy="10" r="3" stroke="#fff" strokeWidth="1.4"/>
                <path d="M6.5 5 L7.5 3 h3 L11.5 5" stroke="#fff" strokeWidth="1.4" strokeLinejoin="round"/>
              </svg>
            </div>
            <div>
              <div className={s.qcardTxT}>拍一道新错题</div>
              <div className={s.qcardTxS}>自动识别 · 多学科</div>
            </div>
            <div className={s.qcardArr} aria-hidden="true">
              <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                <path d="M3 2 L7 5 L3 8" stroke="#8E8E93" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </div>
          </div>

          <div
            className={s.qcard}
            data-testid="p-home-quick-entries-item-3"
            role="button"
            tabIndex={0}
            aria-label="完整日历 月周日视图"
            onClick={() => nav('/calendar/month')}
          >
            <div className={`${s.qcardIc} ${s.icBlu}`}>
              <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
                <rect x="2.5" y="3.5" width="13" height="12" rx="2" stroke="#fff" strokeWidth="1.4"/>
                <path d="M2.5 7 H15.5" stroke="#fff" strokeWidth="1.4"/>
                <path d="M6 1.5 V4.5 M12 1.5 V4.5" stroke="#fff" strokeWidth="1.4" strokeLinecap="round"/>
              </svg>
            </div>
            <div>
              <div className={s.qcardTxT}>完整日历</div>
              <div className={s.qcardTxS}>月 / 周 / 日视图</div>
            </div>
            <div className={s.qcardArr} aria-hidden="true">
              <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                <path d="M3 2 L7 5 L3 8" stroke="#8E8E93" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </div>
          </div>

          <div
            className={s.qcard}
            data-testid="p-home-quick-entries-item-4"
            role="button"
            tabIndex={0}
            aria-label="偏好与提醒 免打扰 节奏 语言"
            onClick={() => nav('/me')}
          >
            <div className={`${s.qcardIc} ${s.icPur}`}>
              <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
                <path d="M9 2 L11 4 L13.5 3.5 L14 6 L16 7 L15 9 L16 11 L14 12 L13.5 14.5 L11 14 L9 16 L7 14 L4.5 14.5 L4 12 L2 11 L3 9 L2 7 L4 6 L4.5 3.5 L7 4 Z" stroke="#fff" strokeWidth="1.4" strokeLinejoin="round"/>
                <circle cx="9" cy="9" r="2" stroke="#fff" strokeWidth="1.4"/>
              </svg>
            </div>
            <div>
              <div className={s.qcardTxT}>偏好与提醒</div>
              <div className={s.qcardTxS}>免打扰 · 节奏 · 语言</div>
            </div>
            <div className={s.qcardArr} aria-hidden="true">
              <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                <path d="M3 2 L7 5 L3 8" stroke="#8E8E93" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </div>
          </div>
        </div>
      </div>
    </main>
  );
};
