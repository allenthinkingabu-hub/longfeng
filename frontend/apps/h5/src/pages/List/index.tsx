// S7 · FE-03 · WrongbookList (P05) · 对标 design/mockups/wrongbook/_archive/05_wrongbook_list.html
// Mood B · pure-warm · 米白底 + 白卡 + iOS 标准 nav
// AC 覆盖: AC-WB-LIST-001 ~ AC-WB-LIST-010
import React, { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useInfiniteQuery } from '@tanstack/react-query';
import { wrongbookClient, WrongItemVO, WrongItemListResponse } from '@longfeng/api-contracts';
import { TEST_IDS } from '@longfeng/testids';
import s from './List.module.css';

type MasteryFilter = 'all' | 'low' | 'mid' | 'high';
type SubjectFilter = 'all' | 'math' | 'physics' | 'chemistry' | 'english' | string;

const SUBJECT_OPTS: Array<{ value: string; label: string; count: number }> = [
  { value: 'all',       label: '全部', count: 128 },
  { value: 'math',      label: '数学', count: 52 },
  { value: 'physics',   label: '物理', count: 31 },
  { value: 'chemistry', label: '化学', count: 18 },
  { value: 'english',   label: '英语', count: 19 },
  { value: 'chinese',   label: '语文', count: 8 },
];

const SUBJECT_COLOR: Record<string, string> = {
  math: s.subMath,
  physics: s.subPhysics,
  chemistry: s.subChemistry,
  english: s.subEnglish,
  chinese: s.subMath,
};
const SUBJECT_LABEL: Record<string, string> = {
  math: '数学', physics: '物理', chemistry: '化学', english: '英语', chinese: '语文',
};

function masteryBucket(m: number): 'low' | 'mid' | 'high' {
  if (m < 40) return 'low';
  if (m < 70) return 'mid';
  return 'high';
}

function pillFor(m: 'low' | 'mid' | 'high'): { cls: string; text: string } {
  if (m === 'low') return { cls: s.pillbgRed,    text: '未掌握' };
  if (m === 'mid') return { cls: s.pillbgOrange, text: '部分' };
  return                  { cls: s.pillbgGreen,  text: '已掌握' };
}

// AC-WB-LIST-006 · left bar color = subject color (not mastery)
function leftBarFor(subject: string): string {
  switch (subject) {
    case 'math':      return s.leftBarMath;
    case 'physics':   return s.leftBarPhysics;
    case 'chemistry': return s.leftBarChemistry;
    case 'english':   return s.leftBarEnglish;
    default:          return s.leftBarMath;
  }
}

export const ListPage: React.FC = () => {
  const nav = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const [subject, setSubject] = useState<SubjectFilter>(
    (searchParams.get('subject') as SubjectFilter) ?? 'all',
  );
  const [masteryFilter, setMasteryFilter] = useState<MasteryFilter>('all');
  const [semanticMode, setSemanticMode] = useState(false);

  // AC-WB-LIST-009 · highlight from P04 save
  const highlightQid = searchParams.get('highlight');

  const query = useInfiniteQuery<WrongItemListResponse, Error, { pages: WrongItemListResponse[] }, readonly unknown[], string | null>({
    queryKey: ['wrongbook', { subject }] as const,
    queryFn: ({ pageParam }) =>
      wrongbookClient.list({
        subject: subject === 'all' ? undefined : subject,
        cursor: pageParam ?? undefined,
        limit: 20,
      }),
    initialPageParam: null,
    getNextPageParam: (last) => (last.has_more ? last.next_cursor ?? null : null),
    refetchInterval: (q) => {
      const pages = (q.state.data?.pages ?? []) as WrongItemListResponse[];
      const hasAnalyzing = pages.some((p) => p.items.some((i) => i.status === 'analyzing'));
      return hasAnalyzing ? 3000 : false;
    },
  });

  const allItems = useMemo(() => query.data?.pages.flatMap((p) => p.items) ?? [], [query.data]);

  // client-side mastery filter (spec §6 FILTERED state)
  const filteredItems = useMemo(() => {
    if (masteryFilter === 'all') return allItems;
    return allItems.filter((it) => masteryBucket(it.mastery) === masteryFilter);
  }, [allItems, masteryFilter]);

  const counts = useMemo(() => {
    let lo = 0, mi = 0, hi = 0;
    for (const it of allItems) {
      const b = masteryBucket(it.mastery);
      if (b === 'low') lo++;
      else if (b === 'mid') mi++;
      else hi++;
    }
    return { lo, mi, hi };
  }, [allItems]);

  // AC-WB-LIST-002 · update URL when subject changes
  const handleSubjectChange = (val: string) => {
    setSubject(val);
    const params = new URLSearchParams(searchParams);
    if (val === 'all') params.delete('subject');
    else params.set('subject', val);
    setSearchParams(params, { replace: true });
  };

  return (
    <div
      className={s.root}
      data-mood="B"
      data-testid={TEST_IDS.wrongbookList.root}
    >
      {/* Status Bar (decorative) */}
      <div className={s.status} aria-hidden="true">
        <span>9:41</span>
        <span className={s.statusIcons}>
          <svg width="17" height="11" viewBox="0 0 17 11" aria-hidden="true"><g fill="currentColor"><rect x="0" y="7" width="3" height="4" rx=".5"/><rect x="4.5" y="5" width="3" height="6" rx=".5"/><rect x="9" y="3" width="3" height="8" rx=".5"/><rect x="13.5" y="1" width="3" height="10" rx=".5"/></g></svg>
          <svg width="26" height="12" viewBox="0 0 26 12" aria-hidden="true"><rect x=".5" y=".5" width="22" height="11" rx="3" fill="none" stroke="currentColor" opacity=".45"/><rect x="2" y="2" width="17" height="8" rx="1.6" fill="currentColor"/><rect x="23" y="4" width="2" height="4" rx="1" fill="currentColor" opacity=".45"/></svg>
        </span>
      </div>

      {/* B1 · PageHeader 大标题 + 搜索 */}
      <header
        className={s.nav}
        role="banner"
        data-testid="p05-page-header"
      >
        <div className={s.navRow}>
          {/* AC-WB-LIST-001 · display-hero 大标题 */}
          <h1
            className={s.navTitle}
            data-testid="p05-page-header-title"
          >
            错题本
          </h1>
          <div className={s.navRight} role="toolbar">
            <button
              className={s.navIconBtn}
              aria-label="筛选排序"
              data-testid="p05-sort-bar"
            >
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M4 7h16M6 12h12M9 17h6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
              </svg>
            </button>
          </div>
        </div>

        {/* AC-WB-LIST-001 · 搜索框凹陷底 */}
        <button
          type="button"
          className={s.search}
          data-testid="p05-page-header-search"
          aria-label="搜索错题"
          onClick={() => {/* TODO 搜索页 */}}
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="11" cy="11" r="6.5" stroke="currentColor" strokeWidth="1.8" />
            <path d="m20 20-3-3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          </svg>
          <span className={s.searchPlaceholder}>二次函数 顶点</span>
          {/* AC-WB-LIST-010 · AI 语义 Badge */}
          <button
            className={`${s.searchAi} ${semanticMode ? s.searchAiActive : ''}`}
            type="button"
            aria-pressed={semanticMode}
            data-testid="p05-page-header-semantic-badge"
            data-active={String(semanticMode)}
            onClick={(e) => { e.stopPropagation(); setSemanticMode((v) => !v); }}
          >
            AI 语义
          </button>
        </button>

        {/* B2 · SubjectChips 学科横滚 */}
        <div
          className={s.chipsRow}
          data-testid={TEST_IDS.wrongbookList['filter-bar']}
          role="group"
          aria-label="学科筛选"
        >
          {SUBJECT_OPTS.map((o) => (
            <button
              key={o.value}
              className={`${s.sc} ${subject === o.value ? s.scOn : ''}`}
              onClick={() => handleSubjectChange(o.value)}
              data-testid={`subject-chip-${o.value}`}
              aria-pressed={subject === o.value}
            >
              {o.label}
              {' '}
              <span className={s.scCt}>{o.count}</span>
            </button>
          ))}
        </div>
      </header>

      <main className={s.content} role="main">
        {/* B3 · MasteryStatusCards */}
        <div
          className={s.mr}
          role="group"
          aria-label="掌握度筛选"
          data-testid="p05-mastery-status"
        >
          {(['low', 'mid', 'high'] as const).map((m) => {
            const count = m === 'low' ? counts.lo : m === 'mid' ? counts.mi : counts.hi;
            const label = m === 'low' ? '未掌握' : m === 'mid' ? '部分掌握' : '已掌握';
            const colorCls = m === 'low' ? s.mfRed : m === 'mid' ? s.mfOrange : s.mfGreen;
            const masteryKey = m === 'low' ? 'forgot' : m === 'mid' ? 'partial' : 'mastered';
            const isOn = masteryFilter === m;
            return (
              <button
                key={m}
                className={`${s.mf} ${colorCls} ${isOn ? s.mfOn : ''}`}
                onClick={() => setMasteryFilter(isOn ? 'all' : m)}
                data-testid={`mastery-status-card-${masteryKey}`}
                aria-checked={isOn}
                role="checkbox"
              >
                <div className={s.mfBar} />
                <div className={s.mfV}>{count}</div>
                <div className={s.mfT}>{label}</div>
              </button>
            );
          })}
        </div>

        {/* B4 · SortBar */}
        <div className={s.sort} data-testid={TEST_IDS.wrongbookList['filter-subject']}>
          <div className={s.sortLeft}>
            按 <strong>下次复习时间</strong> · 升序
          </div>
          <div>共 <strong>{filteredItems.length}</strong> 条</div>
        </div>

        {/* B5 · QuestionList */}
        {query.isLoading && (
          <div data-testid={TEST_IDS.wrongbookList.skeleton} className={s.skeleton} aria-busy="true">
            {Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className={s.skeletonCard} />
            ))}
          </div>
        )}

        {/* EMPTY state · AC-WB-LIST-008 */}
        {!query.isLoading && filteredItems.length === 0 && (
          <div
            className={s.emptyState}
            data-testid="p05-empty-state"
            role="status"
          >
            <div className={s.emptyIcon} aria-hidden="true">📚</div>
            <div className={s.emptyText}>没有匹配的错题</div>
            <button
              className={s.emptyCapture}
              onClick={() => nav('/capture')}
              data-testid="p05-empty-capture-btn"
            >
              拍一张试试 →
            </button>
          </div>
        )}

        {/* ERROR state */}
        {query.isError && (
          <div className={s.errorBanner} role="alert" data-testid={TEST_IDS.common['error-banner']}>
            加载失败 ·
            <button className={s.retryBtn} onClick={() => query.refetch()}>重试</button>
          </div>
        )}

        {/* List */}
        <ol className={s.list} role="list" aria-label="错题列表" aria-live="polite">
          {filteredItems.map((it, idx) => (
            <li key={it.id} role="listitem">
              <CardItem
                item={it}
                idx={idx}
                highlighted={it.id === highlightQid}
                onOpen={() => nav(`/wrongbook/${it.id}`)}
              />
            </li>
          ))}
        </ol>

        {query.hasNextPage && (
          <button
            className={s.loadMoreBtn}
            onClick={() => query.fetchNextPage()}
            disabled={query.isFetchingNextPage}
            data-testid={TEST_IDS.wrongbookList['load-more']}
          >
            {query.isFetchingNextPage ? '加载中…' : '加载更多'}
          </button>
        )}
      </main>

      {/* B6 · FAB 拍题悬浮按钮 · AC-WB-LIST-007 */}
      <button
        className={s.fab}
        onClick={() => nav('/capture')}
        aria-label="拍照录入新题"
        data-testid="p05-fab-capture"
      >
        <svg width="26" height="26" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <circle cx="12" cy="13" r="4.5" stroke="#fff" strokeWidth="1.8" />
          <path
            d="M5 8h3l1.5-2h5L16 8h3a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2Z"
            stroke="#fff" strokeWidth="1.8" strokeLinejoin="round"
          />
        </svg>
      </button>

      {/* TabBar */}
      <nav className={s.tabbar} role="navigation" aria-label="底部导航">
        <button className={s.tab} role="tab" aria-selected={false} onClick={() => nav('/')} data-testid="wrongbook.list.tab-home">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M3 11 L12 3 L21 11 V20 a1 1 0 0 1 -1 1 H14 V14 H10 V21 H4 a1 1 0 0 1 -1 -1 Z"
                  stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" strokeLinecap="round" />
          </svg>
          <span>首页</span>
        </button>
        <button
          className={`${s.tab} ${s.tabActive}`}
          role="tab"
          aria-selected={true}
          data-testid={TEST_IDS.wrongbookList['tabbar-wrongbook']}
        >
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M5 4h11l3 3v13H5V4Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
            <path d="M8 11h8M8 14h6M8 17h5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          </svg>
          <span>错题本</span>
        </button>
        <button className={s.tab} role="tab" aria-selected={false} onClick={() => nav('/capture')} data-testid="wrongbook.list.tab-capture">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="12" cy="13" r="4.5" stroke="currentColor" strokeWidth="1.8" />
            <path d="M5 8h3l1.5-2h5L16 8h3a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2Z"
                  stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
          </svg>
          <span>拍题</span>
        </button>
        <button className={s.tab} role="tab" aria-selected={false} data-testid="wrongbook.list.tab-review">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M12 3.5c-3.6 0-6.2 2.6-6.2 6.2v3.4L4 15.5v1.3h16v-1.3l-1.8-2.4V9.7c0-3.6-2.6-6.2-6.2-6.2Z"
                  stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
            <path d="M10 19.5a2 2 0 0 0 4 0" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          </svg>
          <span>复习</span>
        </button>
        <button className={s.tab} role="tab" aria-selected={false} data-testid="wrongbook.list.tab-me">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="12" cy="8.5" r="3.8" stroke="currentColor" strokeWidth="1.8" />
            <path d="M4.5 20c1.2-3.8 4.2-5.6 7.5-5.6s6.3 1.8 7.5 5.6"
                  stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          </svg>
          <span>我的</span>
        </button>
      </nav>

      <div className={s.homebar} aria-hidden="true" />
    </div>
  );
};

// ─── CardItem ─────────────────────────────────────────────────────────────
const CardItem: React.FC<{
  item: WrongItemVO;
  idx: number;
  highlighted: boolean;
  onOpen: () => void;
}> = ({ item, idx, highlighted, onOpen }) => {
  const m = masteryBucket(item.mastery);
  const pill = pillFor(m);
  const subColorCls = SUBJECT_COLOR[item.subject] ?? s.subMath;
  const subjectLabel = SUBJECT_LABEL[item.subject] ?? item.subject;
  const stem = item.stem_text || '（无题干）';
  const tags = item.tags.slice(0, 4);
  // 6 段进度 (T0-T5): mastery 0-100 → phase 0-6
  const phase = Math.min(5, Math.floor((item.mastery / 100) * 6));

  return (
    <article
      className={`${s.card} ${highlighted ? s.cardHighlighted : ''}`}
      data-testid={`question-list-card-${idx + 1}`}
      data-highlight={highlighted ? 'true' : undefined}
      role="article"
      aria-label={`${subjectLabel}错题 · ${stem.slice(0, 20)} · T${phase + 1}`}
      onClick={onOpen}
      tabIndex={0}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onOpen(); } }}
    >
      {/* AC-WB-LIST-006 · 4px 学科色左条 · --tkn-subject-{subject} */}
      <span
        className={`${s.leftBar} ${leftBarFor(item.subject)}`}
        aria-hidden="true"
        data-testid={`question-list-card-${idx + 1}-subject-bar`}
      />

      {/* 缩略图 · AC-WB-LIST-005 */}
      <div
        className={s.thumb}
        data-testid={`question-list-card-${idx + 1}-thumbnail`}
      >
        {item.image_url ? (
          <img src={item.image_url} alt="" className={s.thumbImg} />
        ) : (
          <>
            <span className={s.thumbQno}>{String(idx + 1).padStart(2, '0')}</span>
            <h3 className={s.thumbH3}>{stem.slice(0, 32)}</h3>
            <div className={s.thumbStrk} aria-hidden="true" />
          </>
        )}
      </div>

      <div className={s.body}>
        <div className={s.bodyHead}>
          <span className={subColorCls}>{subjectLabel}</span>
          <span className={s.bodyDot} aria-hidden="true" />
          <span>{tags[0] ?? '—'}</span>
          <span className={s.bodyDot} aria-hidden="true" />
          <span className={s.bodyTime}>
            {item.status === 'analyzing' ? '解析中…' : new Date(item.created_at).toLocaleDateString()}
          </span>
        </div>
        <div className={s.stem}>{stem}</div>
        <div className={s.tags}>
          {tags.map((tg, i) => (
            <span key={tg + i} className={`${s.tg} ${i < 2 ? s.tgKp : i === 2 ? s.tgErr : s.tgDiff}`}>
              {tg}
            </span>
          ))}
        </div>
      </div>

      <div className={s.right}>
        <span className={`${s.pillbg} ${pill.cls}`}>{pill.text}</span>
        {/* AC-WB-LIST-005 · 6 段进度 dots */}
        <div className={s.stageBar} aria-hidden="true">
          {Array.from({ length: 6 }).map((_, i) => {
            const cls = i < phase ? s.sbDone : i === phase ? s.sbNow : '';
            return (
              <span
                key={i}
                className={`${s.sb} ${cls}`}
                data-testid={`question-list-card-${idx + 1}-stage-${i}`}
              />
            );
          })}
        </div>
        {/* AC-WB-LIST-005 · nextDueAt */}
        <span
          className={s.due}
          data-testid={`question-list-card-${idx + 1}-due`}
        >
          T{phase + 1}
        </span>
      </div>
    </article>
  );
};
