// S7 · SC-01.AC-1 + SC-07.AC-1 · 录入 · 三入口 + 原图（≤10MB）+ 草稿 localStorage
import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { wrongbookClient, filesClient, WrongItemCreate } from '@longfeng/api-contracts';
import { NavBar, Input, Button, Banner, Picker, Progress, Tag } from '@longfeng/ui-kit';
import { TEST_IDS } from '@longfeng/testids';

const MAX_BYTES = 10 * 1024 * 1024;
const DRAFT_KEY = 'lf:capture:draft:v1';
const DRAFT_TTL_MS = 7 * 24 * 3600 * 1000;

type Tab = 'camera' | 'gallery' | 'manual';

type DraftShape = { subject: string; stem_text: string; image_file_key?: string; saved_at: number };

export const CapturePage: React.FC = () => {
  const { t } = useTranslation();
  const nav = useNavigate();
  const [tab, setTab] = useState<Tab>('manual');
  const [banner, setBanner] = useState<string | null>(null);
  const [uploadPct, setUploadPct] = useState<number | null>(null);
  const [fileKey, setFileKey] = useState<string | undefined>();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const { register, handleSubmit, setValue, watch, reset } = useForm<WrongItemCreate>({
    defaultValues: { subject: 'math', stem_text: '', tags: [] },
  });

  const subject = watch('subject');
  const stem = watch('stem_text');

  // 恢复草稿
  useEffect(() => {
    try {
      const raw = localStorage.getItem(DRAFT_KEY);
      if (!raw) return;
      const d = JSON.parse(raw) as DraftShape;
      if (Date.now() - d.saved_at > DRAFT_TTL_MS) {
        localStorage.removeItem(DRAFT_KEY);
        return;
      }
      setValue('subject', d.subject);
      setValue('stem_text', d.stem_text);
      if (d.image_file_key) setFileKey(d.image_file_key);
      setBanner(t('capture.draft_restored'));
    } catch {
      localStorage.removeItem(DRAFT_KEY);
    }
  }, [setValue, t]);

  // 离页 / 字段变 · 保存草稿
  useEffect(() => {
    return () => {
      const snap: DraftShape = {
        subject,
        stem_text: stem,
        image_file_key: fileKey,
        saved_at: Date.now(),
      };
      if (snap.stem_text || snap.image_file_key) {
        localStorage.setItem(DRAFT_KEY, JSON.stringify(snap));
      }
    };
  }, [subject, stem, fileKey]);

  // 上传 · presign → PUT OSS → complete
  const upload = useMutation({
    mutationFn: async (file: File) => {
      if (file.size > MAX_BYTES) throw new Error('SIZE');
      setUploadPct(0);
      const presign = await filesClient.presign({ mime: file.type, size: file.size });
      setUploadPct(10);
      await filesClient.directUpload(presign.upload_url, file);
      setUploadPct(80);
      await filesClient.complete(presign.file_key);
      setUploadPct(100);
      return presign.file_key;
    },
    onSuccess: (key) => {
      setFileKey(key);
      setTimeout(() => setUploadPct(null), 400);
    },
    onError: (e: Error) => {
      setUploadPct(null);
      setBanner(e.message === 'SIZE' ? t('capture.size_exceeded') : t('capture.ocr_fallback'));
    },
  });

  // 提交
  const submit = useMutation({
    mutationFn: (payload: WrongItemCreate) =>
      wrongbookClient.create({ ...payload, image_id: fileKey }),
    onSuccess: () => {
      localStorage.removeItem(DRAFT_KEY);
      reset();
      setFileKey(undefined);
      nav('/wrongbook');
    },
  });

  const onFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (!f) return;
    upload.mutate(f);
  };

  return (
    <div data-testid={TEST_IDS.capture.root}>
      <NavBar title={t('capture.title')} onBack={() => nav(-1)} testIdPrefix="capture" />

      {banner && <Banner type="info" message={banner} closable onClose={() => setBanner(null)} testIdPrefix="capture" />}
      {uploadPct !== null && (
        <div style={{ padding: '8px 12px' }}>
          <Progress
            value={uploadPct}
            label={t('capture.upload_progress', { percent: uploadPct })}
            testIdPrefix={TEST_IDS.capture['upload-progress']}
          />
        </div>
      )}
      {fileKey && (
        <div style={{ padding: '4px 12px' }}>
          <Tag color="success">✓ {fileKey.slice(0, 8)}…</Tag>
        </div>
      )}

      {/* Tab 切入口 */}
      <div role="tablist" style={{ display: 'flex', gap: 8, padding: 12 }}>
        {(['camera', 'gallery', 'manual'] as Tab[]).map((tk) => (
          <button
            key={tk}
            role="tab"
            aria-selected={tab === tk}
            data-testid={TEST_IDS.capture[tk].btn}
            onClick={() => {
              setTab(tk);
              if (tk === 'camera' || tk === 'gallery') fileInputRef.current?.click();
            }}
            style={{
              flex: 1,
              minHeight: 44,
              background: tab === tk ? 'var(--tkn-color-primary-default)' : 'var(--tkn-color-button-default-light)',
              color: tab === tk ? 'var(--tkn-color-white)' : 'var(--tkn-color-text-primary)',
              border: 'none',
              borderRadius: 8,
              cursor: 'pointer',
            }}
          >
            {t(`capture.tab_${tk}`)}
          </button>
        ))}
      </div>

      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        capture={tab === 'camera' ? 'environment' : undefined}
        onChange={onFile}
        style={{ display: 'none' }}
      />

      <form
        onSubmit={handleSubmit((data) => submit.mutate(data))}
        style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 12 }}
      >
        <Picker
          label={t('capture.field_subject')}
          value={subject}
          onChange={(v) => setValue('subject', v)}
          options={[
            { label: 'math', value: 'math' },
            { label: 'physics', value: 'physics' },
            { label: 'chemistry', value: 'chemistry' },
            { label: 'english', value: 'english' },
          ]}
          testIdPrefix={TEST_IDS.capture.form.subject}
        />
        <Input
          placeholder={t('capture.field_stem')}
          testIdPrefix={TEST_IDS.capture.form.stem}
          {...register('stem_text', { required: true, minLength: 2 })}
        />
        <Button
          type="submit"
          variant="primary"
          block
          loading={submit.isPending}
          disabled={!stem || stem.length < 2}
          testIdPrefix={TEST_IDS.capture.form.submit}
        >
          {t('capture.submit')}
        </Button>
        <div data-testid={TEST_IDS.capture.form['draft-hint']} style={{ fontSize: 12, color: 'var(--tkn-color-text-secondary)' }}>
          {t('capture.draft_hint')}
        </div>
      </form>
    </div>
  );
};
