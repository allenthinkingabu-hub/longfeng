// S7 · SC-08.AC-1 · 错题列表 · Active/Mastered Tab · cursor 分页 · 轮询 analyzing 条目
import React, { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useInfiniteQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { wrongbookClient, WrongItemVO } from '@longfeng/api-contracts';
import { NavBar, Card, Tag, Skeleton, Empty, Picker, Button, Badge } from '@longfeng/ui-kit';
import { TEST_IDS } from '@longfeng/testids';

type StatusTab = 'active' | 'mastered';

export const ListPage: React.FC = () => {
  const { t } = useTranslation();
  const nav = useNavigate();
  const [status, setStatus] = useState<StatusTab>('active');
  const [subject, setSubject] = useState<string>('');

  const query = useInfiniteQuery({
    queryKey: ['wrongbook', status, { subject }],
    queryFn: ({ pageParam }) =>
      wrongbookClient.list({
        status,
        subject: subject || undefined,
        cursor: pageParam as string | undefined,
        limit: 20,
      }),
    initialPageParam: undefined,
    getNextPageParam: (last) => (last.has_more ? last.next_cursor : undefined),
    refetchInterval: (q) => {
      const pages = q.state.data?.pages ?? [];
      const hasAnalyzing = pages.some((p) => p.items.some((i) => i.status === 'analyzing'));
      return hasAnalyzing ? 3000 : false;
    },
  });

  const items = useMemo(() => query.data?.pages.flatMap((p) => p.items) ?? [], [query.data]);

  return (
    <div data-testid={TEST_IDS.wrongbookList.root}>
      <NavBar title={t('wrongbook_list.title')} testIdPrefix="wrongbook.list" />

      {/* 顶部 Tab · active/mastered */}
      <div
        role="tablist"
        style={{
          display: 'flex',
          padding: '8px 12px',
          gap: 8,
          borderBottom: '1px solid var(--tkn-color-button-default-light)',
        }}
      >
        <button
          role="tab"
          aria-selected={status === 'active'}
          data-testid={TEST_IDS.wrongbookList['active-tab']}
          onClick={() => setStatus('active')}
          style={tabStyle(status === 'active')}
        >
          {t('wrongbook_list.tab_active')}
        </button>
        <button
          role="tab"
          aria-selected={status === 'mastered'}
          data-testid={TEST_IDS.wrongbookList['archive-tab']}
          onClick={() => setStatus('mastered')}
          style={tabStyle(status === 'mastered')}
        >
          {t('wrongbook_list.tab_mastered')}
        </button>
      </div>

      {/* 筛选栏 */}
      <div data-testid={TEST_IDS.wrongbookList['filter-bar']} style={{ padding: '8px 12px' }}>
        <Picker
          label={t('wrongbook_list.filter_subject')}
          value={subject || 'all'}
          onChange={(v) => setSubject(v === 'all' ? '' : v)}
          options={[
            { label: t('wrongbook_list.filter_all'), value: 'all' },
            { label: 'math', value: 'math' },
            { label: 'physics', value: 'physics' },
            { label: 'chemistry', value: 'chemistry' },
            { label: 'english', value: 'english' },
          ]}
          testIdPrefix={TEST_IDS.wrongbookList['filter-subject']}
        />
      </div>

      {/* 列表 */}
      <main style={{ padding: '8px 12px 80px', display: 'flex', flexDirection: 'column', gap: 8 }}>
        {query.isLoading && (
          <>
            <Skeleton height={80} testIdPrefix={TEST_IDS.wrongbookList.skeleton} />
            <Skeleton height={80} testIdPrefix={TEST_IDS.wrongbookList.skeleton} />
            <Skeleton height={80} testIdPrefix={TEST_IDS.wrongbookList.skeleton} />
          </>
        )}

        {!query.isLoading && items.length === 0 && (
          <Empty
            title={status === 'active' ? t('wrongbook_list.empty_active') : t('wrongbook_list.empty_mastered')}
            testIdPrefix={TEST_IDS.wrongbookList.empty}
          />
        )}

        {items.map((it) => (
          <ItemCard key={it.id} item={it} onOpen={() => nav(`/wrongbook/${it.id}`)} />
        ))}

        {query.hasNextPage && (
          <Button
            variant="secondary"
            onClick={() => query.fetchNextPage()}
            loading={query.isFetchingNextPage}
            testIdPrefix={TEST_IDS.wrongbookList['load-more']}
          >
            {t('wrongbook_list.load_more')}
          </Button>
        )}
      </main>
    </div>
  );
};

const ItemCard: React.FC<{ item: WrongItemVO; onOpen: () => void }> = ({ item, onOpen }) => {
  const { t } = useTranslation();
  const statusLabel = {
    analyzing: t('wrongbook_list.item_analyzing'),
    completed: t('wrongbook_list.item_completed'),
    error: t('wrongbook_list.item_error'),
    pending: t('wrongbook_list.item_analyzing'),
  }[item.status];
  const statusColor = item.status === 'completed' ? 'success' : item.status === 'error' ? 'danger' : 'warning';

  return (
    <button
      data-testid={TEST_IDS.wrongbookList['item-card']}
      onClick={onOpen}
      style={{
        all: 'unset',
        cursor: 'pointer',
        display: 'block',
      }}
    >
      <Card>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 6 }}>
          <Tag color="primary">{item.subject}</Tag>
          <Tag color={statusColor as 'success' | 'danger' | 'warning'}>{statusLabel}</Tag>
          {item.tags.slice(0, 2).map((tg) => (
            <Tag key={tg}>{tg}</Tag>
          ))}
          {item.mastery >= 80 && (
            <Badge count={item.mastery} max={100}>
              <span />
            </Badge>
          )}
        </div>
        <div
          style={{
            fontSize: 14,
            lineHeight: 1.5,
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
          }}
        >
          {item.stem_text}
        </div>
      </Card>
    </button>
  );
};

function tabStyle(active: boolean): React.CSSProperties {
  return {
    flex: 1,
    minHeight: 44,
    padding: '8px 12px',
    background: active ? 'var(--tkn-color-primary-default)' : 'transparent',
    color: active ? 'var(--tkn-color-white)' : 'var(--tkn-color-text-primary)',
    border: '1px solid var(--tkn-color-primary-default)',
    borderRadius: 8,
    cursor: 'pointer',
    fontSize: 15,
    fontWeight: active ? 600 : 400,
  };
}
