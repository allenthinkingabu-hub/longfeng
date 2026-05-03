// P11 · 事件详情三形态 · EventDetail
// Archive: design/mockups/wrongbook/_archive/11_event_detail.html
// Mood B · pure-warm · 三形态同壳 (study | general/exam/family | shared)
// AC 覆盖: AC-P11-001 ~ AC-P11-010
// testid 根: p11-*

import React, { useState, useEffect } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import s from './EventDetail.module.css';

// ─── Types ────────────────────────────────────────────────────────────────────
type RelationType = 'STUDY' | 'EXAM' | 'FAMILY';
type SubjectType = 'math' | 'physics' | 'chemistry' | 'english';
type TLevelType = 'T0' | 'T1' | 'T2' | 'T3' | 'T4' | 'T5' | 'T6';

interface MemoryNode {
  tLevel: TLevelType;
  status: 'done' | 'now' | 'future';
  dueAt: string;
}

interface EventDetailResp {
  eventId: string;
  relationType: RelationType;
  title: string;
  startAt: string;
  endAt?: string;
  durationMin?: number;
  description?: string;
  reminder?: { offsetMin: number; channel: 'PUSH' | 'IN_APP' };
  recurrence?: { rule: string; count?: number };
  source: 'SELF' | 'AI' | 'PARENT' | 'TEACHER';
  fromUser?: { name: string; role: 'PARENT' | 'TEACHER' | 'SELF' };

  // STUDY
  study?: {
    subject: SubjectType;
    questionId: string;
    questionStem: string;
    thumbnailUrl: string;
    nodeId: string;
    tLevel: TLevelType;
    nodes: MemoryNode[];
  };

  // EXAM
  exam?: {
    subject: SubjectType;
    location: string;
    countdownDays: number;
  };

  // FAMILY
  family?: {
    participants: string[];
    note: string;
  };
}

// Variant type for three-shell pattern
type EventVariant = 'study' | 'general' | 'shared';

// ─── Helpers ──────────────────────────────────────────────────────────────────
const SUBJECT_LABEL: Record<SubjectType, string> = {
  math: '数学',
  physics: '物理',
  chemistry: '化学',
  english: '英语',
};

const SUBJECT_COLOR: Record<SubjectType, string> = {
  math: 'var(--tkn-subject-math, #C41E3A)',
  physics: 'var(--tkn-subject-physics, #0057B7)',
  chemistry: 'var(--tkn-subject-chemistry, #1A6B3A)',
  english: 'var(--tkn-subject-english, #9C4F00)',
};

function ribbonColor(relationType: RelationType, subject?: SubjectType): string {
  if (relationType === 'EXAM') return '#FF3B30';
  if (relationType === 'FAMILY') return '#FF9500';
  if (subject) return SUBJECT_COLOR[subject];
  return '#5856D6';
}

function backLabel(from: string | null): string {
  if (from === 'CAL') return '4月';
  if (from === 'HOME') return '首页';
  if (from === 'NOTIF') return '通知';
  return '返回';
}

// API calls
async function fetchEventDetail(
  eventId: string,
  shareToken?: string,
): Promise<EventDetailResp> {
  const path = shareToken
    ? `/api/s/${shareToken}/events/${eventId}`
    : `/api/calendar/events/${eventId}`;
  const res = await fetch(path);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json() as Promise<EventDetailResp>;
}

async function postAck(eventId: string): Promise<void> {
  await fetch(`/api/events/${eventId}/ack`, { method: 'PATCH' });
}

// ─── Main Page ────────────────────────────────────────────────────────────────
export const EventDetailPage: React.FC = () => {
  const { eventId = '' } = useParams<{ eventId: string }>();
  const [searchParams] = useSearchParams();
  const nav = useNavigate();

  const from = searchParams.get('from');
  const shareToken = searchParams.get('shareToken') ?? undefined;

  // Determine variant from URL / share token
  const variant: EventVariant = shareToken ? 'shared' : 'general';

  const { data, isLoading, isError, refetch } = useQuery<EventDetailResp>({
    queryKey: ['event', eventId, shareToken],
    queryFn: () => fetchEventDetail(eventId, shareToken),
    enabled: !!eventId,
  });

  // AC-P11-010 · EXAM form → auto-ack on mount
  const ackMut = useMutation({ mutationFn: () => postAck(eventId) });

  useEffect(() => {
    if (data?.relationType === 'EXAM') {
      ackMut.mutate();
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data?.relationType]);

  // ── LOADING ─────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <div className={s.root} data-mood="B">
        <TopBar
          backLabel={backLabel(from)}
          onBack={() => nav(-1)}
          title="事件详情"
          showEdit={false}
          data-testid="p11-top-bar"
        />
        <div className={s.skeleton} aria-busy="true" aria-label="加载中">
          <div className={s.skeletonBar} />
          <div className={s.skeletonHero} />
          <div className={s.skeletonCard} />
          <div className={s.skeletonCard} />
        </div>
      </div>
    );
  }

  // ── ERROR / NOT FOUND ────────────────────────────────────────────
  if (isError || !data) {
    return (
      <div className={s.root} data-mood="B">
        <TopBar
          backLabel={backLabel(from)}
          onBack={() => nav(-1)}
          title="事件详情"
          showEdit={false}
        />
        <div className={s.errorPage} role="alert">
          <div className={s.errorText}>事件不存在或已删除</div>
          <button className={s.retryBtn} onClick={() => refetch()}>重试</button>
          <button className={s.ghostBtn} onClick={() => nav(-1)}>返回</button>
        </div>
      </div>
    );
  }

  const relType = data.relationType;
  const isStudy = relType === 'STUDY';
  const isExam = relType === 'EXAM';
  const isFamily = relType === 'FAMILY';
  const isShared = variant === 'shared';

  // Determine heroCard variant class
  let heroCardClass = s.heroCard;
  if (isExam) heroCardClass = `${s.heroCard} ${s.heroCardExam}`;
  else if (isFamily) heroCardClass = `${s.heroCard} ${s.heroCardFamily}`;

  const nodeCancelledStudy =
    isStudy &&
    data.study &&
    !data.study.nodes.some((n) => n.status === 'now' || n.status === 'future');

  const startDate = new Date(data.startAt);
  const startFormatted = startDate.toLocaleString('zh-CN', {
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });

  return (
    <div className={s.root} data-mood="B">
      {/* ── B0 · TopBar ─────────────────────────────────── */}
      <TopBar
        backLabel={backLabel(from)}
        onBack={() => nav(-1)}
        title="事件详情"
        showEdit={isFamily && !isShared}
        onEdit={() => {/* FAMILY edit bottom-sheet */}}
        data-testid="p11-top-bar"
      />

      {/* ── B1 · MorphRibbon ────────────────────────────── */}
      <div
        className={s.morphRibbon}
        data-testid="p11-morph-ribbon"
        data-relation-type={relType}
        style={{
          background: ribbonColor(relType, data.study?.subject ?? data.exam?.subject),
        }}
        aria-hidden="true"
      />

      {/* ── Scroll area ─────────────────────────────────── */}
      <div className={s.scroll} role="main">
        {/* Shared mode banner (C5 desensitized) */}
        {isShared && (
          <div className={s.sharedBanner} role="status">
            <span className={s.sharedBannerIcon}>🔗</span>
            <span>分享只读视图 · 个人信息已脱敏</span>
          </div>
        )}

        {/* ── B2 · EventHeroCard ───────────────────────── */}
        <section
          className={heroCardClass}
          data-testid="p11-event-hero-card"
          aria-label={`${data.title} · ${relType === 'STUDY' ? '复习节点' : relType === 'EXAM' ? '考试' : '家庭事件'}`}
        >
          {/* Type badge */}
          <div className={s.eventBadge}>
            <span
              className={
                isExam ? `${s.badgeDot} ${s.badgeDotExam}` :
                isFamily ? `${s.badgeDot} ${s.badgeDotFamily}` :
                s.badgeDot
              }
            >
              {isStudy && data.study ? data.study.tLevel : isExam ? '📅' : '🏠'}
            </span>
            <span data-testid="p11-event-hero-card-badge">
              {isStudy ? '复习节点' : isExam ? '考试' : '家庭'}
            </span>
            {isStudy && data.study && (
              <>
                <span style={{ width: 1, height: 10, background: 'rgba(255,255,255,0.25)', margin: '0 2px' }} />
                <span style={{ color: '#E8E6FF' }}>
                  艾宾浩斯曲线 · 第{data.study.nodes.filter((n) => n.status === 'done').length + 1}次回顾
                </span>
              </>
            )}
          </div>

          {/* Title */}
          <h1 className={s.heroTitle}>
            {data.title}
            {data.description && (
              <span className={s.heroSubtitle}>{data.description}</span>
            )}
          </h1>

          {/* Countdown / time info */}
          <div className={s.countRow}>
            <div>
              <div className={s.countLabel}>开始时间</div>
              <div className={s.countVal} style={{ fontSize: 16 }}>{startFormatted}</div>
            </div>
            {data.durationMin && (
              <div style={{ textAlign: 'right' }}>
                <div className={s.countLabel}>预计用时</div>
                <div className={s.countVal}>
                  {data.durationMin}
                  <span className={s.countUnit}>分钟</span>
                </div>
              </div>
            )}
          </div>

          {/* Meta row */}
          <div className={s.heroMeta}>
            {data.fromUser && !isShared && (
              <span className={s.heroMetaItem}>来源：{data.fromUser.name}</span>
            )}
            {data.reminder && (
              <span className={s.heroMetaItem}>提醒已开</span>
            )}
          </div>
        </section>

        {/* ── B3 · Variant-specific area ────────────────── */}

        {/* B3a · STUDY form */}
        {isStudy && data.study && !nodeCancelledStudy && (
          <div data-testid="p11-related-study">
            {/* Question thumb */}
            <div className={s.thumbCard} data-testid="p11-related-study-question">
              <div className={s.thumbImg} aria-label="错题原图缩略">
                {/* Paper placeholder */}
                <svg viewBox="0 0 90 90" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <rect width="90" height="90" fill="#F8F9FE"/>
                  <g fill="#3C3C43" opacity=".55" fontFamily="Georgia,serif" fontSize="8">
                    <text x="8" y="20">{data.study.questionStem.slice(0, 18)}</text>
                    <text x="8" y="36">{data.study.questionStem.slice(18, 36)}</text>
                    <text x="8" y="52">{data.study.questionStem.slice(36, 54)}</text>
                  </g>
                  <path d="M8 70 L36 70" stroke="#FF3B30" strokeWidth="1.6" strokeLinecap="round"/>
                  <rect x="0" y="0" width="90" height="90" fill="rgba(255,255,255,.35)"/>
                </svg>
              </div>

              <div className={s.thumbInfo}>
                <div>
                  <div className={s.thumbQuestion}>
                    {isShared
                      ? data.study.questionStem.slice(0, 20) + '…'
                      : data.study.questionStem}
                  </div>
                  <div className={s.chips}>
                    <span className={`${s.chip} ${s.chipSubject}`}>
                      {SUBJECT_LABEL[data.study.subject]}
                    </span>
                    <span className={`${s.chip} ${s.chipGrade}`}>
                      {data.study.tLevel}
                    </span>
                  </div>
                </div>
                {!isShared && (
                  <div className={s.thumbStat}>
                    掌握度：<span className={s.thumbStatDanger}>复习中</span>
                  </div>
                )}
              </div>
            </div>

            {/* Memory Curve */}
            <MemoryCurveCard
              nodes={data.study.nodes}
              data-testid="p11-related-study-memory-curve"
            />
          </div>
        )}

        {/* Cancelled study node (AC-P11-009) */}
        {isStudy && nodeCancelledStudy && (
          <div
            className={s.cancelledStudy}
            data-testid="p11-related-study-cancelled"
            role="status"
          >
            <p style={{ marginBottom: 12 }}>该复习节点已取消，下次排期已更新</p>
            <button
              className={s.retryBtn}
              onClick={() => nav('/wrongbook')}
              style={{ textDecoration: 'underline' }}
            >
              查看新排期
            </button>
          </div>
        )}

        {/* B3b · FAMILY form */}
        {isFamily && (
          <div data-testid="p11-related-family">
            <div className={s.familyCard}>
              {data.family?.note && (
                <p className={s.familyNote}>{data.family.note}</p>
              )}
              {data.family?.participants && data.family.participants.length > 0 && (
                <div className={s.familyMeta}>
                  参与人：{data.family.participants.join('、')}
                </div>
              )}
              {data.recurrence && (
                <div className={s.familyMeta} style={{ marginTop: 6 }}>
                  重复规则：{data.recurrence.rule}
                </div>
              )}
            </div>
          </div>
        )}

        {/* B3c · EXAM form */}
        {isExam && data.exam && (
          <div data-testid="p11-related-exam">
            <div className={s.examCard}>
              <div
                className={s.examSubjectChip}
                data-testid="p11-related-exam-subject-chip"
                style={{ background: SUBJECT_COLOR[data.exam.subject] }}
              >
                {SUBJECT_LABEL[data.exam.subject]}
              </div>

              {data.exam.location && (
                <div className={s.examLocation} data-testid="p11-related-exam-location">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7Z" stroke="currentColor" strokeWidth="1.8"/>
                    <circle cx="12" cy="9" r="2.5" stroke="currentColor" strokeWidth="1.8"/>
                  </svg>
                  {data.exam.location}
                </div>
              )}

              <div data-testid="p11-related-exam-countdown">
                <span className={s.examCountdown}>{data.exam.countdownDays}</span>
                <span className={s.examCountdownLabel}>天后</span>
              </div>

              {data.fromUser && (
                <div className={s.examFrom} data-testid="p11-related-exam-from">
                  来自 {data.fromUser.role === 'PARENT' ? '家长' : data.fromUser.role === 'TEACHER' ? '老师' : '自己'} {isShared ? '' : data.fromUser.name} 分享
                </div>
              )}
            </div>
          </div>
        )}

        {/* B4 · MetaRow (time & reminder — shared chrome) */}
        <section className={s.section} data-testid="p11-meta-row">
          <div className={s.sectionHd}>时间 &amp; 提醒</div>

          <div className={s.row}>
            <div className={s.rowIc} style={{ background: '#007AFF' }} aria-hidden="true">
              <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
                <rect x="2" y="3" width="12" height="11" rx="1.6" stroke="#fff" strokeWidth="1.4"/>
                <path d="M2 6h12M5 2v2M11 2v2" stroke="#fff" strokeWidth="1.4" strokeLinecap="round"/>
              </svg>
            </div>
            <div className={s.rowK}>开始</div>
            <div className={s.rowV}>{startFormatted}</div>
          </div>

          {data.durationMin && (
            <div className={s.row}>
              <div className={s.rowIc} style={{ background: '#FF9500' }} aria-hidden="true">
                <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
                  <circle cx="8" cy="8" r="6" stroke="#fff" strokeWidth="1.4"/>
                  <path d="M8 4.5V8l2.5 1.5" stroke="#fff" strokeWidth="1.4" strokeLinecap="round"/>
                </svg>
              </div>
              <div className={s.rowK}>时长</div>
              <div className={s.rowV}>{data.durationMin} 分钟</div>
            </div>
          )}

          {data.reminder && (
            <div className={s.row}>
              <div className={s.rowIc} style={{ background: '#34C759' }} aria-hidden="true">
                <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
                  <path d="M8 2.5c-3 0-5 2-5 5v3l-1 1.5v.8h12v-.8L13 10.5v-3c0-3-2-5-5-5Z" stroke="#fff" strokeWidth="1.4"/>
                  <path d="M7 13.5a1 1 0 0 0 2 0" stroke="#fff" strokeWidth="1.4" strokeLinecap="round"/>
                </svg>
              </div>
              <div className={s.rowK}>提醒</div>
              <div className={s.rowV}>
                开始前 {data.reminder.offsetMin} 分钟
                <span className={s.rowVDim}>{data.reminder.channel === 'PUSH' ? '推送' : 'App 内'}</span>
              </div>
            </div>
          )}

          {data.recurrence && !isFamily && (
            <div className={s.row}>
              <div className={s.rowIc} style={{ background: '#5856D6' }} aria-hidden="true">
                <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
                  <path d="M3 8a5 5 0 1 0 10 0 5 5 0 0 0-10 0Z" stroke="#fff" strokeWidth="1.4"/>
                  <path d="M8 5v3.5L10 10" stroke="#fff" strokeWidth="1.4" strokeLinecap="round"/>
                </svg>
              </div>
              <div className={s.rowK}>重复</div>
              <div className={s.rowV}>{data.recurrence.rule}</div>
            </div>
          )}
        </section>

        {/* Spacer for fixed CTA */}
        <div style={{ height: 20 }} aria-hidden="true" />
      </div>

      {/* ── B5 · Bottom CTA ─────────────────────────────── */}
      <footer
        className={s.actbar}
        data-testid="p11-bottom-cta"
        role="contentinfo"
      >
        {/* Ghost: STUDY → 延后; FAMILY → never show ghost; EXAM → never show ghost */}
        {isStudy && !isShared && (
          <button
            className={s.btnGhost}
            onClick={() => nav(-1)}
          >
            延后 30 分
          </button>
        )}

        {/* Primary CTA */}
        {isStudy && !isShared && !nodeCancelledStudy && data.study && (
          <button
            className={s.btnPrimary}
            data-testid="p11-bottom-cta-review-now"
            aria-label={`立即复习这道${SUBJECT_LABEL[data.study.subject]}题`}
            onClick={() => {
              if (data.study) {
                nav(`/review/exec/${data.study.nodeId}`);
              }
            }}
          >
            立即复习 →
          </button>
        )}

        {isFamily && !isShared && (
          <button
            className={s.btnPrimary}
            data-testid="p11-bottom-cta-edit"
            aria-label="编辑家庭事件"
            onClick={() => {/* open bottom sheet */}}
          >
            编辑
          </button>
        )}

        {isExam && !isShared && (
          <button
            className={s.btnPrimary}
            data-testid="p11-bottom-cta-add-calendar"
            aria-label="加入日历"
            onClick={() => {
              fetch(`/api/calendar/events/${eventId}/subscribe`, { method: 'POST' })
                .catch(() => {/* toast error */});
            }}
          >
            加入日历
          </button>
        )}

        {isShared && (
          <button
            className={s.btnPrimary}
            aria-label="注册以查看完整内容"
            onClick={() => nav('/auth')}
          >
            注册以查看完整内容 →
          </button>
        )}
      </footer>
    </div>
  );
};

// ─── TopBar sub-component ──────────────────────────────────────────────────────
const TopBar: React.FC<{
  backLabel: string;
  onBack: () => void;
  title: string;
  showEdit?: boolean;
  onEdit?: () => void;
  'data-testid'?: string;
}> = ({ backLabel, onBack, title, showEdit, onEdit, 'data-testid': testId }) => (
  <nav
    className={s.topnav}
    role="navigation"
    aria-label="返回导航"
    data-testid={testId ?? 'p11-top-bar'}
  >
    <button
      className={s.backBtn}
      onClick={onBack}
      aria-label={`返回${backLabel}`}
      data-testid="p11-top-bar-back"
    >
      <svg width="12" height="20" viewBox="0 0 12 20" fill="none" aria-hidden="true">
        <path d="M10 2L2 10l8 8" stroke="#007AFF" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"/>
      </svg>
      {backLabel}
    </button>
    <div className={s.topTitle}>{title}</div>
    {showEdit ? (
      <button className={s.editBtn} onClick={onEdit} aria-label="编辑">
        编辑
      </button>
    ) : (
      <div style={{ width: 44 }} aria-hidden="true" />
    )}
  </nav>
);

// ─── MemoryCurveCard sub-component ────────────────────────────────────────────
const MemoryCurveCard: React.FC<{
  nodes: MemoryNode[];
  'data-testid'?: string;
}> = ({ nodes, 'data-testid': testId }) => {
  const doneCount = nodes.filter((n) => n.status === 'done').length;
  const totalCount = nodes.length;
  const todayNode = nodes.find((n) => n.status === 'now');

  return (
    <section
      className={s.curveCard}
      data-testid={testId ?? 'p11-related-study-memory-curve'}
      role="figure"
      aria-label={`艾宾浩斯记忆曲线 · ${doneCount}/${totalCount} 已完成`}
    >
      <div className={s.curveHdr}>
        <div className={s.curveTtl}>记忆曲线</div>
        <div className={s.curveRight}>
          完成后预计留存{' '}
          <span className={s.curveRightVal}>
            {Math.round(60 + (doneCount / Math.max(totalCount, 1)) * 29)}%
          </span>
        </div>
      </div>

      {/* SVG graph (simplified from archive reference) */}
      <div className={s.curveGraph} aria-hidden="true">
        <svg viewBox="0 0 340 120" preserveAspectRatio="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="p11area" x1="0" x2="0" y1="0" y2="1">
              <stop offset="0" stopColor="#5856D6" stopOpacity=".22"/>
              <stop offset="1" stopColor="#5856D6" stopOpacity="0"/>
            </linearGradient>
            <linearGradient id="p11after" x1="0" x2="1" y1="0" y2="0">
              <stop offset="0" stopColor="#34C759" stopOpacity=".35"/>
              <stop offset="1" stopColor="#34C759" stopOpacity="0"/>
            </linearGradient>
          </defs>
          <line x1="0" y1="30" x2="340" y2="30" stroke="#E5E5EA" strokeDasharray="2 4"/>
          <line x1="0" y1="65" x2="340" y2="65" stroke="#E5E5EA" strokeDasharray="2 4"/>
          <line x1="0" y1="100" x2="340" y2="100" stroke="#E5E5EA" strokeDasharray="2 4"/>
          <path d="M20 22 C 60 55, 90 82, 130 96 S 220 108, 320 112"
                stroke="#CDCEE5" strokeWidth="1.4" strokeDasharray="3 3" fill="none"/>
          <path d="M20 22 L20 18 C 50 40, 70 22, 80 18 L80 14 C 110 34, 130 18, 140 14 L140 10 C 170 32, 190 14, 200 10"
                stroke="#5856D6" strokeWidth="2" fill="none" strokeLinejoin="round" strokeLinecap="round"/>
          <path d="M20 22 L20 18 C 50 40, 70 22, 80 18 L80 14 C 110 34, 130 18, 140 14 L140 10 C 170 32, 190 14, 200 10 L200 120 L20 120 Z"
                fill="url(#p11area)"/>
          <line x1="200" y1="4" x2="200" y2="110" stroke="#5856D6" strokeWidth="1.2" strokeDasharray="2 3" opacity=".6"/>
          <path d="M200 10 C 230 30, 250 12, 260 8 L260 6 C 290 26, 310 10, 320 6"
                stroke="#34C759" strokeWidth="2" strokeDasharray="4 3" fill="none" opacity=".8"/>
          <path d="M200 10 L200 120 L320 120 L320 6 C 310 10, 290 26, 260 6 L260 8 C 250 12, 230 30, 200 10 Z"
                fill="url(#p11after)"/>
        </svg>
      </div>

      {/* Node indicators */}
      <div className={s.nodes}>
        {nodes.map((nd, idx) => {
          const isDone = nd.status === 'done';
          const isNow = nd.status === 'now';
          const dotClass = isNow
            ? `${s.nodeDot} ${s.nodeDotToday}`
            : isDone
            ? `${s.nodeDot} ${s.nodeDotDone}`
            : s.nodeDot;

          const dueDate = new Date(nd.dueAt);
          const dateLabel = isNow
            ? '今天'
            : `${dueDate.getMonth() + 1}/${dueDate.getDate()}`;

          return (
            <div
              key={nd.tLevel}
              className={s.nodeItem}
              data-testid={`p11-memory-curve-node-${nd.tLevel}`}
              data-status={nd.status}
            >
              <span className={dotClass} aria-hidden="true" />
              <span className={isNow ? `${s.nodeLabel} ${s.nodeLabelToday}` : s.nodeLabel}>
                {nd.tLevel}
              </span>
              <span className={s.nodeDate}>{dateLabel}</span>
            </div>
          );
        })}
      </div>

      {/* Footer stats */}
      <div className={s.curveFooter}>
        <div className={s.retainText}>
          当前留存{' '}
          <span className={s.retainGreen}>
            {Math.round(40 + (doneCount / Math.max(totalCount, 1)) * 22)}%
          </span>
          {todayNode && (
            <>
              {' → '}
              <span className={s.retainGreen}>
                {Math.round(60 + (doneCount / Math.max(totalCount, 1)) * 29)}%
              </span>
            </>
          )}
        </div>
        {nodes.find((n) => n.status === 'future') && (
          <div className={s.nextText}>
            下一次：
            <span className={s.nextTextBold}>
              {(() => {
                const next = nodes.find((n) => n.status === 'future');
                if (!next) return '—';
                const d = new Date(next.dueAt);
                return `${d.getMonth() + 1} 月 ${d.getDate()} 日`;
              })()}
            </span>
          </div>
        )}
      </div>
    </section>
  );
};
