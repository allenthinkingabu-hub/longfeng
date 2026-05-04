/**
 * P12 · 通知中心 · NotificationsPage
 * Mood B (pure-warm) · 米白底 + 白卡 + iOS nav
 * archive ref: _archive/12_notifications.html
 * spec: design/system/pages/P12-notifications.spec.md
 * AC 覆盖: AC-P12-001 ~ AC-P12-010
 */
import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import s from './Notifications.module.css';

/* ── Types (spec §4) ── */
type NotifKind = 'REVIEW' | 'EXAM' | 'SHARE' | 'SYSTEM';

interface NotificationItem {
  id: string;
  kind: NotifKind;
  title: string;
  subtitle: string;
  occurredAt: string;
  read: boolean;
  targetType: 'EVENT' | 'NODE' | 'SETTING' | 'STATIC';
  targetId?: string;
  fromUser?: { name: string; role: 'PARENT' | 'TEACHER' };
  timeLabel: string;
  body?: string;
}

type PageState = 'LOADING' | 'READY' | 'EMPTY' | 'ERROR' | 'READ_ALL_PENDING';

/* ── Mock data ── */
const MOCK_TODAY: NotificationItem[] = [
  {
    id: 'n1', kind: 'REVIEW', title: '该复习《Hibernate 二级缓存》了',
    subtitle: '第 3 阶段复习（间隔 4 天）· 14:30 开始',
    body: '第 3 阶段复习（间隔 4 天）· 14:30 开始，预计 40 分钟。完成后下次复习 2026-05-02。',
    occurredAt: '2026-05-02T14:28:00Z', read: false,
    targetType: 'NODE', targetId: 'node-123', timeLabel: '2 分钟前',
  },
  {
    id: 'n2', kind: 'EXAM', title: '数学月考 · 明天 14:00',
    subtitle: '考试范围：第 1-8 章 · 已同步到日历',
    occurredAt: '2026-05-02T09:00:00Z', read: false,
    targetType: 'EVENT', targetId: 'evt-456', timeLabel: '5 小时前',
  },
];

const MOCK_YESTERDAY: NotificationItem[] = [
  {
    id: 'n3', kind: 'SHARE', title: '妈妈分享了「5 月月考安排」',
    subtitle: '5 月 12 日 · 周一 · 已同步到日历',
    occurredAt: '2026-05-01T18:00:00Z', read: true,
    targetType: 'EVENT', targetId: 'evt-789',
    fromUser: { name: '妈妈', role: 'PARENT' }, timeLabel: '昨天 18:00',
  },
];

const MOCK_THIS_WEEK: NotificationItem[] = [
  {
    id: 'n4', kind: 'SYSTEM', title: '免打扰时段已更新',
    subtitle: '23:00 – 07:30 · 记忆曲线节奏不变',
    occurredAt: '2026-04-28T10:00:00Z', read: true,
    targetType: 'SETTING', targetId: 'quietHours', timeLabel: '周日',
  },
];

/* ── Kind → badge color ── */
function badgeClass(kind: NotifKind): string {
  switch (kind) {
    case 'REVIEW': return s.badgeBlue;
    case 'EXAM':   return s.badgeRed;
    case 'SHARE':  return s.badgeOrange;
    case 'SYSTEM': return s.badgeIndigo;
    default:       return s.badgeBlue;
  }
}

/* ── Kind → SVG icon ── */
const KindIcon: React.FC<{ kind: NotifKind }> = ({ kind }) => {
  switch (kind) {
    case 'REVIEW':
      return (
        <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
          <path d="M4 6h16M4 12h10M4 18h16" stroke="#fff" strokeWidth="1.8" strokeLinecap="round"/>
        </svg>
      );
    case 'EXAM':
      return (
        <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
          <rect x="3" y="4" width="18" height="18" rx="3" stroke="#fff" strokeWidth="1.6"/>
          <path d="M3 9h18M8 4V2M16 4V2" stroke="#fff" strokeWidth="1.6" strokeLinecap="round"/>
          <circle cx="12" cy="15" r="2" fill="#fff"/>
        </svg>
      );
    case 'SHARE':
      return (
        <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
          <circle cx="18" cy="5" r="3" stroke="#fff" strokeWidth="1.6"/>
          <circle cx="6" cy="12" r="3" stroke="#fff" strokeWidth="1.6"/>
          <circle cx="18" cy="19" r="3" stroke="#fff" strokeWidth="1.6"/>
          <path d="M8.6 10.7l6.8-4M8.6 13.3l6.8 4" stroke="#fff" strokeWidth="1.6"/>
        </svg>
      );
    default:
      return (
        <svg viewBox="0 0 24 24" width="17" height="17" fill="none" aria-hidden="true">
          <circle cx="12" cy="12" r="9" stroke="#fff" strokeWidth="1.6"/>
          <path d="M12 8v4M12 16h.01" stroke="#fff" strokeWidth="1.6" strokeLinecap="round"/>
        </svg>
      );
  }
};

/* ── StatusBar icons ── */
const StatusIcons = () => (
  <div className={s.statusRight}>
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
);

/* ── NotifCard ── */
const NotifCard: React.FC<{
  item: NotificationItem;
  index: number;
  onTap: (item: NotificationItem) => void;
}> = ({ item, index, onTap }) => (
  <div
    className={`${s.card} ${!item.read ? s.cardUnread : ''}`}
    data-testid={`p12-notif-card-${index}`}
    data-kind={item.kind}
    data-read={item.read ? 'true' : 'false'}
    role="button"
    tabIndex={0}
    aria-label={item.title}
    onClick={() => onTap(item)}
    onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') onTap(item); }}
  >
    <div className={s.cardRow1}>
      <div
        className={`${s.badge} ${badgeClass(item.kind)}`}
        data-testid={`p12-notif-card-${index}-icon`}
      >
        <KindIcon kind={item.kind} />
      </div>
      <div className={s.cardSource}>
        <strong className={s.cardSourceStrong}>
          {item.kind === 'REVIEW' ? '学习 · 记忆曲线' :
           item.kind === 'EXAM'   ? '日历 · 考试' :
           item.kind === 'SHARE'  ? `${item.fromUser?.name ?? '家人'}分享` :
           '系统通知'}
        </strong>
      </div>
      <div className={s.cardTime} data-testid={`p12-notif-card-${index}-time`}>
        {item.timeLabel}
      </div>
    </div>

    <div className={s.cardTitle} data-testid={`p12-notif-card-${index}-title`}>
      {item.title}
    </div>
    {item.subtitle && (
      <div className={s.cardBody} data-testid={`p12-notif-card-${index}-subtitle`}>
        {item.subtitle}
      </div>
    )}

    {/* 未读红点 (AC-P12-004) */}
    {!item.read && (
      <div
        className={s.unreadDot}
        data-testid={`p12-notif-card-${index}-unread-dot`}
        aria-label="未读"
        role="status"
      />
    )}

    {/* Action buttons for REVIEW type */}
    {item.kind === 'REVIEW' && (
      <div className={s.actions}>
        <button className={`${s.btnBase} ${s.btnPrimary}`} type="button">
          立即复习
        </button>
        <button className={s.btnBase} type="button">
          稍后
        </button>
      </div>
    )}
  </div>
);

/* ── Skeleton ── */
const SkeletonCards = () => (
  <>
    {[0, 1].map((i) => (
      <div key={i} className={s.skeletonCard}>
        <div className={s.skeletonLine} style={{ width: '70%' }} />
        <div className={s.skeletonLine} style={{ width: '90%' }} />
        <div className={s.skeletonLine} />
      </div>
    ))}
  </>
);

export const NotificationsPage: React.FC = () => {
  const nav = useNavigate();

  const [groups] = useState({
    today:     MOCK_TODAY,
    yesterday: MOCK_YESTERDAY,
    thisweek:  MOCK_THIS_WEEK,
    earlier:   [] as NotificationItem[],
  });
  const [pageState] = useState<PageState>('READY');
  const [readAllPending, setReadAllPending] = useState(false);
  const [localReadIds, setLocalReadIds] = useState<Set<string>>(new Set());

  const unreadTotal = [
    ...groups.today, ...groups.yesterday, ...groups.thisweek, ...groups.earlier,
  ].filter((n) => !n.read && !localReadIds.has(n.id)).length;

  const isAllEmpty =
    groups.today.length === 0 &&
    groups.yesterday.length === 0 &&
    groups.thisweek.length === 0 &&
    groups.earlier.length === 0;

  const handleMarkAllRead = useCallback(async () => {
    setReadAllPending(true);
    // Optimistic update
    const allIds = [
      ...groups.today, ...groups.yesterday, ...groups.thisweek, ...groups.earlier,
    ].map((n) => n.id);
    setLocalReadIds(new Set(allIds));
    try {
      // POST /api/notifications/read-all
      await new Promise((r) => setTimeout(r, 300));
    } finally {
      setReadAllPending(false);
    }
  }, [groups]);

  const handleCardTap = useCallback((item: NotificationItem) => {
    // Mark single as read
    setLocalReadIds((prev) => new Set([...prev, item.id]));
    // Route by targetType (AC-P12-006)
    switch (item.targetType) {
      case 'EVENT':   nav(`/event/${item.targetId}`);   break;
      case 'NODE':    nav(`/review/exec/${item.targetId}`); break;
      case 'SETTING': nav(`/me`);                        break;
      default:        /* STATIC: toast */ break;
    }
  }, [nav]);

  const renderItem = (item: NotificationItem, idx: number) => (
    <NotifCard
      key={item.id}
      item={{ ...item, read: item.read || localReadIds.has(item.id) }}
      index={idx + 1}
      onTap={handleCardTap}
    />
  );

  return (
    <main
      className={s.page}
      role="main"
      data-testid="p12-root"
      data-mood="B"
    >
      {/* StatusBar 已删 · iOS chrome · _archive data-mockup-chrome="iphone-statusbar" */}

      {/* ── Nav ── */}
      <nav className={s.nav} role="navigation" aria-label="通知中心导航">
        <div className={s.navRow}>
          <button
            className={s.navBack}
            type="button"
            onClick={() => nav(-1 as unknown as string)}
            aria-label="返回"
          >
            <svg viewBox="0 0 12 20" width="12" height="20" fill="none" aria-hidden="true">
              <path d="M10 2 2 10l8 8" stroke="#007AFF" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            首页
          </button>
          <button
            className={s.navMarkAllRead}
            type="button"
            disabled={unreadTotal === 0 || readAllPending}
            onClick={handleMarkAllRead}
            data-testid="p12-header-mark-all-read"
            aria-label="全部已读"
          >
            {readAllPending ? (
              <span aria-live="polite">处理中…</span>
            ) : (
              '全部已读'
            )}
          </button>
        </div>
        <h1 className={s.navTitle} data-testid="p12-header-title">
          通知{' '}
          {unreadTotal > 0 && (
            <span className={s.navTitleCount} aria-live="polite">
              · {unreadTotal} 条未读
            </span>
          )}
        </h1>
      </nav>

      {/* ── Content ── */}
      <div className={s.content}>

        {/* Loading skeleton */}
        {pageState === 'LOADING' && (
          <>
            {['今天', '昨天', '本周', '更早'].map((g) => (
              <React.Fragment key={g}>
                <div className={s.groupTitle}>{g}</div>
                <SkeletonCards />
              </React.Fragment>
            ))}
          </>
        )}

        {/* Empty state (AC-P12-008) */}
        {(pageState === 'READY' && isAllEmpty) && (
          <div
            className={s.emptyState}
            data-testid="p12-empty-state"
            role="status"
            aria-label="暂无新消息"
          >
            <div className={s.emptyIcon} aria-hidden="true">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" stroke="#8E8E93" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/>
                <path d="M13.7 21a2 2 0 0 1-3.4 0" stroke="#8E8E93" strokeWidth="1.6" strokeLinecap="round"/>
              </svg>
            </div>
            <p className={s.emptyTitle}>暂无新消息</p>
            <p className={s.emptyBody}>复习提醒、日历事件和重要通知<br/>将在这里显示</p>
          </div>
        )}

        {/* Groups (AC-P12-002) */}
        {pageState === 'READY' && !isAllEmpty && (
          <>
            {groups.today.length > 0 && (
              <section
                role="region"
                aria-label="今天的通知"
                data-testid="p12-group-today"
              >
                <div className={s.groupTitle}>
                  <span>
                    {groups.today.some((n) => !n.read && !localReadIds.has(n.id)) && (
                      <span className={s.groupTitleDot} aria-label="有未读" />
                    )}
                    今天
                  </span>
                </div>
                {groups.today.map((item, idx) => renderItem(item, idx))}
              </section>
            )}

            {groups.yesterday.length > 0 && (
              <section
                role="region"
                aria-label="昨天的通知"
                data-testid="p12-group-yesterday"
              >
                <div className={s.groupTitle}>昨天</div>
                {groups.yesterday.map((item, idx) =>
                  renderItem(item, groups.today.length + idx)
                )}
              </section>
            )}

            {groups.thisweek.length > 0 && (
              <section
                role="region"
                aria-label="本周通知"
                data-testid="p12-group-thisweek"
              >
                <div className={s.groupTitle}>本周</div>
                {groups.thisweek.map((item, idx) =>
                  renderItem(item, groups.today.length + groups.yesterday.length + idx)
                )}
              </section>
            )}

            {groups.earlier.length > 0 && (
              <section
                role="region"
                aria-label="更早的通知"
                data-testid="p12-group-earlier"
              >
                <div className={s.groupTitle}>更早</div>
                {groups.earlier.map((item, idx) =>
                  renderItem(item,
                    groups.today.length + groups.yesterday.length +
                    groups.thisweek.length + idx)
                )}
              </section>
            )}
          </>
        )}
      </div>

    </main>
  );
};
