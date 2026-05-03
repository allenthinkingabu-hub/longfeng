// S7-S8 miniapp · testid 常量 · 与 @longfeng/testids 双端同步 · ADR 0014
// 小程序 data-test-id 属性 · miniprogram-automator 兼容
// 14 页全覆盖 · 与 H5 PXX-spec.md §8 AC 同结构
export const TEST_IDS = {
  // ========== Mood A · Landing ==========
  landing: {
    root: 'landing.root',
    'cta-try': 'landing.cta-try',
    'cta-login': 'landing.cta-login',
    sample: 'landing.sample',
    feature: 'landing.feature',
    social: 'landing.social',
    'how-step': 'landing.how-step',
  },

  // ========== Mood C · Camera flow ==========
  capture: {
    root: 'capture.root',
    shutter: 'capture.shutter',
    gallery: 'capture.gallery.btn',
    manual: 'capture.manual.btn',
    subject: 'capture.form.subject',
    stem: 'capture.form.stem',
    submit: 'capture.form.submit',
    'draft-hint': 'capture.form.draft-hint',
    'size-exceeded': 'capture.size-exceeded.toast',
    bracket: 'capture.bracket',
    detect: 'capture.detect.badge',
    tip: 'capture.tip',
  },
  analyzing: {
    root: 'analyzing.root',
    progress: 'analyzing.progress',
    stage: 'analyzing.stage',
    cancel: 'analyzing.cancel',
    fallback: 'analyzing.fallback.indicator',
  },
  result: {
    root: 'result.root',
    'ans-card': 'result.ans-card',
    reason: 'result.reason',
    step: 'result.step',
    'kp-card': 'result.kp-card',
    'ebbing-card': 'result.ebbing-card',
    'cta-save': 'result.cta-save',
    'cta-retake': 'result.cta-retake',
  },

  // ========== Mood B · Wrongbook ==========
  wrongbookList: {
    root: 'wrongbook.list.root',
    'filter-bar': 'wrongbook.list.filter-bar',
    'item-card': 'wrongbook.list.item-card',
    'active-tab': 'wrongbook.list.active-tab',
    'archive-tab': 'wrongbook.list.archive-tab',
    'load-more': 'wrongbook.list.load-more',
    empty: 'wrongbook.list.empty',
  },
  wrongbookDetail: {
    root: 'wrongbook.detail.root',
    'stem-text': 'wrongbook.detail.stem-text',
    'tag-sheet': 'wrongbook.detail.tag-sheet',
    'tag-chip': 'wrongbook.detail.tag-chip',
    'tag-save': 'wrongbook.detail.tag-save',
    'explain-stream': 'wrongbook.detail.explain-stream',
    'similar-card': 'wrongbook.detail.similar-card',
    archive: 'wrongbook.detail.archive',
  },

  // ========== Mood B · Review ==========
  reviewToday: {
    root: 'review.today.root',
    'summary-hero': 'review.today.summary-hero',
    'time-group': 'review.today.time-group',
    'session-card': 'review.today.session-card',
    'cta-start-all': 'review.today.cta-start-all',
    empty: 'review.today.empty',
  },
  reviewExec: {
    root: 'review.exec.root',
    stem: 'review.exec.stem',
    'reveal-btn': 'review.exec.reveal-btn',
    'mastery-forgot': 'review.exec.mastery-forgot',
    'mastery-partial': 'review.exec.mastery-partial',
    'mastery-mastered': 'review.exec.mastery-mastered',
    quit: 'review.exec.quit',
    'quit-confirm': 'review.exec.quit-confirm',
  },
  reviewDone: {
    root: 'review.done.root',
    'session-summary': 'review.done.session-summary',
    'cta-back': 'review.done.cta-back',
    'cta-continue': 'review.done.cta-continue',
    confetti: 'review.done.confetti',
  },

  // ========== Mood B · Calendar ==========
  calendarMonth: {
    root: 'calendar.month.root',
    'day-cell': 'calendar.month.day-cell',
    'event-list': 'calendar.month.event-list',
    'subscribe-btn': 'calendar.month.subscribe-btn',
  },
  eventDetail: {
    root: 'event.detail.root',
    'event-title': 'event.detail.title',
    'event-meta': 'event.detail.meta',
    'cta-review-now': 'event.detail.cta-review-now',
    'share-btn': 'event.detail.share-btn',
  },

  // ========== Mood B · Notification ==========
  notification: {
    root: 'notification.root',
    'msg-card': 'notification.msg-card',
    'mark-read': 'notification.mark-read',
    'subscribe-msg-cta': 'notification.subscribe-msg-cta',
    empty: 'notification.empty',
  },

  // ========== Mood B · Me ==========
  settings: {
    root: 'settings.root',
    'profile-cell': 'settings.profile-cell',
    'lang-cell': 'settings.lang-cell',
    'ai-model-cell': 'settings.ai-model-cell',
    'logout-btn': 'settings.logout-btn',
    'tier-badge': 'settings.tier-badge',
  },
  aiModelPref: {
    root: 'me.ai-model-pref.root',
    'model-radio': 'me.ai-model-pref.model-radio',
    'save-btn': 'me.ai-model-pref.save-btn',
    'tier-hint': 'me.ai-model-pref.tier-hint',
  },
};
