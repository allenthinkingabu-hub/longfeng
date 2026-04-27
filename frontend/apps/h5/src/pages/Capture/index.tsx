// S7 · SC-01.AC-1 + SC-07.AC-1 + SC-11.AC-1 · 录入 · 对标 design/mockups/wrongbook/02_capture.html
import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { wrongbookClient, filesClient, WrongItemCreate } from '@longfeng/api-contracts';
import { TEST_IDS } from '@longfeng/testids';
import s from './Capture.module.css';

const MAX_BYTES = 10 * 1024 * 1024;
const DRAFT_KEY = 'lf:capture:draft:v1';
const DRAFT_TTL_MS = 7 * 24 * 3600 * 1000;

function safeStorageGet(key: string): string | null {
  try { return typeof localStorage !== 'undefined' ? localStorage.getItem(key) : null; } catch { return null; }
}
function safeStorageSet(key: string, value: string): void {
  try { if (typeof localStorage !== 'undefined') localStorage.setItem(key, value); } catch {}
}
function safeStorageRemove(key: string): void {
  try { if (typeof localStorage !== 'undefined') localStorage.removeItem(key); } catch {}
}

type Mode = 'photo' | 'manual';
type DraftShape = { subject: string; stem_text: string; image_file_key?: string; saved_at: number };

const SUBJECTS = [
  { value: 'math', label: '数学' },
  { value: 'physics', label: '物理' },
  { value: 'chemistry', label: '化学' },
  { value: 'english', label: '英语' },
  { value: 'chinese', label: '语文' },
];

export const CapturePage: React.FC = () => {
  const { t } = useTranslation();
  const nav = useNavigate();
  const [mode, setMode] = useState<Mode>('photo');
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
      const raw = safeStorageGet(DRAFT_KEY);
      if (!raw) return;
      const d = JSON.parse(raw) as DraftShape;
      if (Date.now() - d.saved_at > DRAFT_TTL_MS) {
        safeStorageRemove(DRAFT_KEY);
        return;
      }
      setValue('subject', d.subject);
      setValue('stem_text', d.stem_text);
      if (d.image_file_key) setFileKey(d.image_file_key);
      setBanner(t('capture.draft_restored'));
    } catch {
      safeStorageRemove(DRAFT_KEY);
    }
  }, [setValue, t]);

  useEffect(() => {
    return () => {
      const snap: DraftShape = {
        subject, stem_text: stem, image_file_key: fileKey, saved_at: Date.now(),
      };
      if (snap.stem_text || snap.image_file_key) {
        safeStorageSet(DRAFT_KEY, JSON.stringify(snap));
      }
    };
  }, [subject, stem, fileKey]);

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

  const submit = useMutation({
    mutationFn: (payload: WrongItemCreate) =>
      wrongbookClient.create({ ...payload, image_id: fileKey }),
    onSuccess: () => {
      safeStorageRemove(DRAFT_KEY);
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

  const triggerCamera = () => {
    if (fileInputRef.current) {
      fileInputRef.current.setAttribute('capture', 'environment');
      fileInputRef.current.click();
    }
  };
  const triggerGallery = () => {
    if (fileInputRef.current) {
      fileInputRef.current.removeAttribute('capture');
      fileInputRef.current.click();
    }
  };

  return (
    <div className={s.root} data-testid={TEST_IDS.capture.root}>
      <div className={s.nav}>
        <button className={s.iconBtn} onClick={() => nav(-1)} aria-label={t('common.back')} data-testid="capture.back">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M15 5l-7 7 7 7" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
        <div className={s.title}>{t('capture.title')}</div>
        <button className={s.iconBtn} aria-label="切换闪光" data-testid="capture.flash">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M13 2L4 14h7l-1 8 9-12h-7l1-8z" stroke="#fff" strokeWidth="1.8" strokeLinejoin="round" />
          </svg>
        </button>
      </div>

      {banner && <div className={s.banner}>{banner}</div>}
      {fileKey && <div className={s.fileOk} data-testid="capture.file-ok">✓ 已上传 {fileKey.slice(0, 8)}…</div>}
      {uploadPct !== null && (
        <div className={s.uploadProgress} data-testid={TEST_IDS.capture['upload-progress']}>
          {t('capture.upload_progress', { percent: uploadPct })}
        </div>
      )}

      <div className={s.view}>
        {fileKey ? (
          <div className={s.preview}>已识别 · 可在下方修正题干</div>
        ) : (
          <>
            <div className={s.detect}>
              <span className={s.detectPulse} />
              <span>自动检测中…</span>
            </div>
            <div className={s.tip}>
              <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M12 2v4M12 18v4M2 12h4M18 12h4M5 5l3 3M16 16l3 3M5 19l3-3M16 8l3-3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
              </svg>
              <span>对准题目 · 黄框自动取景 · 一拍即解</span>
            </div>
            <span className={`${s.bracket} ${s.bracketTL}`} aria-hidden="true" />
            <span className={`${s.bracket} ${s.bracketTR}`} aria-hidden="true" />
            <span className={`${s.bracket} ${s.bracketBL}`} aria-hidden="true" />
            <span className={`${s.bracket} ${s.bracketBR}`} aria-hidden="true" />
            <div className={s.scan} aria-hidden="true" />
          </>
        )}
      </div>

      <div className={s.dock}>
        {/* subject quick selector */}
        <div className={s.subjects} data-testid={TEST_IDS.capture.form.subject}>
          {SUBJECTS.map((sj) => (
            <button
              key={sj.value}
              className={`${s.subj} ${subject === sj.value ? s.subjOn : ''}`}
              onClick={() => setValue('subject', sj.value)}
              data-testid={`${TEST_IDS.capture.form.subject}.${sj.value}`}
            >
              {sj.label}
            </button>
          ))}
        </div>

        {/* mode tabs */}
        <div className={s.modes} role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'photo'}
            className={`${s.modeBtn} ${mode === 'photo' ? s.modeOn : ''}`}
            onClick={() => setMode('photo')}
            data-testid={TEST_IDS.capture.camera.btn}
          >
            照片
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'manual'}
            className={`${s.modeBtn} ${mode === 'manual' ? s.modeOn : ''}`}
            onClick={() => setMode('manual')}
            data-testid={TEST_IDS.capture.manual.btn}
          >
            手动
          </button>
        </div>

        {/* controls */}
        {mode === 'photo' && (
          <div className={s.controls}>
            <button className={s.controlBtn} onClick={triggerGallery} data-testid={TEST_IDS.capture.gallery.btn}>
              <span className={s.controlIc}>
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <rect x="3" y="5" width="18" height="14" rx="2" stroke="#fff" strokeWidth="1.8" />
                  <circle cx="8" cy="11" r="2" stroke="#fff" strokeWidth="1.8" />
                  <path d="m3 17 5-5 5 5 4-4 4 4" stroke="#fff" strokeWidth="1.8" />
                </svg>
              </span>
              <span>相册</span>
            </button>
            <button className={s.shutter} onClick={triggerCamera} aria-label="拍照" data-testid="capture.shutter">
              <div className={s.shutterCore}>
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <circle cx="12" cy="12" r="6" stroke="#fff" strokeWidth="2" />
                </svg>
              </div>
            </button>
            <button className={s.controlBtn} aria-label="光线" data-testid="capture.lighting">
              <span className={s.controlIc}>
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <circle cx="12" cy="12" r="4" stroke="#fff" strokeWidth="1.8" />
                  <path d="M12 2v3M12 19v3M2 12h3M19 12h3" stroke="#fff" strokeWidth="1.8" strokeLinecap="round" />
                </svg>
              </span>
              <span>补光</span>
            </button>
          </div>
        )}

        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          onChange={onFile}
          style={{ display: 'none' }}
          data-testid="capture.file-input"
        />

        {/* manual form */}
        {mode === 'manual' && (
          <form className={s.manualForm} onSubmit={handleSubmit((data) => submit.mutate(data))}>
            <div>
              <div className={s.fieldLabel}>{t('capture.field_subject')}</div>
              <input
                className={s.fieldInput}
                value={subject}
                onChange={(e) => setValue('subject', e.target.value)}
                data-testid={TEST_IDS.capture.form.subject}
              />
            </div>
            <div>
              <div className={s.fieldLabel}>{t('capture.field_stem')}</div>
              <textarea
                className={s.fieldTextarea}
                placeholder="请输入题干"
                {...register('stem_text', { required: true, minLength: 2 })}
                data-testid={TEST_IDS.capture.form.stem}
              />
            </div>
            <button
              type="submit"
              className={s.submitBtn}
              disabled={!stem || stem.length < 2 || submit.isPending}
              data-testid={TEST_IDS.capture.form.submit}
            >
              {submit.isPending ? '提交中…' : t('capture.submit')}
            </button>
            <div className={s.draftHint} data-testid={TEST_IDS.capture.form['draft-hint']}>
              {t('capture.draft_hint')}
            </div>
          </form>
        )}
      </div>
    </div>
  );
};
