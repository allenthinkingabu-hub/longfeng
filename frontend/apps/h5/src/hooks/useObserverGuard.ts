// S7 · hooks/useObserverGuard.ts
// 观察者防护 Hook
//
// C4 红线（TDD §1.3 / PRD §2A.3.1 §8 匿名 Shell 硬性规则）：
//   - OBSERVER JWT 不得写任何 wb_* 表
//   - 所有写请求网关直接 403；前端在写操作发起前拦截
//   - 写按钮必须 aria-disabled="true" + tabIndex=-1
//
// 与 resolveEntry 的关系：
//   - resolveEntry 已判断 scope=OBSERVER 的 JWT → ObserverShell
//   - 该 hook 在 ObserverShell 内使用，亦可在其他 Shell 内作防御性使用
//   - 判断依据：localStorage `lf:token` 的 scope 字段

import { useCallback, useMemo, useRef } from 'react';
import type { JwtPayload } from '../types/shell';

const TOKEN_KEY = 'lf:token' as const;

// 写动词白名单（这些方法被认为是写操作）
const WRITE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'] as const);
type WriteMethod = 'POST' | 'PUT' | 'PATCH' | 'DELETE';

/** 全局 Toast 回调（由 Shell 层注入，避免 hook 直接依赖 UI 库） */
type ToastFn = (message: string) => void;

let _globalToast: ToastFn | null = null;
export function setObserverGuardToast(fn: ToastFn): void {
  _globalToast = fn;
}

// ── §1 JWT 读取 ──────────────────────────────────────────────────────────────

function getLocalJwtPayload(): JwtPayload | null {
  try {
    const raw = typeof localStorage !== 'undefined' ? localStorage.getItem(TOKEN_KEY) : null;
    if (!raw) return null;
    const parts = raw.split('.');
    if (parts.length !== 3) return null;
    const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = b64 + '=='.slice((b64.length % 4) || 4);
    return JSON.parse(atob(padded)) as JwtPayload;
  } catch {
    return null;
  }
}

// ── §2 Hook ───────────────────────────────────────────────────────────────────

export interface UseObserverGuardReturn {
  /** 当前用户是否为观察者（scope === 'OBSERVER'）*/
  isObserver: boolean;

  /**
   * 写操作守卫。
   *
   * 用法：
   *   const { guardWrite } = useObserverGuard();
   *   const handleSave = guardWrite(() => callApi(...));
   *
   * 若为观察者，显示 Toast 并返回 rejected Promise（阻止原操作）。
   * 若非观察者，直接执行原操作。
   */
  guardWrite: <T>(fn: () => T | Promise<T>, method?: WriteMethod) => () => Promise<T>;

  /**
   * 为写按钮生成 ARIA 属性（C4 红线）。
   * 在 JSX 中展开：`<button {...observerButtonProps}>保存</button>`
   */
  observerButtonProps: {
    'aria-disabled': boolean;
    tabIndex: number;
    'data-observer-locked': boolean;
  } | Record<string, never>;
}

/**
 * useObserverGuard · 观察者写操作拦截 Hook
 *
 * @param toastMessage - 观察者触发写操作时的提示文案（默认"观察者模式不支持此操作"）
 */
export function useObserverGuard(
  toastMessage = '观察者模式仅供查看，无法执行此操作'
): UseObserverGuardReturn {
  const payload = useMemo(() => getLocalJwtPayload(), []);
  const isObserver = payload?.scope === 'OBSERVER';
  const toastShownRef = useRef(false);

  const guardWrite = useCallback(
    <T>(fn: () => T | Promise<T>, _method?: WriteMethod): (() => Promise<T>) => {
      return async () => {
        if (!isObserver) {
          return Promise.resolve(fn()) as Promise<T>;
        }
        // 观察者 → 拦截
        if (_globalToast) {
          _globalToast(toastMessage);
        } else if (!toastShownRef.current) {
          // fallback: console.warn（测试环境无 toast 注入时）
          console.warn('[ObserverGuard]', toastMessage);
          toastShownRef.current = true;
        }
        return Promise.reject(new Error('OBSERVER_WRITE_BLOCKED'));
      };
    },
    [isObserver, toastMessage]
  );

  const observerButtonProps = useMemo((): UseObserverGuardReturn['observerButtonProps'] => {
    if (!isObserver) return {};
    return {
      'aria-disabled': true,
      tabIndex: -1,
      'data-observer-locked': true,
    };
  }, [isObserver]);

  return { isObserver, guardWrite, observerButtonProps };
}

/**
 * guardFetch · 便捷包装：为 fetch 调用注入观察者防护。
 * 在非 hook 上下文中（如 API 层）使用。
 *
 * 仅当方法为写动词时拦截。
 */
export function guardFetch(
  input: RequestInfo | URL,
  init?: RequestInit,
  onBlocked?: () => void
): Promise<Response> {
  const method = ((init?.method ?? 'GET') as string).toUpperCase() as WriteMethod;
  if (WRITE_METHODS.has(method)) {
    const payload = getLocalJwtPayload();
    if (payload?.scope === 'OBSERVER') {
      if (_globalToast) _globalToast('观察者模式仅供查看，无法执行此操作');
      if (onBlocked) onBlocked();
      return Promise.reject(new Error('OBSERVER_WRITE_BLOCKED'));
    }
  }
  return fetch(input, init);
}
