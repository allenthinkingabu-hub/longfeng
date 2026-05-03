/**
 * P-SHARED · 分享只读预览
 * Mood E (teal-observer) · archive ref: _archive/16_shared.html
 * spec: design/system/pages/P-SHARED.spec.md
 *
 * Security constraints:
 *   - HS256 token from URL param
 *   - EXAM_DAY sensitive data is desensitized server-side (stem_preview ≤12 chars)
 *   - 防写动词: write-verb buttons are aria-disabled + onClick intercepted (C4)
 *   - No raw qid / student_id in DOM
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useDeviceFingerprint } from '../../hooks/useDeviceFingerprint';
import s from './Shared.module.css';

/* ── Types (§4 data contract) ── */
type SharedType = 'EXAM_DAY' | 'QUESTION' | 'REVIEW_NODE';

interface SharedResp {
  type: SharedType;
  signature_valid: boolean;
  ttl_sec: number;
  sharer_nick: string;
  sharer_avatar_url: string;
  shared_at: string;
  masked_payload: {
    qid_hash: string;
    subject: 'math' | 'physics' | 'chemistry' | 'english';
    stem_preview: string;
    thumbnail_url_masked: string;
    review_count: number;
    node_stage_preview: number;
  };
  upgrade_cta: {
    text: string;
    target_route: string;
    can_claim: boolean;
  };
}

interface SharedTokenError {
  error: 'TOKEN_EXPIRED' | 'TOKEN_INVALID' | 'TOKEN_REVOKED';
  fallback_route: string;
}

type PageState = 'LOADING' | 'READY' | 'TOKEN_EXPIRED' | 'TOKEN_INVALID' | 'TOKEN_REVOKED' | 'ERROR';

const SUBJECT_LABELS: Record<string, string> = {
  math: '数学 · 高一',
  physics: '物理 · 高二',
  chemistry: '化学 · 高一',
  english: '英语 · 初三',
};

/* ── C4 · 防写动词拦截 ── */
function interceptWriteVerb(e: React.MouseEvent | React.KeyboardEvent, label: string): void {
  e.preventDefault();
  e.stopPropagation();
  console.warn(`[P-SHARED] Write verb intercepted: ${label}. Anonymous users cannot perform write operations.`);
  // In production: show a toast / redirect to auth
}

/* ── StatusBar icons ── */
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

/* ── MemoryCurve preview (B4): 6 grey nodes ── */
const MemoryCurvePreview: React.FC<{ reviewCount: number }> = ({ reviewCount }) => (
  <div data-testid="memory-curve-preview" className={s.memoryCurve} role="region" aria-label={`已被复习 ${reviewCount} 次`}>
    <div className={s.memoryCurveTitle}>▸ 艾宾浩斯曲线 · 预览</div>
    <svg
      data-testid="memory-curve-preview-svg"
      width="100%"
      height="60"
      viewBox="0 0 280 60"
      aria-hidden="true"
    >
      {/* 6 grey nodes per AC-SHARED-004 */}
      {[0, 1, 2, 3, 4, 5].map((i) => (
        <g key={i}>
          <circle
            cx={20 + i * 48}
            cy={40 - i * 4}
            r={6}
            fill="rgba(0,0,0,0.16)"
          />
          <text
            x={20 + i * 48}
            y={56}
            textAnchor="middle"
            fontSize="8"
            fill="rgba(0,0,0,0.36)"
            fontFamily="-apple-system, sans-serif"
          >
            T{i}
          </text>
        </g>
      ))}
      {/* Connecting line */}
      <polyline
        points={[0, 1, 2, 3, 4, 5].map((i) => `${20 + i * 48},${40 - i * 4}`).join(' ')}
        fill="none"
        stroke="rgba(0,0,0,0.12)"
        strokeWidth="1.5"
        strokeDasharray="4 3"
      />
    </svg>
    <p className={s.memoryCurveCount}>已被复习 {reviewCount} 次</p>
  </div>
);

/* ── Token error screens ── */
const TokenErrorScreen: React.FC<{
  type: 'TOKEN_EXPIRED' | 'TOKEN_INVALID' | 'TOKEN_REVOKED';
  onNavigate: () => void;
}> = ({ type, onNavigate }) => {
  const messages: Record<string, { icon: string; title: string; desc: string }> = {
    TOKEN_EXPIRED: { icon: '⏱', title: '这个分享已过期', desc: '分享链接仅 7 天内有效，请向分享者重新申请' },
    TOKEN_INVALID: { icon: '🔗', title: '分享链接无效', desc: '链接可能已损坏，请检查 URL 是否完整' },
    TOKEN_REVOKED: { icon: '🚫', title: '分享者已撤销', desc: '该分享链接已被取消，请联系分享者' },
  };
  const msg = messages[type] ?? messages.TOKEN_EXPIRED;

  const testId = type === 'TOKEN_EXPIRED' ? 'token-expired-screen'
    : type === 'TOKEN_INVALID' ? 'token-invalid-screen'
    : 'token-revoked-screen';

  return (
    <div className={s.errorScreen} data-testid={testId} role="alertdialog" aria-modal="true" aria-label={msg.title}>
      <div style={{ fontSize: 48 }} aria-hidden="true">{msg.icon}</div>
      <h2 className={s.errorTitle}>{msg.title}</h2>
      <p className={s.errorDesc}>{msg.desc}</p>
      <button
        className={s.errorCta}
        data-testid={type === 'TOKEN_EXPIRED' ? 'token-expired-cta' : undefined}
        onClick={onNavigate}
        aria-label="去看产品介绍"
      >
        去看产品介绍
      </button>
    </div>
  );
};

/* ── Main component ── */
export const SharedPage: React.FC = () => {
  const { shareToken } = useParams<{ shareToken: string }>();
  const nav = useNavigate();
  const deviceFp = useDeviceFingerprint();
  const [pageState, setPageState] = useState<PageState>('LOADING');
  const [data, setData] = useState<SharedResp | null>(null);
  const [ttlCountdown, setTtlCountdown] = useState<number>(0);
  const ttlRef = useRef<ReturnType<typeof setInterval> | null>(null);

  /* Load share data */
  useEffect(() => {
    if (!shareToken) {
      setPageState('TOKEN_INVALID');
      return;
    }
    let cancelled = false;
    async function load() {
      try {
        const res = await fetch(`/api/share/${shareToken}`, {
          headers: { 'Cache-Control': 'no-store' },
        });
        if (cancelled) return;

        if (res.status === 410) {
          setPageState('TOKEN_EXPIRED');
          return;
        }
        if (res.status === 403) {
          // Could be invalid or revoked — distinguish by body
          const err = await res.json() as SharedTokenError;
          setPageState(err.error === 'TOKEN_REVOKED' ? 'TOKEN_REVOKED' : 'TOKEN_INVALID');
          return;
        }
        if (!res.ok) {
          setPageState('ERROR');
          return;
        }

        const json = await res.json() as SharedResp;
        if (!json.signature_valid) {
          setPageState('TOKEN_INVALID');
          return;
        }

        setData(json);
        setTtlCountdown(json.ttl_sec);
        setPageState('READY');

        // Emit view event
        if (deviceFp) {
          void fetch('/api/analytics/event', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              event: 'anon_share_view',
              device_fp: deviceFp,
              type: json.type,
              sharer_id_hash: 'anonymized',
            }),
          }).catch(() => { /* silently fail */ });
        }
      } catch {
        if (!cancelled) setPageState('ERROR');
      }
    }
    void load();
    return () => { cancelled = true; };
  }, [shareToken, deviceFp]);

  /* TTL countdown */
  useEffect(() => {
    if (pageState !== 'READY' || ttlCountdown <= 0) return;
    ttlRef.current = setInterval(() => {
      setTtlCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(ttlRef.current!);
          setPageState('TOKEN_EXPIRED');
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => { if (ttlRef.current) clearInterval(ttlRef.current); };
  }, [pageState]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleUpgradeCta = useCallback(() => {
    if (!data) return;
    void fetch('/api/analytics/event', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        event: 'anon_share_upgrade_cta',
        device_fp: deviceFp,
        type: data.type,
        cta_position: 'bottom',
      }),
    }).catch(() => { /* silently fail */ });
    nav(data.upgrade_cta.target_route ?? `/auth?redirect=/s/${shareToken}`);
  }, [data, deviceFp, nav, shareToken]);

  const handleBack = () => nav(-1);
  const navigateToLanding = () => nav('/welcome');

  /* ── Token error states ── */
  if (pageState === 'TOKEN_EXPIRED') {
    return (
      <div className={s.phone} data-testid="p-shared" data-mood="E">
        <TokenErrorScreen type="TOKEN_EXPIRED" onNavigate={navigateToLanding} />
        <div className={s.homebar} aria-hidden="true" />
      </div>
    );
  }
  if (pageState === 'TOKEN_INVALID') {
    return (
      <div className={s.phone} data-testid="p-shared" data-mood="E">
        <TokenErrorScreen type="TOKEN_INVALID" onNavigate={navigateToLanding} />
        <div className={s.homebar} aria-hidden="true" />
      </div>
    );
  }
  if (pageState === 'TOKEN_REVOKED') {
    return (
      <div className={s.phone} data-testid="p-shared" data-mood="E">
        <TokenErrorScreen type="TOKEN_REVOKED" onNavigate={navigateToLanding} />
        <div className={s.homebar} aria-hidden="true" />
      </div>
    );
  }

  /* ── Loading skeleton ── */
  if (pageState === 'LOADING') {
    return (
      <div className={s.phone} data-testid="p-shared" data-mood="E" aria-busy="true" aria-label="加载分享内容">
        <div className={s.statusbar} aria-hidden="true">
          <span className={s.time}>9:41</span>
          <StatusIcons />
        </div>
        <div className={s.header} />
        <div className={s.scroll} style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', marginTop: 60 }}>
          <div aria-label="加载中" style={{ color: '#8E8E93', fontSize: 14 }}>加载中…</div>
        </div>
        <div className={s.homebar} aria-hidden="true" />
      </div>
    );
  }

  if (!data) return null;

  const payload = data.masked_payload;
  const subjectLabel = SUBJECT_LABELS[payload.subject] ?? payload.subject;
  /* stem_preview: first 12 chars clear, rest masked */
  const stemClear = payload.stem_preview.slice(0, 12);
  const stemBlurred = payload.stem_preview.length > 12 ? payload.stem_preview.slice(12) : '…内容已脱敏';
  const ttlMinutes = Math.floor(ttlCountdown / 60);

  return (
    <div
      className={s.phone}
      data-testid="p-shared"
      data-mood="E"
    >
      {/* B1 StatusBar */}
      <div className={s.statusbar} data-testid="p-shared-statusbar" aria-hidden="true">
        <span className={s.time}>9:41</span>
        <StatusIcons />
      </div>

      {/* Teal header (Mood E) */}
      <header
        className={s.header}
        role="banner"
        aria-label="分享只读预览"
      />

      {/* Anon nav */}
      <nav className={s.anonNav} aria-label="顶部导航">
        <button className={s.back} onClick={handleBack} aria-label="返回">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <polyline points="15 18 9 12 15 6"/>
          </svg>
        </button>
        <div className={s.brandPill} aria-label="分享链接">
          <span className={s.brandDot} aria-hidden="true" />
          <span className={s.brandNm}>分享链接 · Shared</span>
        </div>
        <button
          className={s.signinBtn}
          onClick={handleUpgradeCta}
          aria-label="登录账号"
        >
          登录
        </button>
      </nav>

      {/* B2 · Sharer banner */}
      <div
        className={s.sharer}
        data-testid="sharer-banner"
        role="region"
        aria-label={`来自 ${data.sharer_nick} 的分享`}
      >
        <div
          className={s.sharerAv}
          data-testid="sharer-banner-avatar"
          role="img"
          aria-label={`${data.sharer_nick} 的头像`}
        >
          {data.sharer_nick.charAt(0).toUpperCase()}
        </div>
        <div>
          <div className={s.sharerFrom}>来自同学分享 · {ttlMinutes > 0 ? `${ttlMinutes} 分钟前` : '刚刚'}</div>
          <div
            className={s.sharerName}
            data-testid="sharer-banner-text"
            aria-label={`${data.sharer_nick} 和你分享了一道错题`}
          >
            <em>{data.sharer_nick}</em> 和你分享了一道错题
          </div>
        </div>
      </div>

      {/* Scroll area */}
      <main className={s.scroll} role="main">

        {/* Preview ribbon */}
        <div className={s.ribbon} role="note" aria-label="预览模式说明">
          <span className={s.ribbonBadge}>PREVIEW</span>
          预览模式 · 已脱敏显示 · 加入错题本后可查看完整 AI 分析
        </div>

        {/* B3 · Masked question card (AC-SHARED-002 + AC-SHARED-003) */}
        <article
          data-testid="masked-question"
          className={s.qcard}
          aria-label="脱敏题目预览"
        >
          <div className={s.qtop}>
            <div className={s.qtopLeft}>
              <span className={s.subChip}>{subjectLabel}</span>
              <span className={s.diffChip}>★★★ 中档</span>
            </div>
            <span className={s.stageChip}>
              📅 第 <em>T{payload.node_stage_preview}</em> 节
            </span>
          </div>

          {/* Question image (blurred) */}
          <div className={s.qimg} role="img" aria-label="题目图片（已脱敏）">
            <div className={s.qimgForm}>
              <div className={s.qimgLine} data-testid="masked-question-stem-clear" aria-label={`题干前 12 字：${stemClear}`}>
                {stemClear}
              </div>
              <div
                className={`${s.qimgLine} ${s.qimgLineBlur}`}
                data-testid="masked-question-stem-blurred"
                aria-hidden="true"
              >
                {stemBlurred}
              </div>
            </div>
            <div className={s.qimgLock} aria-label="原图已脱敏">
              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <rect x="3" y="11" width="18" height="11" rx="2"/>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
              </svg>
              原图脱敏
            </div>
          </div>

          {/* Question text (partly masked) */}
          <div className={s.qtext} aria-label="题干（部分脱敏）">
            题干：{stemClear}
            <span className={s.mask} aria-hidden="true">&nbsp;&nbsp;&nbsp;&nbsp;后续内容&nbsp;&nbsp;&nbsp;&nbsp;</span>
            出错在
            <span className={s.mask} aria-hidden="true">&nbsp;&nbsp;&nbsp;&nbsp;步骤&nbsp;&nbsp;&nbsp;&nbsp;</span>
            处。
          </div>

          {/* KP pills */}
          <div className={s.kps}>
            <span className={s.kpPill}># 知识点</span>
            <span className={`${s.kpPill} ${s.kpPillLocked}`}>
              <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <rect x="3" y="11" width="18" height="11" rx="2"/>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
              </svg>
              + 2 个知识点
            </span>
          </div>

          {/* B3 · Masked overlay (blocks student answer / error reason / notes) */}
          <div
            className={s.maskedOverlay}
            data-testid="masked-question-overlay"
            role="region"
            aria-label="注册后查看完整内容 · 点击跳转登录"
            onClick={handleUpgradeCta}
            style={{ cursor: 'pointer' }}
          >
            <div className={s.maskedOverlayIc} aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <rect x="3" y="11" width="18" height="11" rx="2"/>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
              </svg>
            </div>
            <div className={s.maskedOverlayTitle}>注册查看 · 完整 AI 分析</div>
            <div className={s.maskedOverlayDesc}>含错因 / 正解 / 变式 / 知识点网络 · 自动排 T0–T6 节点</div>
          </div>
        </article>

        {/* B4 · Memory curve preview */}
        <MemoryCurvePreview reviewCount={payload.review_count} />

        {/* B5 · Share meta */}
        <section
          data-testid="share-meta"
          className={s.shareMeta}
          aria-label="分享元信息"
        >
          <div className={s.shareMetaRow}>
            <span className={s.shareMetaKey}>分享时间</span>
            <span className={s.shareMetaVal}>{new Date(data.shared_at).toLocaleDateString('zh-CN')}</span>
          </div>
          <div className={s.shareMetaRow}>
            <span className={s.shareMetaKey}>有效期剩余</span>
            <span className={s.shareMetaVal}>{Math.floor(ttlCountdown / 3600)}h {Math.floor((ttlCountdown % 3600) / 60)}m</span>
          </div>
          <div className={s.shareMetaRow}>
            <span className={s.shareMetaKey}>审计记录</span>
            <span className={s.shareMetaWarn}>已记录 IP · 设备指纹</span>
          </div>
        </section>

        {/* 防写动词: 写操作按钮 (comment / bookmark) — all aria-disabled (AC-SHARED-008) */}
        <div
          role="group"
          aria-label="操作（仅注册用户可用）"
          style={{ marginTop: 12, display: 'flex', gap: 8, padding: '0 2px' }}
        >
          {/* Comment button — write verb intercepted */}
          <button
            aria-disabled="true"
            aria-label="评论（注册后可用）"
            onClick={(e) => interceptWriteVerb(e, 'comment')}
            style={{
              flex: 1, height: 36, borderRadius: 10,
              background: 'rgba(60,60,67,0.06)',
              border: '0.5px solid rgba(60,60,67,0.12)',
              color: '#8E8E93', fontSize: 12, fontWeight: 600,
              cursor: 'not-allowed', opacity: 0.6,
            }}
          >
            💬 评论
          </button>
          {/* Bookmark button — write verb intercepted */}
          <button
            aria-disabled="true"
            aria-label="收藏（注册后可用）"
            onClick={(e) => interceptWriteVerb(e, 'bookmark')}
            style={{
              flex: 1, height: 36, borderRadius: 10,
              background: 'rgba(60,60,67,0.06)',
              border: '0.5px solid rgba(60,60,67,0.12)',
              color: '#8E8E93', fontSize: 12, fontWeight: 600,
              cursor: 'not-allowed', opacity: 0.6,
            }}
          >
            🔖 收藏
          </button>
        </div>
      </main>

      {/* B6 · Sticky upgrade CTA dock */}
      <footer className={s.ctaDock} role="contentinfo" aria-label="注册升级">
        <button
          className={s.ctaJoin}
          data-testid="upgrade-cta-fixed"
          onClick={handleUpgradeCta}
          aria-label={data.upgrade_cta.text ?? '注册查看 + 拥有自己的错题本'}
        >
          {data.upgrade_cta.text ?? '注册查看 + 拥有自己的错题本'}
          <span className={s.ctaJoinBadge} aria-hidden="true">FREE</span>
        </button>
        <button
          className={s.ctaSkip}
          onClick={() => nav('/welcome')}
          aria-label="先看看产品介绍"
        >
          先看看产品介绍 →
        </button>
      </footer>

      <div className={s.homebar} aria-hidden="true" />
    </div>
  );
};

export default SharedPage;
