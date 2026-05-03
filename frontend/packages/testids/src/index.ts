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

  // ── S7 FE-01 · Shells & Bootstrap ──────────────────────────────────────────

  // AnonymousShell · 匿名 Shell
  anonShell: {
    root:      'anon-shell',
    nav:       'anon-shell-nav',
    logo:      'anon-shell-logo',
    loginBtn:  'anon-shell-login-btn',
    outlet:    'anon-shell-outlet',
  },

  // TabShell · 已登录主 Shell（5 Tab）
  tabShell: {
    root:    'tab-shell',
    tabbar:  'tab-shell-tabbar',
    outlet:  'tab-shell-outlet',
    tabs: {
      home:      'tab-home',
      wrongbook: 'tab-wrongbook',
      capture:   'tab-capture',
      review:    'tab-review',
      me:        'tab-me',
    },
    badges: {
      review: 'tab-review-badge',
    },
  },

  // ObserverShell · 观察者 Shell（scope=READ · C4 红线）
  observerShell: {
    root:          'observer-shell',
    watermark:     'observer-watermark',
    nav:           'observer-shell-nav',
    backBtn:       'observer-back-btn',
    exitBtn:       'observer-exit-btn',
    identityCard:  'observer-identity-card',
    scopeBadge:    'observer-scope-badge',
    outlet:        'observer-shell-outlet',
    ghostTabs: {
      home:      'observer-ghost-tab-home',
      wrongbook: 'observer-ghost-tab-wrongbook',
      capture:   'observer-ghost-tab-capture',
      review:    'observer-ghost-tab-review',
      me:        'observer-ghost-tab-me',
    },
  },
} as const;

/** 工具：取叶子值（deep-flatten 枚举 · ESLint 规则消费）. */
export type TestIdValue = typeof TEST_IDS extends infer T ? ExtractLeafValues<T> : never;
type ExtractLeafValues<T> = T extends string
  ? T
  : T extends object
  ? { [K in keyof T]: ExtractLeafValues<T[K]> }[keyof T]
  : never;
