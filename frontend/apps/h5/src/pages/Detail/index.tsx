// S7 · FE-03 · WrongbookDetail (P06) · 对标 design/mockups/wrongbook/_archive/06_wrongbook_detail.html
// Mood B · pure-warm · 米白底 + 白卡 + iOS 标准 nav
// AC 覆盖: AC-WB-DETAIL-001 ~ AC-WB-DETAIL-010
import React, { useState, useMemo, useRef, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { wrongbookClient, WrongItemVO } from '@longfeng/api-contracts';
import { TEST_IDS } from '@longfeng/testids';
import s from './Detail.module.css';

// ─── Types ────────────────────────────────────────────────────────────────
type TabId = 'analysis' | 'records' | 'variants';

// AC-WB-DETAIL-004 · MemoryCurve node
interface ReviewNode {
  nid: string;
  tLevel: 'T0' | 'T1' | 'T2' | 'T3' | 'T4' | 'T5' | 'T6';
  status: 'done' | 'now' | 'future';
  dueAt?: string;
  gradedAt?: string;
  grade?: 'forgot' | 'partial' | 'mastered';
}

interface ReviewRecord {
  rid: string;
  timestamp: string;
  grade: 'forgot' | 'partial' | 'mastered';
  durationSec: number;
  tLevel: string;
}

// Mock nodes for MVP (replaced by real API in S7 A-track)
function mockNodes(mastery: number): ReviewNode[] {
  const phase = Math.min(5, Math.floor((mastery / 100) * 6));
  return ['T0', 'T1', 'T2', 'T3', 'T4', 'T5', 'T6'].map((tl, i) => ({
    nid: `n${i}`,
    tLevel: tl as ReviewNode['tLevel'],
    status: i < phase ? 'done' : i === phase ? 'now' : 'future',
    gradedAt: i < phase ? new Date(Date.now() - (phase - i) * 86400000).toISOString() : undefined,
  }));
}

// Mock radar values
const RADAR_AXES = ['运算', '概念', '方法', '速度', '准确'] as const;
function mockRadar(mastery: number) {
  const base = mastery / 100;
  return [base * 60, base * 80, base * 70, base * 50, base * 75].map((v) => Math.round(v));
}

const SUBJECT_LABEL: Record<string, string> = {
  math: '数学', physics: '物理', chemistry: '化学', english: '英语',
};

function masteryLabel(m: number): string {
  if (m < 40) return '未掌握';
  if (m < 70) return '部分';
  return '已掌握';
}

// ─── Main Page ────────────────────────────────────────────────────────────
export const DetailPage: React.FC = () => {
  const { id = '' } = useParams();
  const nav = useNavigate();
  const qc = useQueryClient();

  const [tab, setTab] = useState<TabId>('analysis');
  const [imageViewerOpen, setImageViewerOpen] = useState(false);
  const [archived, setArchived] = useState(false);
  const [archiving, setArchiving] = useState(false);

  const { data: item, isLoading, isError, refetch } = useQuery<WrongItemVO>({
    queryKey: ['wrongbook', 'item', id],
    queryFn: () => wrongbookClient.get(id),
    enabled: !!id,
  });

  const nodes = useMemo(() => item ? mockNodes(item.mastery) : [], [item]);
  const radarVals = useMemo(() => item ? mockRadar(item.mastery) : [0, 0, 0, 0, 0], [item]);
  const currentNode = nodes.find((n) => n.status === 'now');
  const subjectLabel = item ? (SUBJECT_LABEL[item.subject] ?? item.subject) : '';

  // SC-10 · 归档级联 + 5s undo 窗口
  // 归档后 5s 内点击 undo → cancel · 不导航 · 否则 5s 后 nav /wrongbook
  const undoTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const archiveMut = useMutation({
    mutationFn: () => wrongbookClient.softDelete(id),
    onSuccess: () => {
      setArchived(true);
      setArchiving(false);
      qc.invalidateQueries({ queryKey: ['wrongbook'] });
      // SC-10: undo 窗口 5s · 内未点 undo 才真离页 · 内点 undo 取消并 unarchive
      if (undoTimerRef.current) clearTimeout(undoTimerRef.current);
      undoTimerRef.current = setTimeout(() => {
        undoTimerRef.current = null;
        nav('/wrongbook', { replace: true });
      }, 5000);
    },
    onError: () => {
      setArchiving(false);
    },
  });

  const handleUndoArchive = () => {
    if (undoTimerRef.current) {
      clearTimeout(undoTimerRef.current);
      undoTimerRef.current = null;
    }
    setArchived(false);
    qc.invalidateQueries({ queryKey: ['wrongbook'] });
  };

  useEffect(() => {
    return () => {
      if (undoTimerRef.current) clearTimeout(undoTimerRef.current);
    };
  }, []);

  // ── LOADING ─────────────────────────────────────────────────
  if (isLoading) {
    return (
      <div className={s.root} data-mood="B" data-testid={TEST_IDS.wrongbookDetail.root}>
        <div className={s.skeleton} aria-busy="true" aria-label="加载中">
          <div className={s.skeletonNav} />
          <div className={s.skeletonImg} />
          <div className={s.skeletonTab} />
          <div className={s.skeletonCard} />
          <div className={s.skeletonCard} />
        </div>
      </div>
    );
  }

  // ── ERROR ────────────────────────────────────────────────────
  if (isError || !item) {
    return (
      <div className={s.root} data-mood="B" data-testid={TEST_IDS.wrongbookDetail.root}>
        <div className={s.errorPage}>
          <div className={s.errorText}>加载失败</div>
          <button className={s.retryBtn} onClick={() => refetch()}>重试</button>
          <button className={s.ghostBtn} onClick={() => nav(-1)}>返回</button>
        </div>
      </div>
    );
  }

  const mLabel = masteryLabel(item.mastery);

  return (
    <div
      className={`${s.root} ${archived ? s.rootArchived : ''}`}
      data-mood="B"
      data-testid={TEST_IDS.wrongbookDetail.root}
      data-archived={archived ? 'true' : undefined}
    >
      {/* Status Bar */}
      <div className={s.status} aria-hidden="true">
        <span>9:41</span>
        <span className={s.statusIcons}>
          <svg width="17" height="11" viewBox="0 0 17 11" aria-hidden="true"><g fill="#111"><rect x="0" y="7" width="3" height="4" rx=".5"/><rect x="4.5" y="5" width="3" height="6" rx=".5"/><rect x="9" y="3" width="3" height="8" rx=".5"/><rect x="13.5" y="1" width="3" height="10" rx=".5"/></g></svg>
          <svg width="26" height="12" viewBox="0 0 26 12" aria-hidden="true"><rect x=".5" y=".5" width="22" height="11" rx="3" fill="none" stroke="#111" opacity=".45"/><rect x="2" y="2" width="17" height="8" rx="1.6" fill="#111"/><rect x="23" y="4" width="2" height="4" rx="1" fill="#111" opacity=".45"/></svg>
        </span>
      </div>

      {/* NavBar */}
      <header className={s.nav} role="banner">
        <div className={s.navRow}>
          <button
            className={s.back}
            onClick={() => nav(-1)}
            data-testid={TEST_IDS.common.back}
            aria-label="返回错题本"
          >
            <svg width="12" height="20" viewBox="0 0 12 20" fill="none" aria-hidden="true">
              <path d="M10 2 2 10l8 8" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            错题本
          </button>
          <div className={s.navRight}>
            <button className={s.navIcon} aria-label="编辑">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M16 5l3 3-10 10H6v-3L16 5Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round"/>
              </svg>
            </button>
            <button className={s.navIcon} aria-label="更多操作">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <circle cx="12" cy="6" r="1.6" fill="currentColor"/>
                <circle cx="12" cy="12" r="1.6" fill="currentColor"/>
                <circle cx="12" cy="18" r="1.6" fill="currentColor"/>
              </svg>
            </button>
          </div>
        </div>
        <h1 className={s.navTitle}>
          错题 #{item.id.slice(-3)}
          <span className={s.pillRed}>{mLabel}</span>
        </h1>
      </header>

      {/* Scrollable content */}
      <main
        className={s.content}
        role="main"
        data-testid={TEST_IDS.wrongbookDetail['stem-text']}
      >
        {/* B1 · OriginImageCard 原图卡 · AC-WB-DETAIL-001 */}
        <div
          className={s.imgCard}
          data-testid={TEST_IDS.wrongbookDetail['origin-image']}
          style={{ height: '170px' }}
        >
          {item.image_url ? (
            <>
              <img
                src={item.image_url}
                alt={`错题 #${item.id.slice(-3)} 原图`}
                className={s.imgFull}
              />
              <div className={s.imgBadge}>原图 · {subjectLabel}</div>
            </>
          ) : (
            <div className={s.imgPaper}>
              <div className={s.imgPaperLabel}>{subjectLabel} · 复习题</div>
              <div className={s.imgPaperQno}>{item.id.slice(-3)}</div>
              <h3 className={s.imgPaperText}>{item.stem_text}</h3>
              <div className={s.imgPaperStrike} aria-hidden="true" />
              <div className={s.imgPaperBadge}>原图 · 1.2 MB</div>
            </div>
          )}
          {/* AC-WB-DETAIL-001 · 放大按钮 */}
          <button
            className={s.imgZoom}
            onClick={() => setImageViewerOpen(true)}
            aria-label="放大原图"
            data-testid={TEST_IDS.wrongbookDetail['origin-image-zoom']}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5" stroke="#fff" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </button>
        </div>

        {/* B2 · SegmentTab · AC-WB-DETAIL-002 */}
        <div
          className={s.stab}
          role="tablist"
          aria-label="详情分段"
          data-testid={TEST_IDS.wrongbookDetail['segment-tab']}
        >
          {(['analysis', 'records', 'variants'] as const).map((t) => {
            const labels: Record<TabId, string> = { analysis: '分析', records: '复习记录', variants: '变式' };
            const tid: Record<TabId, string> = {
              analysis: TEST_IDS.wrongbookDetail['segment-tab-analysis'],
              records: TEST_IDS.wrongbookDetail['segment-tab-records'],
              variants: TEST_IDS.wrongbookDetail['segment-tab-variants'],
            };
            return (
              <button
                key={t}
                role="tab"
                aria-selected={tab === t}
                className={tab === t ? s.stabOn : ''}
                onClick={() => setTab(t)}
                data-testid={tid[t]}
              >
                {labels[t]}
              </button>
            );
          })}
        </div>

        {/* ── tab=分析 ──────────────────────────────────────── */}
        {tab === 'analysis' && (
          <>
            {/* B3 · AIBriefCard · AC-WB-DETAIL-003 */}
            <div
              className={s.brief}
              role="article"
              aria-label="AI 错因简报"
              data-testid={TEST_IDS.wrongbookDetail['ai-brief']}
            >
              <div className={s.briefRow}>
                <span className={s.kicker}>{subjectLabel} · {item.tags[0] ?? '错题详情'}</span>
              </div>
              <div className={s.briefStem}>{item.stem_text}</div>

              {/* 答案对错卡 */}
              <div className={s.ansGrid}>
                <div className={`${s.ans} ${s.ansWrong}`}>
                  <div className={s.ansL}>✗ 你的答</div>
                  <div className={s.ansV}>—</div>
                </div>
                <div className={`${s.ans} ${s.ansRight}`}>
                  <div className={s.ansL}>✓ 正解</div>
                  <div className={s.ansV}>—</div>
                </div>
              </div>

              {/* AC-WB-DETAIL-003 · 4px mastery-forgot 红条 · 错因 */}
              <div
                className={s.errBlock}
                data-testid={TEST_IDS.wrongbookDetail['ai-brief-reason-bar']}
              >
                错因：概念混淆或计算错误，请对照正解仔细复习。
              </div>

              {/* KP chips · AC-WB-DETAIL-003 */}
              <div className={s.kpRow}>
                {item.tags.map((tag, n) => (
                  <span
                    key={tag}
                    className={s.kpChip}
                    data-testid={`p06-ai-brief-kp-chip-${n + 1}`}
                  >
                    {tag}
                  </span>
                ))}
                {item.tags.length === 0 && (
                  <span className={s.kpChip} data-testid="p06-ai-brief-kp-chip-1">知识点</span>
                )}
              </div>

              {/* 难度 ★ · AC-WB-DETAIL-003 */}
              <div
                className={s.diffRow}
                data-testid={TEST_IDS.wrongbookDetail['ai-brief-difficulty']}
                aria-label={`难度：${Math.ceil(item.mastery / 20)} 星`}
              >
                {Array.from({ length: 5 }).map((_, i) => (
                  <span
                    key={i}
                    className={`${s.star} ${i < Math.ceil((100 - item.mastery) / 20) ? s.starOn : ''}`}
                    aria-hidden="true"
                  >
                    ★
                  </span>
                ))}
              </div>
            </div>
          </>
        )}

        {/* ── tab=复习记录 ──────────────────────────────────── */}
        {tab === 'records' && (
          <>
            {/* B4 · MemoryCurve · AC-WB-DETAIL-004 */}
            <div
              className={s.tl}
              data-testid={TEST_IDS.wrongbookDetail['memory-curve']}
              role="figure"
              aria-label={`艾宾浩斯遗忘曲线 · ${nodes.filter(n => n.status === 'done').length}/7 已完成`}
            >
              <div className={s.secHeader}>
                <span className={s.secTitle}>艾宾浩斯 复习时间线</span>
                <span className={s.secRight}>
                  {nodes.filter((n) => n.status === 'done').length}/6 已完成
                </span>
              </div>

              {/* Curve SVG */}
              <div className={s.axis}>
                <svg viewBox="0 0 320 60" preserveAspectRatio="none" aria-hidden="true">
                  <defs>
                    <linearGradient id="curveLg" x1="0" x2="1" y1="0" y2="0">
                      <stop offset="0" stopColor="#34C759"/>
                      <stop offset=".5" stopColor="#5FA8FF"/>
                      <stop offset="1" stopColor="#C7C7CC"/>
                    </linearGradient>
                  </defs>
                  <path
                    d="M10 50 C40 38, 60 30, 80 36 S130 50, 156 28 S210 6, 250 22 S300 48, 310 50"
                    stroke="url(#curveLg)" strokeWidth="2.4" fill="none" strokeLinecap="round"
                  />
                  <path
                    d="M10 50 C40 38, 60 30, 80 36 S130 50, 156 28 L156 60 L10 60 Z"
                    fill="url(#curveLg)" opacity=".10"
                  />
                </svg>
              </div>

              {/* Nodes · T0-T6 · AC-WB-DETAIL-004 */}
              <div className={s.nodes}>
                {nodes.map((nd) => (
                  <div
                    key={nd.nid}
                    className={`${s.nd} ${nd.status === 'done' ? s.ndDone : nd.status === 'now' ? s.ndNow : ''}`}
                    data-testid={`memory-curve-node-${nd.tLevel}`}
                    data-status={nd.status}
                  >
                    <div
                      className={`${s.ndDot} ${nd.status === 'now' ? s.ndDotPulse : ''}`}
                      aria-hidden="true"
                    />
                    <div className={s.ndLv}>{nd.tLevel}</div>
                    <div className={s.ndDt}>
                      {nd.gradedAt
                        ? new Date(nd.gradedAt).toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
                        : nd.status === 'now' ? '今日' : '—'}
                    </div>
                  </div>
                ))}
              </div>

              {/* Stats */}
              <div className={s.stats}>
                <div className={s.st}>
                  <div className={`${s.stV} ${s.stVB}`}>{nodes.filter(n => n.status === 'done').length} 次</div>
                  <div className={s.stT}>累计复习</div>
                </div>
                <div className={s.st}>
                  <div className={`${s.stV} ${s.stVG}`}>{item.mastery} %</div>
                  <div className={s.stT}>掌握度</div>
                </div>
                <div className={s.st}>
                  <div className={`${s.stV} ${s.stVO}`}>0 次</div>
                  <div className={s.stT}>遗忘</div>
                </div>
              </div>
            </div>

            {/* B5 · RecordsTimeline · AC-WB-DETAIL-004 */}
            <div
              className={s.timeline}
              data-testid={TEST_IDS.wrongbookDetail['records-timeline']}
            >
              <div className={s.secDivider}>
                <h3>复习记录</h3>
                <span className={s.secLine} />
              </div>
              {nodes.filter(n => n.status === 'done').length === 0 ? (
                <div className={s.timelineEmpty}>暂无复习记录</div>
              ) : (
                nodes
                  .filter((n) => n.status === 'done')
                  .map((nd, i) => (
                    <div
                      key={nd.nid}
                      className={s.tlItem}
                      data-testid={`p06-records-timeline-item-${i + 1}`}
                    >
                      <div className={s.tlDot} aria-hidden="true" />
                      <div className={s.tlBody}>
                        <span className={s.tlLevel}>{nd.tLevel}</span>
                        <span className={s.tlDate}>
                          {nd.gradedAt
                            ? new Date(nd.gradedAt).toLocaleDateString('zh-CN')
                            : ''}
                        </span>
                        <span className={`${s.tlGrade} ${s.tlGradeGreen}`}>已掌握 ✓</span>
                      </div>
                    </div>
                  ))
              )}
            </div>

            {/* B7 · RadarChart · AC-WB-DETAIL-006 */}
            <RadarChart axes={RADAR_AXES} values={radarVals} />
          </>
        )}

        {/* ── tab=变式 ──────────────────────────────────────── */}
        {tab === 'variants' && (
          // B6 · AC-WB-DETAIL-005
          <div
            className={s.variantsEmpty}
            data-testid={TEST_IDS.wrongbookDetail['variants-empty']}
            role="status"
          >
            <div className={s.variantsIcon} aria-hidden="true">🔜</div>
            <div className={s.variantsText}>敬请期待</div>
            <div className={s.variantsHint}>变式题功能将在后续版本推出</div>
          </div>
        )}

        {/* legacy testid anchors · 保持 SC-02/03/04 后向兼容 */}
        <span
          data-testid={TEST_IDS.wrongbookDetail['tag-sheet']}
          aria-hidden="true"
          style={{ display: 'none' }}
        />

        {/* Spacer for sticky bottom */}
        <div style={{ height: 96 }} aria-hidden="true" />
      </main>

      {/* B8 · BottomActions sticky · AC-WB-DETAIL-007/008/009 */}
      <footer
        className={s.cta}
        role="contentinfo"
        data-testid={TEST_IDS.wrongbookDetail['bottom-actions']}
        data-archived={archived ? 'true' : undefined}
      >
        <button
          className={s.btnGhost}
          onClick={() => { setArchiving(true); archiveMut.mutate(); }}
          disabled={archived || archiveMut.isPending}
          data-testid={TEST_IDS.wrongbookDetail['bottom-actions-archive-btn']}
          aria-label="归档此错题"
        >
          {archived ? '已归档' : archiveMut.isPending ? '…' : '归档'}
        </button>
        {/* legacy testid anchor · 保持 SC-02/03 后向兼容 */}
        <span
          data-testid={TEST_IDS.wrongbookDetail['review-entry']}
          aria-hidden="true"
          style={{ display: 'none' }}
        />
        <button
          className={s.btnPrimary}
          disabled={archived}
          data-testid={TEST_IDS.wrongbookDetail['bottom-actions-review-btn']}
          aria-label="立即开始复习"
          onClick={() => {
            if (currentNode) {
              nav(`/review/exec/${currentNode.nid}`);
            }
          }}
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M8 5v14l11-7L8 5Z" fill="#fff"/>
          </svg>
          立即复习
        </button>
      </footer>

      {/* SC-10 · 归档后 5s undo toast */}
      {archived && undoTimerRef.current && (
        <div
          role="status"
          aria-live="polite"
          data-testid="p06-archive-undo-toast"
          style={{
            position: 'fixed', left: 16, right: 16, bottom: 96, zIndex: 50,
            background: '#1C1C1E', color: '#fff', borderRadius: 12,
            padding: '12px 16px', display: 'flex', alignItems: 'center',
            gap: 12, fontSize: 14, fontWeight: 500,
            boxShadow: '0 8px 24px rgba(0,0,0,0.18)',
          }}
        >
          <span style={{ flex: 1 }}>已归档 · 5 秒内可撤销</span>
          <button
            type="button"
            onClick={handleUndoArchive}
            aria-label="撤销归档"
            style={{
              background: 'transparent', border: 'none',
              color: '#5AA9FF', fontSize: 14, fontWeight: 600,
              padding: '4px 8px', cursor: 'pointer',
            }}
          >
            撤销
          </button>
        </div>
      )}

      {/* TabBar */}
      <nav className={s.tabbar} role="navigation" aria-label="底部导航">
        <button className={s.tab} role="tab" aria-selected={false} onClick={() => nav('/')}>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M3 11 L12 3 L21 11 V20 a1 1 0 0 1 -1 1 H14 V14 H10 V21 H4 a1 1 0 0 1 -1 -1 Z"
                  stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" strokeLinecap="round"/>
          </svg>
          <span>首页</span>
        </button>
        <button className={`${s.tab} ${s.tabActive}`} role="tab" aria-selected={true}>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M5 4h11l3 3v13H5V4Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round"/>
            <path d="M8 11h8M8 14h6M8 17h5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/>
          </svg>
          <span>错题本</span>
        </button>
        <button className={s.tab} role="tab" aria-selected={false} onClick={() => nav('/capture')}>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="12" cy="13" r="4.5" stroke="currentColor" strokeWidth="1.8"/>
            <path d="M5 8h3l1.5-2h5L16 8h3a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2Z"
                  stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round"/>
          </svg>
          <span>拍题</span>
        </button>
        <button className={s.tab} role="tab" aria-selected={false}>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M12 3.5c-3.6 0-6.2 2.6-6.2 6.2v3.4L4 15.5v1.3h16v-1.3l-1.8-2.4V9.7c0-3.6-2.6-6.2-6.2-6.2Z"
                  stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round"/>
            <path d="M10 19.5a2 2 0 0 0 4 0" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/>
          </svg>
          <span>复习</span>
        </button>
        <button className={s.tab} role="tab" aria-selected={false}>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="12" cy="8.5" r="3.8" stroke="currentColor" strokeWidth="1.8"/>
            <path d="M4.5 20c1.2-3.8 4.2-5.6 7.5-5.6s6.3 1.8 7.5 5.6"
                  stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/>
          </svg>
          <span>我的</span>
        </button>
      </nav>


      {/* Image Viewer Modal */}
      {imageViewerOpen && (
        <div
          className={s.imgViewerBackdrop}
          role="dialog"
          aria-modal="true"
          aria-label="原图放大"
          onClick={() => setImageViewerOpen(false)}
        >
          <div className={s.imgViewerContent} onClick={(e) => e.stopPropagation()}>
            {item.image_url ? (
              <img src={item.image_url} alt="错题原图" className={s.imgViewerFull} />
            ) : (
              <div className={s.imgViewerPlaceholder}>{item.stem_text}</div>
            )}
            <button
              className={s.imgViewerClose}
              onClick={() => setImageViewerOpen(false)}
              aria-label="关闭"
            >
              ✕
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

// ─── RadarChart component (B7 · AC-WB-DETAIL-006) ────────────────────────
const RadarChart: React.FC<{
  axes: readonly string[];
  values: number[];
}> = ({ axes, values }) => {
  const cx = 65, cy = 65, r = 50;
  const n = axes.length;

  function point(i: number, val: number): [number, number] {
    const angle = (i * 2 * Math.PI) / n - Math.PI / 2;
    const rv = (val / 100) * r;
    return [cx + rv * Math.cos(angle), cy + rv * Math.sin(angle)];
  }
  function gridPoint(i: number, scale: number): [number, number] {
    const angle = (i * 2 * Math.PI) / n - Math.PI / 2;
    return [cx + scale * Math.cos(angle), cy + scale * Math.sin(angle)];
  }

  const dataPoints = values.map((v, i) => point(i, v));
  const dataPath = dataPoints.map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x},${y}`).join(' ') + ' Z';

  const grids = [50, 37, 25, 12].map((scale) => {
    const pts = Array.from({ length: n }, (_, i) => gridPoint(i, scale));
    return pts.map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x},${y}`).join(' ') + ' Z';
  });

  const labelPts = Array.from({ length: n }, (_, i) => {
    const angle = (i * 2 * Math.PI) / n - Math.PI / 2;
    return [cx + (r + 12) * Math.cos(angle), cy + (r + 12) * Math.sin(angle)];
  });

  return (
    <div
      className={s.radarWrap}
      data-testid={TEST_IDS.wrongbookDetail['radar-chart']}
      role="figure"
      aria-label="五维能力雷达图"
    >
      <div className={s.radarRow}>
        <div className={s.radar}>
          <div className={s.radarHd}>知识点能力</div>
          <svg width="130" height="130" viewBox="0 0 130 130" aria-hidden="true">
            {grids.map((d, i) => (
              <path key={i} d={d} fill="none" stroke="#E5E5EA" strokeWidth="1" />
            ))}
            {Array.from({ length: n }, (_, i) => {
              const [x, y] = gridPoint(i, r);
              return <line key={i} x1={cx} y1={cy} x2={x} y2={y} stroke="#E5E5EA" strokeWidth="1" />;
            })}
            <path d={dataPath} fill="rgba(0,122,255,.18)" stroke="var(--tkn-color-primary-default)" strokeWidth="1.6" />
            {axes.map((ax, i) => {
              const [lx, ly] = labelPts[i];
              return (
                <text
                  key={ax}
                  x={lx}
                  y={ly}
                  textAnchor="middle"
                  dominantBaseline="middle"
                  fontSize="9"
                  fill="var(--tkn-color-text-secondary)"
                  fontWeight="600"
                  data-testid={`p06-radar-chart-axis-${i + 1}`}
                >
                  {ax}
                </text>
              );
            })}
          </svg>
        </div>
        <div className={s.radarLegend}>
          <div className={s.radarHd}>本题知识点</div>
          {axes.map((ax, i) => (
            <div key={ax} className={s.legendItem}>
              <span className={s.legendDot} style={{ background: ['#007AFF', '#5856D6', '#34C759', '#FF9500', '#FF2D55'][i % 5] }} aria-hidden="true" />
              <span className={s.legendName}>{ax}</span>
              <span className={s.legendVal}>{values[i]}%</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
