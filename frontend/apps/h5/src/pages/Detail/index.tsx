// S7 · SC-02.AC-1 + SC-03.AC-1 + SC-04.AC-1 · 错题详情 · 对标 design/mockups/wrongbook/06_wrongbook_detail.html
import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { wrongbookClient, analysisClient, ExplainChunk } from '@longfeng/api-contracts';
import { TEST_IDS } from '@longfeng/testids';
import s from './Detail.module.css';

const SUBJECT_POOL = ['math', 'physics', 'chemistry', 'english'];
const SUBJECT_LABEL: Record<string, string> = {
  math: '数学', physics: '物理', chemistry: '化学', english: '英语',
};

export const DetailPage: React.FC = () => {
  const { id = '' } = useParams();
  const { t } = useTranslation();
  const nav = useNavigate();
  const qc = useQueryClient();
  const [stab, setStab] = useState<'explain' | 'plan'>('explain');

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
    const customTags = localTags.filter((tg) => !SUBJECT_POOL.includes(tg));
    if (customTags.length >= 5) return;
    setLocalTags((p) => [...p, custom.trim()]);
    setCustom('');
  };

  // SC-04 软删除
  const [confirmDelete, setConfirmDelete] = useState(false);
  const softDelete = useMutation({
    mutationFn: () => wrongbookClient.softDelete(id),
    onSuccess: () => {
      setConfirmDelete(false);
      qc.invalidateQueries({ queryKey: ['wrongbook'] });
      nav('/wrongbook', { replace: true });
    },
  });

  if (isLoading || !item) {
    return (
      <div className={s.root} data-testid={TEST_IDS.wrongbookDetail.root}>
        <div className={s.content}><div className={s.explainPlaceholder}>{t('common.loading')}…</div></div>
      </div>
    );
  }

  const masteryLabel = item.mastery < 40 ? '未掌握' : item.mastery < 70 ? '部分' : '已掌握';
  const subjectLabel = SUBJECT_LABEL[item.subject] ?? item.subject;

  return (
    <div className={s.root} data-testid={TEST_IDS.wrongbookDetail.root}>
      <div className={s.nav}>
        <div className={s.navRow}>
          <button className={s.back} onClick={() => nav(-1)} data-testid="wrongbook.detail.back">
            <svg width="12" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M15 5l-7 7 7 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            <span>{t('common.back')}</span>
          </button>
          <div className={s.navRight}>
            <button
              className={`${s.navIcon} ${s.navIconDanger}`}
              onClick={() => setConfirmDelete(true)}
              aria-label={t('common.delete')}
              data-testid={TEST_IDS.wrongbookDetail.delete.btn}
            >
              {t('common.delete')}
            </button>
          </div>
        </div>
        <h1 className={s.navTitle}>
          {t('wrongbook_detail.title')}
          <span className={s.pillRed}>{masteryLabel}</span>
        </h1>
      </div>

      <div className={s.content}>
        <div className={s.imgCard}>
          {item.image_url ? (
            <img src={item.image_url} alt="" data-testid={TEST_IDS.wrongbookDetail['image-view']} />
          ) : (
            <div className={s.imgPlaceholder}>{item.stem_text}</div>
          )}
          <div className={s.imgBadge}>{subjectLabel}</div>
        </div>

        <div className={s.stab} role="tablist">
          <button role="tab" aria-selected={stab === 'explain'} className={stab === 'explain' ? s.on : ''} onClick={() => setStab('explain')}>
            AI 讲解
          </button>
          <button role="tab" aria-selected={stab === 'plan'} className={stab === 'plan' ? s.on : ''} onClick={() => setStab('plan')}>
            复习计划
          </button>
        </div>

        <div className={s.brief}>
          <div className={s.kicker}>{subjectLabel} · {item.tags[0] ?? '错题详情'}</div>
          <div className={s.tagRow}>
            <span className={`${s.chip} ${s.chipPrimary}`}>{subjectLabel}</span>
            {item.tags.map((tg) => (
              <span key={tg} className={`${s.chip} ${s.chipKp}`}>{tg}</span>
            ))}
            <button className={s.tagEdit} onClick={() => setSheetOpen(true)} data-testid={TEST_IDS.wrongbookDetail['tag-sheet']}>
              {t('wrongbook_detail.tag_edit')}
            </button>
          </div>
          <div className={s.stem} data-testid={TEST_IDS.wrongbookDetail['stem-text']}>
            {item.stem_text}
          </div>
        </div>

        {stab === 'explain' && (
          <>
            <div className={s.sec}>
              <h3>{t('wrongbook_detail.explain_title')}</h3>
              <span className={s.secLine} />
            </div>
            <div className={s.explainCard} data-testid={TEST_IDS.wrongbookDetail['explain-stream']} aria-live="polite">
              {streamError ? (
                <span style={{ color: '#FF3B30' }}>{streamError}</span>
              ) : explain || (
                <span className={s.explainPlaceholder}>{t('wrongbook_detail.explain_loading')}</span>
              )}
            </div>

            {similar?.items && similar.items.length > 0 && (
              <>
                <div className={s.sec}>
                  <h3>{t('wrongbook_detail.similar_title')}</h3>
                  <span className={s.secLine} />
                </div>
                <div className={s.similarList}>
                  {similar.items.map((sim) => (
                    <div key={sim.id} className={s.similarCard} data-testid={TEST_IDS.wrongbookDetail['similar-card']}>
                      <div className={s.similarHead}>
                        <span className={s.kicker}>{SUBJECT_LABEL[sim.subject] ?? sim.subject}</span>
                        <span className={s.distance}>d={sim.distance.toFixed(2)}</span>
                      </div>
                      <div className={s.similarStem}>{sim.stem_text.slice(0, 80)}</div>
                    </div>
                  ))}
                </div>
              </>
            )}
          </>
        )}

        {stab === 'plan' && (
          <div className={s.brief}>
            <div className={s.kicker}>艾宾浩斯节点 · 7 天 · 14 天 · 30 天</div>
            <div className={s.stem}>掌握度 {item.mastery}% · 下次复习时间见列表 / 日历</div>
          </div>
        )}
      </div>

      <div className={s.cta}>
        <button className={s.btnGhost} onClick={() => setSheetOpen(true)}>{t('wrongbook_detail.tag_edit')}</button>
        <button className={s.btnPrimary} data-testid={TEST_IDS.wrongbookDetail['review-entry']}>
          {t('wrongbook_detail.review_entry')}
        </button>
      </div>

      {sheetOpen && (
        <div className={s.sheetBackdrop} onClick={() => setSheetOpen(false)}>
          <div className={s.sheet} onClick={(e) => e.stopPropagation()} role="dialog" aria-label={t('wrongbook_detail.tag_sheet_title')}>
            <div className={s.sheetTitle}>{t('wrongbook_detail.tag_sheet_title')}</div>
            <div className={s.sheetChips}>
              {SUBJECT_POOL.map((tg) => {
                const active = localTags.includes(tg);
                return (
                  <button
                    key={tg}
                    className={`${s.chip} ${active ? s.chipPrimary : ''}`}
                    onClick={() => toggleTag(tg)}
                    data-testid={TEST_IDS.wrongbookDetail['tag-chip']}
                  >
                    {SUBJECT_LABEL[tg]}
                  </button>
                );
              })}
            </div>
            <input
              className={s.sheetInput}
              placeholder={t('wrongbook_detail.tag_custom_placeholder')}
              value={custom}
              onChange={(e) => setCustom(e.target.value)}
              data-testid={TEST_IDS.wrongbookDetail['tag-custom-input']}
            />
            <button className={s.btnGhost} onClick={addCustom} style={{ width: '100%' }}>+ 添加</button>
            <div className={s.sheetActions}>
              <button className={s.btnGhost} onClick={() => setSheetOpen(false)}>{t('common.cancel')}</button>
              <button
                className={s.btnPrimary}
                onClick={() => saveTags.mutate()}
                disabled={saveTags.isPending}
                data-testid={TEST_IDS.wrongbookDetail['tag-save']}
              >
                {saveTags.isPending ? '…' : t('wrongbook_detail.tag_save')}
              </button>
            </div>
          </div>
        </div>
      )}

      {confirmDelete && (
        <div className={s.modalBackdrop} onClick={() => setConfirmDelete(false)}>
          <div className={s.modal} onClick={(e) => e.stopPropagation()} role="dialog">
            <div className={s.modalTitle}>确认删除</div>
            <div className={s.modalBody}>删除后此错题 7 天内可在「设置 → 回收站」恢复，之后永久清除。</div>
            <div className={s.modalFooter}>
              <button className={s.btnGhost} onClick={() => setConfirmDelete(false)} data-testid={TEST_IDS.wrongbookDetail.delete.cancel}>
                {t('common.cancel')}
              </button>
              <button
                className={s.btnPrimary}
                style={{ background: 'linear-gradient(180deg,#FF6B5E,#FF3B30)', boxShadow: '0 10px 24px rgba(255,59,48,.28)' }}
                onClick={() => softDelete.mutate()}
                disabled={softDelete.isPending}
                data-testid={TEST_IDS.wrongbookDetail.delete.confirm}
              >
                {softDelete.isPending ? '…' : t('common.delete')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
