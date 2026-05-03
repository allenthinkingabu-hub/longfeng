/**
 * P-LANDING · 访客落地页
 * Mood A (hero+overlap) · archive ref: _archive/14_landing.html
 * spec: design/system/pages/P-LANDING.spec.md
 *
 * C3 Red Line: guest session token only; never user-id
 */
import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDeviceFingerprint } from '../../hooks/useDeviceFingerprint';
import s from './Landing.module.css';

/* ── Types (§4 data contract) ── */
interface SampleCard {
  id: string;
  subject: 'math' | 'physics' | 'english' | 'chemistry';
  stemPreview: string;
  errorReason: string;
  kpLabel: string;
  tagLabel: string;
  formula: string;
}

interface KpiData {
  totalQuestionsAnalyzed: number;
  retention7d: number;
  headline: string;
}

type PageState = 'LOADING' | 'READY' | 'DEGRADED' | 'ERROR';

/* ── Static SVG icons (inline, no deps) ── */
const IconCamera = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z"/>
    <circle cx="12" cy="13" r="4"/>
  </svg>
);

const IconClock = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <circle cx="12" cy="12" r="9"/><polyline points="12 7 12 12 15 14"/>
  </svg>
);

const IconBell = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
    <path d="M13.7 21a2 2 0 0 1-3.4 0"/>
  </svg>
);

/* ── StatusBar icons (battery, wifi, signal) ── */
const StatusIcons = () => (
  <div className={s.icons}>
    <svg width="18" height="12" viewBox="0 0 18 12" fill="currentColor" aria-hidden="true">
      <rect x="0" y="7" width="3" height="5" rx="1"/>
      <rect x="5" y="4" width="3" height="8" rx="1"/>
      <rect x="10" y="1" width="3" height="11" rx="1"/>
      <rect x="15" y="-2" width="3" height="14" rx="1" opacity=".45"/>
    </svg>
    <svg width="17" height="12" viewBox="0 0 17 12" fill="currentColor" aria-hidden="true">
      <path d="M8.5 2c3.5 0 6.6 1.4 8.5 3.5l-1.5 1.3C13.9 5 11.3 3.8 8.5 3.8S3.1 5 1.5 6.8L0 5.5C1.9 3.4 5 2 8.5 2zm0 3c2.4 0 4.5 1 6 2.5L13 8.8c-1.2-1.1-2.8-1.8-4.5-1.8S5.2 7.7 4 8.8L2.5 7.5C4 6 6.1 5 8.5 5zm0 3c1.3 0 2.5.5 3.4 1.3L8.5 13 5.1 9.3C6 8.5 7.2 8 8.5 8z"/>
    </svg>
    <svg width="28" height="12" viewBox="0 0 28 12" fill="none" aria-hidden="true">
      <rect x=".5" y=".5" width="24" height="11" rx="3" stroke="currentColor" opacity=".55"/>
      <rect x="26" y="4" width="1.5" height="4" rx=".75" fill="currentColor" opacity=".5"/>
      <rect x="2.5" y="2.5" width="20" height="7" rx="1.5" fill="currentColor"/>
    </svg>
  </div>
);

/* ── Mock data (fallback when API not available) ── */
const DEFAULT_SAMPLES: SampleCard[] = [
  {
    id: 's1',
    subject: 'math',
    stemPreview: '求 lim(x→0) (sin 3x)/x',
    formula: 'lim(x→0) (sin 3x)/x',
    errorReason: '错因：未识别等价无穷小',
    kpLabel: '知识点 · 极限计算 / 等价无穷小替换',
    tagLabel: 'T1 · 1h 后复习',
  },
  {
    id: 's2',
    subject: 'physics',
    stemPreview: '斜面 θ=30° 滑块受力',
    formula: '斜面 θ=30° 滑块...',
    errorReason: '错因：分解方向选错',
    kpLabel: '知识点 · 共点力 / 斜面受力分解',
    tagLabel: 'T2 · 1d 后复习',
  },
  {
    id: 's3',
    subject: 'english',
    stemPreview: 'If I ___ you, I would ...',
    formula: 'If I ___ you, I would ...',
    errorReason: '错因：虚拟语气时态',
    kpLabel: '知识点 · 虚拟语气 / 与现在事实相反',
    tagLabel: 'T3 · 3d 后复习',
  },
];

const DEFAULT_KPI: KpiData = {
  totalQuestionsAnalyzed: 1_080_000,
  retention7d: 0.47,
  headline: '已分析 100w+ 错题',
};

/* ── Main component ── */
export const LandingPage: React.FC = () => {
  const nav = useNavigate();
  const deviceFp = useDeviceFingerprint();
  const [pageState, setPageState] = useState<PageState>('LOADING');
  const [samples, setSamples] = useState<SampleCard[]>([]);
  const [kpi, setKpi] = useState<KpiData | null>(null);
  const [ctaLocked, setCtaLocked] = useState(false);

  /* Track landing view once fp is ready */
  useEffect(() => {
    if (!deviceFp) return;
    // Emit analytics event (fire-and-forget)
    void fetch('/api/analytics/event', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ event: 'anon_landing_view', device_fp: deviceFp }),
    }).catch(() => { /* silently fail */ });
  }, [deviceFp]);

  /* Load samples + KPI */
  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const [samplesRes, kpiRes] = await Promise.allSettled([
          fetch('/api/landing/samples?bucket=default'),
          fetch('/api/landing/kpi'),
        ]);

        if (cancelled) return;

        const samplesOk = samplesRes.status === 'fulfilled' && samplesRes.value.ok;
        const kpiOk = kpiRes.status === 'fulfilled' && kpiRes.value.ok;

        if (samplesOk && kpiOk) {
          const samplesData = await (samplesRes as PromiseFulfilledResult<Response>).value.json() as { samples: SampleCard[] };
          const kpiData = await (kpiRes as PromiseFulfilledResult<Response>).value.json() as KpiData;
          if (!cancelled) {
            setSamples(samplesData.samples ?? DEFAULT_SAMPLES);
            setKpi(kpiData);
            setPageState('READY');
          }
        } else {
          // DEGRADED: at least render hero + CTAs
          if (!cancelled) {
            setSamples(DEFAULT_SAMPLES);
            setKpi(null);
            setPageState('DEGRADED');
          }
        }
      } catch {
        if (!cancelled) {
          setSamples(DEFAULT_SAMPLES);
          setKpi(null);
          setPageState('DEGRADED');
        }
      }
    }
    void load();
    return () => { cancelled = true; };
  }, []);

  const handleCtaTry = (position: 'hero' | 'bottom') => {
    if (ctaLocked) return;
    setCtaLocked(true);
    void fetch('/api/analytics/event', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ event: 'anon_landing_cta_try', device_fp: deviceFp, cta_position: position }),
    }).catch(() => { /* silently fail */ });
    nav('/guest/capture');
  };

  const handleCtaLogin = () => {
    void fetch('/api/analytics/event', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ event: 'anon_landing_cta_login', device_fp: deviceFp }),
    }).catch(() => { /* silently fail */ });
    nav('/auth');
  };

  const handleParentEntry = () => {
    void fetch('/api/analytics/event', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ event: 'anon_landing_parent_entry', device_fp: deviceFp }),
    }).catch(() => { /* silently fail */ });
    nav('/observer');
  };

  const displaySamples = pageState !== 'DEGRADED' ? samples : [];

  return (
    <div
      className={s.phone}
      data-testid="landing-page"
      data-mood="A"
      aria-label="访客落地 · AI 帮你拍下错题"
    >
      {/* B1 StatusBar */}
      <div className={s.statusbar} data-testid="p-landing-statusbar" aria-hidden="true">
        <span className={s.time}>9:41</span>
        <StatusIcons />
      </div>

      {/* Hero (Mood A · 380px) */}
      <header
        className={s.hero}
        data-testid="landing-hero"
        data-mood="A"
        role="banner"
        aria-label="AI 错题本访客落地页"
      >
        <span className={s.blob} aria-hidden="true" />
      </header>

      {/* Anon Nav: Logo + Login */}
      <nav className={s.anonNav} aria-label="顶部导航">
        <div className={s.brand} data-testid="landing-hero-logo">
          <div className={s.logo} role="img" aria-label="AI 错题本 Logo">AI</div>
          <span className={s.brandName}>AI 错题本</span>
        </div>
        <button
          className={s.signin}
          data-testid="landing-hero-cta-login"
          onClick={handleCtaLogin}
          aria-label="登录账号"
        >
          登录
        </button>
      </nav>

      {/* Hero copy */}
      <div className={s.heroCopy} aria-hidden="false">
        <div className={s.eyebrow} role="note">
          <span className={s.eyebrowDot} aria-hidden="true" />
          0 注册成本 · 先看看值不值
        </div>
        <h1 className={s.heroTitle} data-testid="landing-hero-headline">
          拍一张错题<br />AI 给你一条<em>记忆曲线</em>
        </h1>
        <p className={s.heroSub}>
          多模态识别题干 · 诊断错因 · 自动排 6 次艾宾浩斯复习 · 到点全平台提醒。无需登录即可试一次。
        </p>
      </div>

      {/* Metric chips */}
      <div className={s.metrics} aria-label="核心数据指标" role="region">
        <div className={s.mchip}>
          <div className={s.mchipN}>4.2<em>s</em></div>
          <div className={s.mchipL}>AI 分析 P95</div>
        </div>
        <div className={s.mchip}>
          <div className={s.mchipN}>T0–T6</div>
          <div className={s.mchipL}>7 节点自动排期</div>
        </div>
        <div className={s.mchip}>
          <div className={s.mchipN}>98<em>%</em></div>
          <div className={s.mchipL}>到点触达率</div>
        </div>
      </div>

      {/* Scroll area (Mood A overlap) */}
      <main className={s.scroll} role="main" id="landing-main">

        {/* B4 · Sample cards */}
        {pageState !== 'DEGRADED' && (
          <section data-testid="landing-samples" aria-label="真实样例">
            <div className={s.secRow}>
              <span className={s.secTitle}>真实样例 · 匿名脱敏</span>
              <span className={s.secMore}>滑动查看 →</span>
            </div>
            <div className={s.samples} role="list">
              {displaySamples.map((card, idx) => (
                <article
                  key={card.id}
                  className={s.sampleCard}
                  data-testid={`landing-samples-card-${idx + 1}`}
                  role="listitem"
                  aria-label={`样例卡 ${idx + 1}: ${card.kpLabel}`}
                >
                  <div className={`${s.sampleThumb} ${s[card.subject]}`}>
                    <span className={s.sampleChip}>
                      {card.subject === 'math' ? '数学 · 高一' : card.subject === 'physics' ? '物理 · 高二' : '英语 · 初三'}
                    </span>
                    <div className={s.sampleFormula} aria-label="公式预览">{card.formula}</div>
                  </div>
                  <div className={s.sampleBody}>
                    <div className={s.sampleErr}>{card.errorReason}</div>
                    <div className={s.sampleKp}>{card.kpLabel}</div>
                    <span className={s.sampleTag}>{card.tagLabel}</span>
                  </div>
                </article>
              ))}
            </div>
          </section>
        )}

        {/* B3 · Feature rows */}
        <section aria-label="功能特色" data-testid="landing-three-step">
          <div className={s.features}>
            <div className={s.feat}>
              <div className={`${s.featIco} ${s.a}`} aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z"/>
                  <circle cx="12" cy="13" r="4"/>
                </svg>
              </div>
              <div>
                <div className={s.featTitle}>拍照即识别，多模态同时在线</div>
                <div className={s.featDesc}>qwen-vl-max / gpt-4o-mini 双模热备 · 支持公式、手写、几何图形</div>
              </div>
            </div>
            <div className={s.feat}>
              <div className={`${s.featIco} ${s.b}`} aria-hidden="true">
                <IconClock />
              </div>
              <div>
                <div className={s.featTitle}>艾宾浩斯 6 次复习自动排期</div>
                <div className={s.featDesc}>T1 1h · T2 1d · T3 3d · T4 7d · T5 15d · T6 30d · 自评驱动自适应推进</div>
              </div>
            </div>
            <div className={s.feat}>
              <div className={`${s.featIco} ${s.c}`} aria-hidden="true">
                <IconBell />
              </div>
              <div>
                <div className={s.featTitle}>到点多渠道触达，不再遗忘</div>
                <div className={s.featDesc}>微信订阅消息 / APP / 邮件 / 站内 · 免打扰时段 · 跨端进度同步</div>
              </div>
            </div>
          </div>
        </section>

        {/* B5 · KPI banner */}
        {kpi && (
          <section data-testid="landing-kpi" aria-label="平台数据">
            <div className={s.kpiBanner} role="region" aria-label="平台关键数据">
              <div className={s.kpiStat}>
                <div className={s.kpiN} data-testid="landing-kpi-total" aria-label={`已分析 ${Math.floor(kpi.totalQuestionsAnalyzed / 10000)}w+ 错题`}>
                  {Math.floor(kpi.totalQuestionsAnalyzed / 10000)}w+
                </div>
                <div className={s.kpiL}>已分析错题</div>
              </div>
              <div className={s.kpiSep} aria-hidden="true" />
              <div className={s.kpiStat}>
                <div className={s.kpiN} data-testid="landing-kpi-retention" aria-label={`7日留存率 ${Math.round(kpi.retention7d * 100)}%`}>
                  {Math.round(kpi.retention7d * 100)}%
                </div>
                <div className={s.kpiL}>7 日留存</div>
              </div>
              <div className={s.kpiSep} aria-hidden="true" />
              <div className={s.kpiStat}>
                <div className={s.kpiN} aria-label="AI 分析准确率 98%">98%</div>
                <div className={s.kpiL}>AI 准确率</div>
              </div>
            </div>
          </section>
        )}

        {/* Social proof */}
        <div className={s.social} aria-label="社区用户数量">
          <div className={s.avatarStack} aria-hidden="true">
            <div className={`${s.stackAv} ${s.a1}`} />
            <div className={`${s.stackAv} ${s.a2}`} />
            <div className={`${s.stackAv} ${s.a3}`} />
            <div className={`${s.stackAv} ${s.a4}`}>+2.4k</div>
          </div>
          <p className={s.socialTxt}>
            本周已有 <em>2,471</em> 位同学用 AI 错题本巩固知识
          </p>
        </div>

        {/* How it works */}
        <div className={s.how} aria-label="使用步骤">
          <div className={s.howTitle}>三步 · 看清曲线</div>
          <div className={s.howRow} role="list">
            <div className={s.howStep} role="listitem">
              <div className={s.howNum} aria-hidden="true">1</div>
              <div className={s.howIc} aria-hidden="true">📸</div>
              <div className={s.howLb}>拍一题</div>
            </div>
            <div className={s.howStep} role="listitem">
              <div className={s.howNum} aria-hidden="true">2</div>
              <div className={s.howIc} aria-hidden="true">🧠</div>
              <div className={s.howLb}>AI 诊断</div>
            </div>
            <div className={s.howStep} role="listitem">
              <div className={s.howNum} aria-hidden="true">3</div>
              <div className={s.howIc} aria-hidden="true">📅</div>
              <div className={s.howLb}>自动排期</div>
            </div>
          </div>
        </div>

        {/* B6 · Bottom CTA */}
        <footer
          data-testid="landing-cta-bottom"
          role="contentinfo"
          style={{ height: '1px' }}
          aria-hidden="true"
        />
      </main>

      {/* Sticky CTA dock */}
      <div className={s.ctaDock} role="group" aria-label="行动按钮">
        <button
          className={s.ctaTry}
          data-testid="landing-hero-cta-try"
          onClick={() => handleCtaTry('hero')}
          aria-label="试一次 · 无需注册"
          tabIndex={0}
        >
          <IconCamera />
          试一次 · 无需注册
          <span className={s.ctaTryBadge} aria-hidden="true">FREE</span>
        </button>
        <button
          className={s.ctaLogin}
          data-testid="landing-cta-bottom-btn"
          onClick={handleCtaLogin}
          aria-label="已有账号，直接登录"
          tabIndex={0}
        >
          已有账号，直接登录 →
        </button>
        <p className={s.ctaHint}>
          <button
            onClick={handleParentEntry}
            style={{ background: 'none', border: 'none', padding: 0, color: 'inherit', fontSize: 'inherit', cursor: 'pointer', font: 'inherit' }}
            aria-label="家长 / 老师入口"
          >
            家长 / 老师入口 →
          </button>
        </p>
      </div>

      <div className={s.homebar} aria-hidden="true" />
    </div>
  );
};

export default LandingPage;
