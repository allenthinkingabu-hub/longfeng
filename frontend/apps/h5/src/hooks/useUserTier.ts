/**
 * useUserTier · SC-16 · 用户 tier 获取 hook
 * 对应 D-AI-Tier-Policy: NORMAL | VIP | VIP_PLUS
 *
 * 安全注意 (TDD §16.8):
 * - NORMAL 用户 UI 层不暴露 VIP 选择器 (tier 信号保护)
 * - aiModelHint 对 NORMAL 用户由后端静默忽略 (不返 403)
 */
import { useState, useEffect } from 'react';

export type UserTier = 'NORMAL' | 'VIP' | 'VIP_PLUS';

interface UserTierState {
  tier: UserTier | null;
  loading: boolean;
  error: Error | null;
}

/**
 * 从 GET /api/me 获取用户 tier
 * 目前使用 mock，后续由 Orval gen 的 client 替换
 */
export function useUserTier(): UserTierState {
  const [state, setState] = useState<UserTierState>({
    tier: null,
    loading: true,
    error: null,
  });

  useEffect(() => {
    let cancelled = false;

    const fetchTier = async () => {
      try {
        // 真实实现: const resp = await meClient.getMe()
        // mock: simulate network
        await new Promise((r) => setTimeout(r, 200));
        if (!cancelled) {
          setState({ tier: 'VIP', loading: false, error: null });
        }
      } catch (err) {
        if (!cancelled) {
          setState({ tier: null, loading: false, error: err as Error });
        }
      }
    };

    fetchTier();
    return () => { cancelled = true; };
  }, []);

  return state;
}
