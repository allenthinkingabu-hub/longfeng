// S7 · testid 常量包 · design/arch/s7-frontend-wrongbook.md §3.2
// 命名规约：<screen>.<region>.<element>[-{variant}] · 三段 kebab-case
// 见 design/system/testid-convention.md · 双端同名（H5 data-testid / miniapp data-test-id）

export const TEST_IDS = {
  // P03 Capture · SC-01 + SC-07
  capture: {
    root: 'capture.root',
    form: {
      submit: 'capture.form.submit',
      subject: 'capture.form.subject',
      stem: 'capture.form.stem',
      'draft-hint': 'capture.form.draft-hint',
      tags: 'capture.form.tags',
    },
    camera: { btn: 'capture.camera.btn' },
    gallery: { btn: 'capture.gallery.btn' },
    manual: { btn: 'capture.manual.btn' },
    'size-exceeded': 'capture.size-exceeded.toast',
    'ocr-fallback': 'capture.ocr-fallback.banner',
    'upload-progress': 'capture.upload-progress',
  },
  // P06 List · SC-08
  wrongbookList: {
    root: 'wrongbook.list.root',
    'filter-bar': 'wrongbook.list.filter-bar',
    'filter-subject': 'wrongbook.list.filter-subject',
    'filter-tag': 'wrongbook.list.filter-tag',
    'filter-difficulty': 'wrongbook.list.filter-difficulty',
    'item-card': 'wrongbook.list.item-card',
    'active-tab': 'wrongbook.list.active-tab',
    'archive-tab': 'wrongbook.list.archive-tab',
    'load-more': 'wrongbook.list.load-more',
    empty: 'wrongbook.list.empty',
    skeleton: 'wrongbook.list.skeleton',
  },
  // P07 Detail · SC-02 + SC-03 + SC-04
  wrongbookDetail: {
    root: 'wrongbook.detail.root',
    'stem-text': 'wrongbook.detail.stem-text',
    'image-view': 'wrongbook.detail.image-view',
    'tag-sheet': 'wrongbook.detail.tag-sheet',
    'tag-chip': 'wrongbook.detail.tag-chip',
    'tag-custom-input': 'wrongbook.detail.tag-custom-input',
    'tag-save': 'wrongbook.detail.tag-save',
    'explain-stream': 'wrongbook.detail.explain-stream',
    'cause-chip': 'wrongbook.detail.cause-chip',
    'similar-card': 'wrongbook.detail.similar-card',
    'review-entry': 'wrongbook.detail.review-entry',
    delete: {
      btn: 'wrongbook.detail.delete.btn',
      confirm: 'wrongbook.detail.delete.confirm',
      cancel: 'wrongbook.detail.delete.cancel',
    },
  },
  // 通用
  common: {
    back: 'common.back.btn',
    'error-banner': 'common.error.banner',
    'confirm-modal': 'common.confirm.modal',
  },
} as const;

/** 工具：取叶子值（deep-flatten 枚举 · ESLint 规则消费）. */
export type TestIdValue = typeof TEST_IDS extends infer T ? ExtractLeafValues<T> : never;
type ExtractLeafValues<T> = T extends string
  ? T
  : T extends object
  ? { [K in keyof T]: ExtractLeafValues<T[K]> }[keyof T]
  : never;
