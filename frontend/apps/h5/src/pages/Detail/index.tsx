// S7 · SC-02.AC-1 + SC-03.AC-1 · 错题详情 · AI 讲解 SSE + Tag 编辑 + 相似题
import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { wrongbookClient, analysisClient, WrongItemVO, ExplainChunk } from '@longfeng/api-contracts';
import { NavBar, Card, Tag, Sheet, Input, Button, Divider, Skeleton, Banner } from '@longfeng/ui-kit';
import { TEST_IDS } from '@longfeng/testids';

const SUBJECT_POOL = ['math', 'physics', 'chemistry', 'english'];

export const DetailPage: React.FC = () => {
  const { id = '' } = useParams();
  const { t } = useTranslation();
  const nav = useNavigate();
  const qc = useQueryClient();

  const { data: item, isLoading } = useQuery({
    queryKey: ['wrongbook', 'item', id],
    queryFn: () => wrongbookClient.get(id),
    enabled: !!id,
  });

  const { data: similar } = useQuery({
    queryKey: ['analysis', 'similar', id],
    queryFn: () => analysisClient.similar(id, 3),
    enabled: !!id && item?.status === 'completed',
  });

  // SSE 讲解流
  const [explain, setExplain] = useState('');
  const [streamError, setStreamError] = useState<string | null>(null);
  const closeRef = useRef<(() => void) | null>(null);
  useEffect(() => {
    if (!id || item?.status !== 'completed') return;
    setExplain('');
    setStreamError(null);
    closeRef.current = analysisClient.explainStream(
      id,
      (chunk: ExplainChunk) => setExplain((p) => p + chunk.chunk),
      () => setStreamError(t('wrongbook_detail.explain_error')),
    );
    return () => closeRef.current?.();
  }, [id, item?.status, t]);

  // Tag 编辑
  const [sheetOpen, setSheetOpen] = useState(false);
  const [localTags, setLocalTags] = useState<string[]>([]);
  const [custom, setCustom] = useState('');
  useEffect(() => {
    if (item) setLocalTags(item.tags);
  }, [item]);

  const saveTags = useMutation({
    mutationFn: () => {
      if (!item) throw new Error('no item');
      return wrongbookClient.updateTags(id, { tags: localTags, version: item.version });
    },
    onSuccess: () => {
      setSheetOpen(false);
      qc.invalidateQueries({ queryKey: ['wrongbook', 'item', id] });
    },
  });

  const toggleTag = (tag: string) => {
    setLocalTags((p) => (p.includes(tag) ? p.filter((x) => x !== tag) : [...p, tag]));
  };

  const addCustom = () => {
    if (!custom.trim()) return;
    const customTags = localTags.filter((t) => !SUBJECT_POOL.includes(t));
    if (customTags.length >= 5) return;
    setLocalTags((p) => [...p, custom.trim()]);
    setCustom('');
  };

  if (isLoading) {
    return (
      <div style={{ padding: 16 }}>
        <Skeleton height={120} />
      </div>
    );
  }
  if (!item) return null;

  return (
    <div data-testid={TEST_IDS.wrongbookDetail.root}>
      <NavBar title={t('wrongbook_detail.title')} onBack={() => nav(-1)} testIdPrefix="wrongbook.detail" />

      {/* 题干 · 图 · 标签 */}
      <main style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 12 }}>
        <Card>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 8 }}>
            <Tag color="primary">{item.subject}</Tag>
            {item.tags.map((t) => (
              <Tag key={t}>{t}</Tag>
            ))}
            <button
              onClick={() => setSheetOpen(true)}
              data-testid={TEST_IDS.wrongbookDetail['tag-sheet']}
              style={{
                minHeight: 28,
                padding: '2px 8px',
                fontSize: 12,
                background: 'var(--tkn-color-button-default-light)',
                border: 'none',
                borderRadius: 4,
                cursor: 'pointer',
              }}
            >
              {t('wrongbook_detail.tag_edit')}
            </button>
          </div>
          <div data-testid={TEST_IDS.wrongbookDetail['stem-text']} style={{ fontSize: 15, lineHeight: 1.6 }}>
            {item.stem_text}
          </div>
          {item.image_url && (
            <img
              data-testid={TEST_IDS.wrongbookDetail['image-view']}
              src={item.image_url}
              alt=""
              style={{ width: '100%', borderRadius: 8, marginTop: 8 }}
            />
          )}
        </Card>

        <Divider text={t('wrongbook_detail.explain_title')} />
        <Card>
          {streamError && <Banner type="error" message={streamError} testIdPrefix="wrongbook.detail.explain" />}
          <div
            data-testid={TEST_IDS.wrongbookDetail['explain-stream']}
            aria-live="polite"
            style={{ fontSize: 14, lineHeight: 1.8, whiteSpace: 'pre-wrap' }}
          >
            {explain || t('wrongbook_detail.explain_loading')}
          </div>
        </Card>

        {similar?.items && similar.items.length > 0 && (
          <>
            <Divider text={t('wrongbook_detail.similar_title')} />
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {similar.items.map((s) => (
                <Card
                  key={s.id}
                  testIdPrefix={TEST_IDS.wrongbookDetail['similar-card']}
                  padding={12}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                    <Tag color="primary">{s.subject}</Tag>
                    <span style={{ fontSize: 12, color: 'var(--tkn-color-text-secondary)' }}>
                      d={s.distance.toFixed(2)}
                    </span>
                  </div>
                  <div style={{ fontSize: 14 }}>{s.stem_text.slice(0, 80)}</div>
                </Card>
              ))}
            </div>
          </>
        )}
      </main>

      {/* Tag 编辑 Sheet */}
      <Sheet
        open={sheetOpen}
        onClose={() => setSheetOpen(false)}
        title={t('wrongbook_detail.tag_sheet_title')}
        testIdPrefix="wrongbook.detail.tag-sheet"
      >
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
          {SUBJECT_POOL.map((tk) => {
            const active = localTags.includes(tk);
            return (
              <button
                key={tk}
                data-testid={TEST_IDS.wrongbookDetail['tag-chip']}
                onClick={() => toggleTag(tk)}
                style={{
                  minHeight: 32,
                  padding: '4px 12px',
                  border: `1px solid ${active ? 'var(--tkn-color-primary-default)' : 'var(--tkn-color-button-default-light)'}`,
                  background: active ? 'var(--tkn-color-primary-default)' : 'transparent',
                  color: active ? 'var(--tkn-color-white)' : 'var(--tkn-color-text-primary)',
                  borderRadius: 16,
                  fontSize: 13,
                  cursor: 'pointer',
                }}
              >
                {tk}
              </button>
            );
          })}
        </div>
        <Input
          placeholder={t('wrongbook_detail.tag_custom_placeholder')}
          value={custom}
          onChange={(e) => setCustom(e.target.value)}
          testIdPrefix={TEST_IDS.wrongbookDetail['tag-custom-input']}
        />
        <div style={{ marginTop: 8 }}>
          <Button variant="secondary" onClick={addCustom} testIdPrefix="wrongbook.detail.tag-add">
            +
          </Button>
        </div>
        <div style={{ marginTop: 12 }}>
          <Button
            variant="primary"
            block
            onClick={() => saveTags.mutate()}
            loading={saveTags.isPending}
            testIdPrefix={TEST_IDS.wrongbookDetail['tag-save']}
          >
            {t('wrongbook_detail.tag_save')}
          </Button>
        </div>
      </Sheet>
    </div>
  );
};
