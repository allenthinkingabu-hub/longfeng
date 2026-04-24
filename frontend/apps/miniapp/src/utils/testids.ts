// S7 miniapp · testid 常量 · 与 @longfeng/testids 值完全同步 · 双端对称 ADR 0014
// 小程序 data-test-id 属性 · miniprogram-automator 兼容
export const TEST_IDS = {
  capture: {
    root: 'capture.root',
    form: {
      submit: 'capture.form.submit',
      subject: 'capture.form.subject',
      stem: 'capture.form.stem',
      'draft-hint': 'capture.form.draft-hint',
    },
    camera: 'capture.camera.btn',
    gallery: 'capture.gallery.btn',
    manual: 'capture.manual.btn',
    'size-exceeded': 'capture.size-exceeded.toast',
  },
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
  },
};
