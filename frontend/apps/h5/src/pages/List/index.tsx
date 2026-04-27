// S7 · SC-08.AC-1 · 错题列表 · 对标 design/mockups/wrongbook/05_wrongbook_list.html · iOS HIG
import React, { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useInfiniteQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { wrongbookClient, WrongItemVO, WrongItemListResponse } from '@longfeng/api-contracts';
import { TEST_IDS } from '@longfeng/testids';
import s from './List.module.css';

type StatusTab = 'active' | 'mastered';
type Mastery = 'all' | 'low' | 'mid' | 'high';

const SUBJECT_OPTS: Array<{ value: string; label: string; count: number }> = [
  { value: 'all', label: '全部', count: 128 },
  { value: 'math', label: '数学', count: 52 },
  { value: 'physics', label: '物理', count: 31 },
  { value: 'chemistry', label: '化学', count: 18 },
  { value: 'english', label: '英语', count: 19 },
  { value: 'chinese', label: '语文', count: 8 },
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
  if (m === 'low') return { cls: s.pillbgRed, text: '未掌握' };
  if (m === 'mid') return { cls: s.pillbgOrange, text: '部分' };
  return { cls: s.pillbgGreen, text: '已掌握' };
}

function leftBarFor(m: 'low' | 'mid' | 'high'): string {
  if (m === 'low') return s.leftBarRed;
  if (m === 'mid') return s.leftBarOrange;
  return s.leftBarGreen;
}

export const ListPage: React.FC = () => {
  const { t } = useTranslation();
  const nav = useNavigate();
  const [status] = useState<StatusTab>('active');
  const [subject, setSubject] = useState<string>('all');
  const [masteryFilter, setMasteryFilter] = useState<Mastery>('low');

  const query = useInfiniteQuery<WrongItemListResponse, Error, { pages: WrongItemListResponse[] }, readonly unknown[], string | null>({
    queryKey: ['wrongbook', status, { subject, masteryFilter }] as const,
    queryFn: ({ pageParam }) =>
      wrongbookClient.list({
        status,
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

  const items = useMemo(() => query.data?.pages.flatMap((p) => p.items) ?? [], [query.data]);

  const filteredItems = useMemo(
    () => items.filter((it) => masteryFilter === 'all' || masteryBucket(it.mastery) === masteryFilter),
    [items, masteryFilter],
  );

  const counts = useMemo(() => {
    let lo = 0, mi = 0, hi = 0;
    for (const it of items) {
      const b = masteryBucket(it.mastery);
      if (b === 'low') lo++;
      else if (b === 'mid') mi++;
      else hi++;
    }
    return { lo, mi, hi };
  }, [items]);

  return (
    <div className={s.root} data-testid={TEST_IDS.wrongbookList.root}>
      <div className={s.nav}>
        <div className={s.navRow}>
          <h1 className={s.navTitle}>{t('wrongbook_list.title')}</h1>
          <div className={s.navRight}>
            <button className={s.navIconBtn} aria-label="筛选" data-testid="wrongbook.list.filter-toggle">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M4 7h16M6 12h12M9 17h6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
              </svg>
            </button>
          </div>
        </div>
        <button
          type="button"
          className={s.search}
          data-testid="wrongbook.list.search"
          onClick={() => { /* TODO 搜索页 */ }}
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="11" cy="11" r="6.5" stroke="currentColor" strokeWidth="1.8" />
            <path d="m20 20-3-3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          </svg>
          <span className={s.searchPlaceholder}>搜索题目 · 二次函数 顶点</span>
          <span className={s.searchAi}>AI 语义</span>
        </button>
        <div className={s.chipsRow} data-testid={TEST_IDS.wrongbookList['filter-bar']}>
          {SUBJECT_OPTS.map((o) => (
            <button
              key={o.value}
              className={`${s.sc} ${subject === o.value ? s.scOn : ''}`}
              onClick={() => setSubject(o.value)}
              data-testid={`wrongbook.list.subject-chip-${o.value}`}
            >
              {o.label} <span className={s.scCt}>{o.count}</span>
            </button>
          ))}
        </div>
      </div>

      <div className={s.content}>
        <div className={s.mr}>
          {(['low', 'mid', 'high'] as const).map((m) => {
            const v = m === 'low' ? counts.lo : m === 'mid' ? counts.mi : counts.hi;
            const label = m === 'low' ? '未掌握' : m === 'mid' ? '部分掌握' : '已掌握';
            const colorCls = m === 'low' ? s.mfRed : m === 'mid' ? s.mfOrange : s.mfGreen;
            const onCls = masteryFilter === m ? s.mfOn : '';
            // 兼容旧 testid · low ↔ active-tab（进行中）· high ↔ archive-tab（已归档）
            const compatTestId = m === 'low'
              ? TEST_IDS.wrongbookList['active-tab']
              : m === 'high'
                ? TEST_IDS.wrongbookList['archive-tab']
                : `wrongbook.list.mastery-mid`;
            return (
              <button
                key={m}
                className={`${s.mf} ${colorCls} ${onCls}`}
                onClick={() => setMasteryFilter(m)}
                data-testid={compatTestId}
              >
                <div className={s.mfBar} />
                <div className={s.mfV}>{v}</div>
                <div className={s.mfT}>{label}</div>
              </button>
            );
          })}
        </div>

        <div className={s.sort}>
          <div>按 <strong>下次复习时间</strong> · 升序</div>
          <div>共 <strong>{filteredItems.length}</strong> 条</div>
        </div>

        {query.isLoading && (
          <div data-testid={TEST_IDS.wrongbookList.skeleton} className={s.card}>
            <div className={s.thumb} />
            <div className={s.body}>
              <div className={s.bodyHead}><span>加载中…</span></div>
            </div>
          </div>
        )}
        {!query.isLoading && filteredItems.length === 0 && (
          <div className={s.card} data-testid={TEST_IDS.wrongbookList.empty} style={{ display: 'flex' }}>
            <div className={s.body}>
              <div className={s.stem}>{t('wrongbook_list.empty_active')}</div>
            </div>
          </div>
        )}
        {filteredItems.map((it, idx) => (
          <CardItem key={it.id} item={it} idx={idx} onOpen={() => nav(`/wrongbook/${it.id}`)} />
        ))}

        {query.hasNextPage && (
          <button
            className={s.card}
            onClick={() => query.fetchNextPage()}
            disabled={query.isFetchingNextPage}
            data-testid={TEST_IDS.wrongbookList['load-more']}
            style={{ justifyContent: 'center', textAlign: 'center', display: 'flex' }}
          >
            <span className={s.loadMoreText}>{t('wrongbook_list.load_more')}</span>
          </button>
        )}
      </div>

      <button
        className={s.fab}
        onClick={() => nav('/capture')}
        aria-label="拍照录入"
        data-testid="wrongbook.list.fab-capture"
      >
        <svg width="26" height="26" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <circle cx="12" cy="13" r="4.5" stroke="#fff" strokeWidth="1.8" />
          <path d="M5 8h3l1.5-2h5L16 8h3a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2Z"
                stroke="#fff" strokeWidth="1.8" strokeLinejoin="round" />
        </svg>
      </button>

      <nav className={s.tabbar} role="tablist">
        <button className={s.tab} role="tab" aria-selected={false} data-testid="wrongbook.list.tab-home">
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
    </div>
  );
};

const CardItem: React.FC<{ item: WrongItemVO; idx: number; onOpen: () => void }> = ({ item, idx, onOpen }) => {
  const m = masteryBucket(item.mastery);
  const pill = pillFor(m);
  const subColorCls = SUBJECT_COLOR[item.subject] ?? s.subBlue;
  const subjectLabel = SUBJECT_LABEL[item.subject] ?? item.subject;
  const stem = item.stem_text || '（无题干）';
  const tags = item.tags.slice(0, 4);
  const phase = Math.floor((item.mastery / 100) * 6);

  return (
    <button className={s.card} onClick={onOpen} data-testid={TEST_IDS.wrongbookList['item-card']}>
      <span className={`${s.leftBar} ${leftBarFor(m)}`} />
      <div className={s.thumb}>
        <span className={s.thumbQno}>{String(idx + 1).padStart(2, '0')}</span>
        <h3 className={s.thumbH3}>{stem.slice(0, 32)}</h3>
        <div className={s.thumbStrk} />
      </div>
      <div className={s.body}>
        <div className={s.bodyHead}>
          <span className={subColorCls}>{subjectLabel}</span>
          <span className={s.bodyDot} />
          <span>{tags[0] ?? '—'}</span>
          <span className={s.bodyDot} />
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
        <div className={s.stageBar}>
          {Array.from({ length: 6 }).map((_, i) => {
            const cls = i < phase ? s.sbDone : i === phase ? s.sbNow : '';
            return <span key={i} className={`${s.sb} ${cls}`} />;
          })}
        </div>
        <span className={s.due}>T{Math.max(1, phase)}</span>
      </div>
    </button>
  );
};
