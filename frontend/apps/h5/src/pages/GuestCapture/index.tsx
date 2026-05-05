/**
 * P-GUEST-CAPTURE · 游客拍题
 * Mood C (dark-camera) · archive ref: _archive/15_guest_capture.html
 * spec: design/system/pages/P-GUEST-CAPTURE.spec.md
 *
 * C3 Red Line:
 *   - POST /api/guest/session → write localStorage `guest_session_token`
 *   - NEVER write user-id field
 *   - NEVER pollute auth session
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDeviceFingerprint } from '../../hooks/useDeviceFingerprint';
import s from './GuestCapture.module.css';

const GUEST_SESSION_KEY = 'guest_session_token'; // C3 compliant key name

type Subject = 'math' | 'physics' | 'chemistry' | 'english';
type CaptureState = 'IDLE' | 'CAPTURED' | 'UPLOADING' | 'ANALYZING' | 'QUOTA_EXHAUSTED' | 'ERROR';

/** ERROR overlay 文案分类 · BUG-LF-20 fix 后增 · 替代 hardcode "请检查相机权限" 误导文案 */
type ErrorCode = 'PRESIGN' | 'UPLOAD' | 'ANALYZE' | 'NETWORK' | 'CAMERA_PERMISSION';

/** BE presign envelope · 跟 BUG-LF-20 修法对齐 */
type PresignResp = {
  code: number;
  message: string;
  data?: { url: string; image_url: string; object_key?: string; expires_in_sec?: string; method?: string };
  trace_id?: string;
};

const ERROR_COPY: Record<ErrorCode, { title: string; desc: string }> = {
  PRESIGN: { title: '上传准备失败', desc: '服务器暂时不可用 · 请稍后再试' },
  UPLOAD: { title: '图片上传失败', desc: '请检查网络后重试 · 或换张更小的图' },
  ANALYZE: { title: 'AI 分析失败', desc: '服务暂时繁忙 · 请稍后再试' },
  NETWORK: { title: '网络异常', desc: '请检查 WiFi / 4G 后重试' },
  CAMERA_PERMISSION: { title: '需要相机权限', desc: '浏览器拒绝了相机访问 · 你也可以选相册图片' },
};

const SUBJECTS: { value: Subject; label: string }[] = [
  { value: 'math', label: '数学' },
  { value: 'physics', label: '物理' },
  { value: 'chemistry', label: '化学' },
  { value: 'english', label: '英语' },
];

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

function safeLocalStorageSet(key: string, value: string): void {
  try { localStorage.setItem(key, value); } catch { /* storage blocked */ }
}

export const GuestCapturePage: React.FC = () => {
  const nav = useNavigate();
  const deviceFp = useDeviceFingerprint();
  const [selectedSubject, setSelectedSubject] = useState<Subject>('math');
  const [captureState, setCaptureState] = useState<CaptureState>('IDLE');
  const [quotaRemaining, setQuotaRemaining] = useState<number>(1);
  /** BUG-LF-20 / Phase 1.1 fix · 文案根据 errorCode 切换 · 替代 hardcode "请检查相机权限" */
  const [lastError, setLastError] = useState<{ code: ErrorCode; detail?: string } | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  /** Phase 1.2 · ERROR overlay fallback · "选文件" 按钮共用 · 跟 source-file tab 同实现 */
  const openFileDialog = useCallback(() => {
    const inp = document.createElement('input');
    inp.type = 'file';
    inp.accept = '.pdf,.doc,.docx,image/*';
    inp.onchange = (e) => {
      const f = (e.target as HTMLInputElement).files?.[0];
      if (f) void processCapture(f);
    };
    inp.click();
  }, []); // processCapture 是 function declaration · 不进 deps

  /* Emit view event once fp ready */
  useEffect(() => {
    if (!deviceFp) return;
    void fetch('/api/analytics/event', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        event: 'anon_guest_capture_view',
        device_fp: deviceFp,
        entry_source: 'landing',
        quota_remaining: quotaRemaining,
      }),
    }).catch(() => { /* silently fail */ });
  }, [deviceFp]); // eslint-disable-line react-hooks/exhaustive-deps

  /* SC-12 异常 · mount 时拉取最新 quota · 0 时仅更新 banner 文案 · 不切全屏（保留 camera-preview + banner 可见，e2e POM 依赖）*/
  useEffect(() => {
    let cancelled = false;
    void fetch('/api/guest/quota', { headers: { 'Cache-Control': 'no-store' } })
      .then((res) => (res.ok ? (res.json() as Promise<{ quotaRemaining?: number }>) : null))
      .then((data) => {
        if (cancelled || !data) return;
        const q = typeof data.quotaRemaining === 'number' ? data.quotaRemaining : 1;
        setQuotaRemaining(q);
      })
      .catch(() => { /* silently keep default */ });
    return () => { cancelled = true; };
  }, []);

  const handleSubjectSelect = (subj: Subject) => {
    setSelectedSubject(subj);
  };

  const handleGallery = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    await processCapture(file);
  };

  const handleShutter = useCallback(async () => {
    if (captureState !== 'IDLE') return;

    void fetch('/api/analytics/event', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        event: 'anon_guest_capture_shoot',
        device_fp: deviceFp,
        subject: selectedSubject,
      }),
    }).catch(() => { /* silently fail */ });

    // In real app: trigger native camera. Here we open file picker as substitute.
    fileInputRef.current?.click();
  }, [captureState, deviceFp, selectedSubject]);

  async function processCapture(file: File) {
    // B 轨：deviceFp 可能因 localStorage key 不对齐暂为 null，用空串 fallback 继续 MSW mock 流程
    const fp = deviceFp ?? '';
    setLastError(null); // 清上次错 · 进新 attempt
    try {
      // 1. Presign upload URL
      setCaptureState('UPLOADING');
      const presignRes = await fetch('/api/file/presign', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ filename: file.name, content_type: file.type }),
      });
      if (!presignRes.ok) {
        setLastError({ code: 'PRESIGN', detail: `HTTP ${presignRes.status}` });
        setCaptureState('ERROR');
        return;
      }
      // BUG-LF-20 fix · BE 返 envelope { code, message, data: { url, image_url } } · 不能直接解构顶层
      const json = (await presignRes.json()) as PresignResp;
      if (json.code !== 0 || !json.data?.url || !json.data?.image_url) {
        setLastError({ code: 'PRESIGN', detail: `BE envelope invalid · code=${json.code}` });
        setCaptureState('ERROR');
        return;
      }
      const { url: uploadUrl, image_url: imageUrl } = json.data;

      // 2. Upload to presigned URL · BUG-LF-20 fix · 加 status check (PUT MinIO 失败时不再静默继续)
      const putRes = await fetch(uploadUrl, { method: 'PUT', body: file });
      if (!putRes.ok) {
        setLastError({ code: 'UPLOAD', detail: `MinIO PUT HTTP ${putRes.status}` });
        setCaptureState('ERROR');
        return;
      }

      // 3. Emit analyze start event
      void fetch('/api/analytics/event', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          event: 'anon_guest_analyze_start',
          device_fp: deviceFp,
          subject: selectedSubject,
        }),
      }).catch(() => { /* silently fail */ });

      // 4. Call guest analyze
      setCaptureState('ANALYZING');
      const analyzeRes = await fetch('/api/guest/analyze', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          device_fp: deviceFp,
          subject: selectedSubject,
          image_url: imageUrl,
        }),
      });

      if (analyzeRes.status === 429) {
        // QUOTA_EXHAUSTED
        setQuotaRemaining(0);
        setCaptureState('QUOTA_EXHAUSTED');
        void fetch('/api/analytics/event', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ event: 'anon_guest_quota_exhausted', device_fp: deviceFp }),
        }).catch(() => { /* silently fail */ });
        return;
      }
      if (!analyzeRes.ok) {
        setLastError({ code: 'ANALYZE', detail: `HTTP ${analyzeRes.status}` });
        setCaptureState('ERROR');
        return;
      }

      const data = await analyzeRes.json() as { guest_session_id: string; task_id: string };

      // C3 Red Line: write guest_session_token only (no user-id)
      safeLocalStorageSet(GUEST_SESSION_KEY, data.guest_session_id);

      void fetch('/api/analytics/event', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          event: 'anon_guest_analyze_done',
          device_fp: deviceFp,
          subject: selectedSubject,
          success: true,
        }),
      }).catch(() => { /* silently fail */ });

      // Navigate to analyzing page
      nav(`/analyzing/${data.task_id}`);
    } catch (e) {
      setLastError({ code: 'NETWORK', detail: e instanceof Error ? e.message : 'fetch reject' });
      setCaptureState('ERROR');
    }
  }

  const handleRegisterCta = (position: 'banner' | 'quota_screen') => {
    void fetch('/api/analytics/event', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        event: 'anon_guest_register_cta',
        device_fp: deviceFp,
        cta_position: position,
      }),
    }).catch(() => { /* silently fail */ });
    nav('/auth');
  };

  const handleBack = () => nav(-1);

  /* QUOTA_EXHAUSTED full-screen block */
  if (captureState === 'QUOTA_EXHAUSTED') {
    return (
      <div className={s.phone} data-testid="guest-capture-page" data-mood="C">
        <div className={s.quotaExhausted} data-testid="quota-exhausted-screen" role="alertdialog" aria-modal="true" aria-label="今日额度已用完">
          <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="#FFD166" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <circle cx="12" cy="12" r="9"/><polyline points="12 7 12 12 15 14"/>
          </svg>
          <h2 className={s.quotaExhaustedTitle}>今日额度已用完</h2>
          <p className={s.quotaExhaustedDesc}>注册免费账号后，每日不限次使用 AI 分析，结果永久保存</p>
          <button
            className={s.quotaExhaustedCta}
            data-testid="quota-exhausted-cta-register"
            onClick={() => handleRegisterCta('quota_screen')}
            aria-label="立即注册，解锁无限次使用"
          >
            立即注册 · 解锁无限次
          </button>
        </div>
        {/* homebar 已删 · iOS chrome */}
      </div>
    );
  }

  return (
    <div
      className={s.phone}
      data-testid="guest-capture-page"
      data-mood="C"
    >
      {/* B1 StatusBar 已删 · 浏览器原生提供时间/信号/电池 · _archive 中是 mockup chrome */}

      {/* B3 Camera viewport (Mood C dark-camera) */}
      <div
        className={s.viewport}
        data-testid="camera-preview"
        role="main"
        aria-label="取景器区域"
      >
        {/* Simulated paper */}
        <div className={s.paper} aria-hidden="true">
          <div className={s.paperQnum}>Q · 3</div>
          <h4 className={s.paperTitle}>二次函数最值</h4>
          <div className={s.paperQ}>设 f(x) = 2x² − 4x + 5，求在 x ∈ [0, 3] 上的最大值。</div>
          <div className={s.paperEq}>f(x) = 2(x − 1)² + 3</div>
          <div className={s.paperQ}>若写作 f(x) = 2x² − 4x + 5，顶点为 (1, 3)，开口向上。</div>
          <div className={s.paperAns}>
            配方：f(x) = 2(x−1)² + 3<br/>
            <span className={s.paperWrong}>最大值 = 3（当 x = 1）</span><br/>
            答：最大值为 3
          </div>
          <div className={s.paperRedMark} aria-hidden="true">✗</div>
        </div>
      </div>

      {/* Edge detection brackets (yellow, Mood C) */}
      <div className={s.edges} aria-hidden="true">
        <span className={`${s.corner} ${s.tl}`} />
        <span className={`${s.corner} ${s.tr}`} />
        <span className={`${s.corner} ${s.bl}`} />
        <span className={`${s.corner} ${s.br}`} />
      </div>

      {/* Anon nav */}
      <nav className={s.anonNav} aria-label="顶部导航">
        <button className={s.back} onClick={handleBack} aria-label="返回上一页">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <polyline points="15 18 9 12 15 6"/>
          </svg>
        </button>
        <div className={s.brandPill} aria-label="游客试用模式">
          <span className={s.brandDot} aria-hidden="true" />
          <span className={s.brandNm}>游客试用 · Guest</span>
        </div>
        <button
          className={s.signinBtn}
          onClick={() => handleRegisterCta('banner')}
          aria-label="登录正式账号"
        >
          登录
        </button>
      </nav>

      {/* B2 · Guest quota banner */}
      <div
        className={s.quotaBanner}
        data-testid="guest-quota-banner"
        role="banner"
        aria-live="polite"
        aria-label="你今天还可以试用 1 次"
      >
        <div className={s.quotaIco} aria-hidden="true">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#FFD166" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <circle cx="12" cy="12" r="9"/><polyline points="12 7 12 12 15 14"/>
          </svg>
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div
            className={s.quotaTitle}
            data-testid="guest-quota-banner-text"
          >
            {quotaRemaining > 0
              ? <>今日还剩 <em>{quotaRemaining} 次</em> 免费分析</>
              : <>今日额度已耗尽 · <em>明天 0 点</em> 重置</>
            }
          </div>
          <div className={s.quotaDesc}>
            结果保留 <em>24 小时</em> · 注册后可一键 claim
          </div>
        </div>
        {/* SC-12: 用 <a role="link"> 而非 button · POM expect role=link with name /注册|不限次/ */}
        <a
          className={s.quotaCta}
          data-testid="guest-quota-banner-cta"
          href="/auth"
          role="link"
          onClick={(e) => { e.preventDefault(); handleRegisterCta('banner'); }}
          aria-label="注册后不限次使用"
        >
          注册后不限次 →
        </a>
      </div>

      {/* B4 · Subject chip row */}
      <nav
        className={s.subjects}
        data-testid="subject-chip-row"
        role="navigation"
        aria-label="学科选择"
      >
        {SUBJECTS.map(({ value, label }) => (
          <button
            key={value}
            className={`${s.subChip}${selectedSubject === value ? ` ${s.active}` : ''}`}
            data-testid={`subject-chip-${value}`}
            onClick={() => handleSubjectSelect(value)}
            aria-pressed={selectedSubject === value}
            aria-label={`选择${label}`}
          >
            {label}
          </button>
        ))}
      </nav>

      {/* B6 · Consent card (compliance gate) */}
      <div
        className={s.consentCard}
        data-testid="guest-consent-card"
        role="group"
        aria-label="隐私同意"
      >
        <div className={s.consentRow}>
          <button
            className={s.consentCheck}
            data-testid="consent-checkbox"
            aria-label="同意隐私条款"
            aria-checked="true"
            role="checkbox"
          >
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <polyline points="20 6 9 17 4 12"/>
            </svg>
          </button>
          <div className={s.consentTx}>
            我已阅读并同意 <a href="/terms/minor">《未成年人保护条款》</a> 与 <a href="/terms/guest">《游客试用协议》</a>。<br/>
            <b>上传的图片仅用于本次 AI 分析</b>，24 小时后自动清理；如您为未成年人，须取得家长同意。
          </div>
        </div>
        <div className={s.consentBadgeRow}>
          <span className={`${s.badge} ${s.badgeGreen}`}>&#10003; 图片端到端加密</span>
          <span className={`${s.badge} ${s.badgeBlue}`}>设备指纹：a4c9…7e21</span>
          <span className={`${s.badge} ${s.badgeOrange}`}>IP 限流：10 次 / 天</span>
        </div>
      </div>

      {/* B5 · Capture controls */}
      <footer
        className={s.controls}
        data-testid="capture-controls"
        role="contentinfo"
        aria-label="拍照控件"
      >
        <div className={s.sources} role="group" aria-label="输入来源">
          {/* Source: 相册 */}
          <button className={s.srcBtn} data-testid="source-album" onClick={handleGallery} aria-label="从相册选择图片">
            <div className={s.srcIco}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <rect x="3" y="3" width="18" height="18" rx="2"/>
                <circle cx="8.5" cy="8.5" r="1.5"/>
                <polyline points="21 15 16 10 5 21"/>
              </svg>
            </div>
            <span className={s.srcLbl}>相册</span>
          </button>
          {/* Source: 相机 */}
          <button className={s.srcBtn} data-testid="source-camera" onClick={handleShutter} aria-label="用相机拍题">
            <div className={s.srcIco}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z"/>
                <circle cx="12" cy="13" r="4"/>
              </svg>
            </div>
            <span className={s.srcLbl}>相机</span>
          </button>
          {/* Source: 文件 */}
          <button
            className={s.srcBtn}
            data-testid="source-file"
            onClick={openFileDialog}
            aria-label="上传文件"
          >
            <div className={s.srcIco}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
              </svg>
            </div>
            <span className={s.srcLbl}>文件</span>
          </button>
        </div>

        <div className={s.shutterRow}>
          {/* Left sidebtn: settings gear */}
          <button
            className={s.sidebtn}
            data-testid="capture-controls-settings"
            aria-label="设置"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/>
              <circle cx="12" cy="12" r="3"/>
            </svg>
          </button>
          {/* Shutter button · 78px (AC-GUEST-005) · white core + ANALYZE badge */}
          <button
            className={s.shutter}
            data-testid="capture-controls-shutter"
            onClick={handleShutter}
            disabled={captureState !== 'IDLE'}
            aria-label="拍题"
            aria-busy={captureState === 'UPLOADING' || captureState === 'ANALYZING'}
          >
            <span className={s.shutterRec} aria-hidden="true">&#9679; Analyze</span>
            <div className={s.shutterCore} aria-hidden="true" />
          </button>
          {/* Right sidebtn: flip camera */}
          <button
            className={s.sidebtn}
            data-testid="capture-controls-flip"
            aria-label="切换相机"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <polyline points="23 4 23 10 17 10"/>
              <polyline points="1 20 1 14 7 14"/>
              <path d="M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15"/>
            </svg>
          </button>
        </div>
      </footer>

      {/* Hidden file input (gallery + camera fallback) */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        capture="environment"
        style={{ display: 'none' }}
        onChange={handleFileChange}
        aria-hidden="true"
      />

      {/* Error overlay · BUG-LF-20 / Phase 1.2 fix · 文案根据 errorCode 切换 + 加 fallback button (按 P02 spec §9 "选图库代替") */}
      {captureState === 'ERROR' && (
        <div className={s.permOverlay} role="alertdialog" aria-modal="true" aria-label="出错了" data-testid="error-overlay" data-error-code={lastError?.code ?? 'UNKNOWN'}>
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#FF5A4F" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
          <h2 className={s.permOverlayTitle} data-testid="error-overlay-title">
            {ERROR_COPY[lastError?.code ?? 'NETWORK'].title}
          </h2>
          <p className={s.permOverlayDesc} data-testid="error-overlay-desc">
            {ERROR_COPY[lastError?.code ?? 'NETWORK'].desc}
          </p>
          {lastError?.detail && (
            <p className={s.permOverlayDetail} data-testid="error-overlay-detail">
              {lastError.detail}
            </p>
          )}
          <div className={s.permOverlayActions}>
            <button
              className={`${s.permOverlayBtn} ${s.permOverlayBtnPrimary}`}
              onClick={() => { setCaptureState('IDLE'); setLastError(null); handleGallery(); }}
              data-testid="error-overlay-fallback-album"
              aria-label="选相册图片代替"
            >
              选相册代替
            </button>
            <button
              className={s.permOverlayBtn}
              onClick={() => { setCaptureState('IDLE'); setLastError(null); openFileDialog(); }}
              data-testid="error-overlay-fallback-file"
              aria-label="选文件代替"
            >
              选文件代替
            </button>
            <button
              className={s.permOverlayBtn}
              onClick={() => { setCaptureState('IDLE'); setLastError(null); }}
              data-testid="error-overlay-retry"
              aria-label="重试"
            >
              重试
            </button>
          </div>
        </div>
      )}

      {/* homebar 已删 · iOS chrome · _archive data-mockup-chrome="iphone-homebar" */}
    </div>
  );
};

export default GuestCapturePage;
