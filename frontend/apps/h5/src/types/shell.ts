// S7 · Shell 类型定义 · FE-01-shells-bootstrap
// 对应 PRD §2A.3.1 登录态决策树

/** Shell 类型枚举 */
export type ShellType = 'tab' | 'anon' | 'observer';

/** 匿名 Shell 内落位路由 */
export type AnonRoute =
  | 'landing'       // P-LANDING · 访客落地（默认）
  | 'guest-capture' // P-GUEST-CAPTURE · 游客拍题
  | 'shared'        // P-SHARED · 分享预览
  | 'welcomeback'   // P-WELCOMEBACK · 回流唤起（P1）
  | 'observer';     // P-OBSERVER · 观察者（注：observer 路由也可在 ObserverShell 直接渲染）

/** resolve-entry 的决策输出 */
export type EntryResult =
  | { shell: 'tab'; deeplink?: string }
  | { shell: 'anon'; route: AnonRoute; shareToken?: string }
  | { shell: 'observer'; code: string };

/** wb:// 深链路由解析结果 */
export type DeeplinkRoute =
  | { type: 'capture' }
  | { type: 'review-exec'; nodeId: string }
  | { type: 'shared'; shareToken: string }
  | { type: 'observer'; code: string }
  | { type: 'home'; focus?: string }
  | { type: 'review-today' }
  | { type: 'calendar' }
  | { type: 'event'; eventId: string }
  | { type: 'notifications' }
  | { type: 'me' }
  | { type: 'wrongbook' }
  | { type: 'wrongbook-detail'; qid: string }
  | { type: 'unknown'; raw: string };

/** JWT 解析结果（本地存储） */
export interface JwtPayload {
  sub: string;        // student_id
  scope?: 'USER' | 'OBSERVER' | 'GUEST';
  exp: number;        // Unix timestamp (seconds)
}

/** Observer JWT 附加信息 */
export interface ObserverJwtPayload extends JwtPayload {
  scope: 'OBSERVER';
  observer_code: string;
  student_id: string;  // 被观察的学生 id
  observer_role?: string; // 'parent' | 'teacher'
}
