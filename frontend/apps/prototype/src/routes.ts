// Sd.9 · 19 路由映射 P01..P19 · 与 design/specs/PXX.md 一一对应
export interface RouteDef {
  id: string; // P01
  path: string; // /p01-login
  title: string; // 登录 / 注册入口
  states: string[]; // 状态枚举 per spec §1
}

export const ROUTES: RouteDef[] = [
  { id: 'P01', path: '/p01-login', title: '登录 / 注册', states: ['default', 'typing', 'loading', 'error', 'success', 'disabled'] },
  { id: 'P02', path: '/p02-home', title: '首页 Home', states: ['default', 'with_due', 'loading', 'error', 'empty'] },
  { id: 'P03', path: '/p03-capture', title: '拍照录入', states: ['ready', 'capturing', 'preview', 'uploading', 'error'] },
  { id: 'P04', path: '/p04-analyzing', title: 'AI 解析中', states: ['analyzing', 'slow', 'failed'] },
  { id: 'P05', path: '/p05-result', title: 'AI 解析结果', states: ['high_conf', 'low_conf', 'manual_edit', 'saved'] },
  { id: 'P06', path: '/p06-wrongbook-list', title: '错题本列表', states: ['default', 'filtered', 'empty', 'loading'] },
  { id: 'P07', path: '/p07-wrongbook-detail', title: '错题详情', states: ['default', 'editing', 'mastered', 'with_plan'] },
  { id: 'P08', path: '/p08-review-today', title: '今日复习', states: ['has_due', 'empty', 'loading'] },
  { id: 'P09', path: '/p09-review-exec', title: '复习执行', states: ['q1', 'q2', 'q3', 'feedback'] },
  { id: 'P10', path: '/p10-review-done', title: '复习完成', states: ['mastered', 'more_due', 'streak'] },
  { id: 'P11', path: '/p11-calendar-month', title: '日历月视图', states: ['default', 'dense', 'empty'] },
  { id: 'P12', path: '/p12-event-detail', title: '事件详情', states: ['due', 'done', 'skipped'] },
  { id: 'P13', path: '/p13-notifications', title: '通知', states: ['default', 'empty', 'unread'] },
  { id: 'P14', path: '/p14-settings', title: '设置', states: ['default', 'signed_in', 'guest'] },
  { id: 'P15', path: '/p15-landing', title: 'Landing / 匿名引导', states: ['default'] },
  { id: 'P16', path: '/p16-guest-capture', title: '匿名拍照试用', states: ['ready', 'capturing', 'preview', 'upgrade_prompt'] },
  { id: 'P17', path: '/p17-shared', title: '分享卡', states: ['default', 'expired'] },
  { id: 'P18', path: '/p18-welcomeback', title: '欢迎回归', states: ['default', 'streak_broken'] },
  { id: 'P19', path: '/p19-observer', title: '家长监督视图', states: ['default', 'alert', 'empty'] },
];
