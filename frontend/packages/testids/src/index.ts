// S7 · testid 常量包 · design/arch/s7-frontend-wrongbook.md §3.2
// 命名规约：<screen>.<region>.<element>[-{variant}] · 三段 kebab-case
// 见 design/system/testid-convention.md · 双端同名（H5 data-testid / miniapp data-test-id）

export const TEST_IDS = {
  // P02 Capture · SC-01 · Mood C dark-camera
  p02: {
    root: 'p02-root',
    topbar: 'p02-topbar',
    topbarBack: 'p02-topbar-back',
    topbarFlash: 'p02-topbar-flash-btn',
    viewfinder: 'p02-viewfinder',
    detectBadge: 'p02-detect-badge',
    tipCard: 'p02-tip-card',
    paper: 'p02-paper',
    subjects: 'p02-subjects',
    subjectMath: 'p02-subject-math',
    subjectPhysics: 'p02-subject-physics',
    subjectChemistry: 'p02-subject-chemistry',
    subjectEnglish: 'p02-subject-english',
    shutter: 'p02-shutter-btn',
    gallery: 'p02-gallery-btn',
    modes: 'p02-mode-tabs',
    modePhoto: 'p02-mode-tabs-tab-1',
    modeMulti: 'p02-mode-tabs-tab-2',
    modeFile: 'p02-mode-tabs-tab-3',
    uploadProgress: 'p02-upload-progress',
    errorBanner: 'p02-error-banner',
  },

  // P03 Analyzing · SSE 4-step pipeline
  p03: {
    root: 'p03-root',
    statusbar: 'p03-statusbar',
    thumbCard: 'p03-thumb-card',
    thumbImage: 'p03-thumb-card-image',
    thumbTitle: 'p03-thumb-card-title',
    modelBadge: 'analyzing-pipeline-model-badge',
    pipeline: 'analyzing-pipeline',
    step1: 'analyzing-pipeline-step-1',
    step2: 'analyzing-pipeline-step-2',
    step3: 'analyzing-pipeline-step-3',
    step4: 'analyzing-pipeline-step-4',
    jsonStream: 'analyzing-pipeline-json-stream',
    cancelBtn: 'analyzing-pipeline-cancel-btn',
    fallbackBanner: 'p03-fallback-banner',
    slowBanner: 'p03-slow-banner',
  },

  // P04 Result · Mood B pure-warm
  p04: {
    root: 'p04-root',
    navbar: 'p04-navbar',
    questionHero: 'p04-question-hero',
    answersRow: 'p04-answers-row',
    answersWrong: 'p04-answers-row-wrong',
    answersRight: 'p04-answers-row-right',
    answersWrongText: 'p04-answers-row-wrong-text',
    answersRightText: 'p04-answers-row-right-text',
    reasonCard: 'p04-reason-card',
    reasonText: 'p04-reason-card-text',
    solutionStepper: 'p04-solution-stepper',
    step1: 'p04-solution-stepper-step-1',
    step2: 'p04-solution-stepper-step-2',
    step3: 'p04-solution-stepper-step-3',
    metaChips: 'p04-meta-chips',
    subjectChipMath: 'subject-chip-math',
    memoryCurve: 'memory-curve',
    memCurveT1: 'memory-curve-node-T1',
    memCurveT2: 'memory-curve-node-T2',
    memCurveT3: 'memory-curve-node-T3',
    memCurveT4: 'memory-curve-node-T4',
    memCurveT5: 'memory-curve-node-T5',
    memCurveT6: 'memory-curve-node-T6',
    saveCta: 'p04-save-cta',
    lowConfBanner: 'p04-low-conf-banner',
    skeleton: 'p04-skeleton',
  },

  // P03 Capture (legacy key kept for backward compat) · SC-01 + SC-07
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
    'tabbar-wrongbook': 'wrongbook.list.tabbar-wrongbook',
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
