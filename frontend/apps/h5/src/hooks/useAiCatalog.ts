/**
 * useAiCatalog · SC-16 · AI 模型 catalog 获取 hook
 * 对应 D-AI-Model-Catalog: GET /api/ai/models (tier 过滤后)
 *
 * 安全注意:
 * - 后端按 user.tier 过滤后返回，前端不需要重复过滤
 * - NORMAL 用户该 hook 返回空数组 (后端不返回任何 vipOnly 模型)
 * - catalog 热更：Nacos 配置中心推送 → 前端 cache invalidate
 */
import { useState, useEffect } from 'react';
import type { UserTier } from './useUserTier';

export interface AiModelCatalogItem {
  id: string;
  provider: string;
  displayName: string;
  vipOnly: boolean;
  supportedSubjects: string[];
  cost_tier: 'L' | 'M' | 'H';
  avg_latency_ms: number;
  notes: string;
}

interface AiCatalogState {
  catalog: AiModelCatalogItem[];
  loading: boolean;
  error: Error | null;
}

/** Mock catalog data (matches D-AI-Model-Catalog schema) */
const MOCK_VIP_CATALOG: AiModelCatalogItem[] = [
  {
    id: 'qwen-vl-max', provider: 'Alibaba Cloud', displayName: 'Qwen-VL Max',
    vipOnly: false, supportedSubjects: ['math', 'physics', 'chemistry', 'chinese'],
    cost_tier: 'M', avg_latency_ms: 3200, notes: '中文学科准确率更高 · VIP',
  },
  {
    id: 'gpt-4o-mini', provider: 'OpenAI', displayName: 'GPT-4o mini',
    vipOnly: false, supportedSubjects: ['english', 'math'],
    cost_tier: 'L', avg_latency_ms: 2100, notes: '英语/海外学生首选 · VIP',
  },
  {
    id: 'qwen-vl-plus', provider: 'Alibaba Cloud', displayName: 'Qwen-VL Plus',
    vipOnly: true, supportedSubjects: ['math', 'physics', 'chemistry', 'chinese', 'english'],
    cost_tier: 'H', avg_latency_ms: 4800, notes: '全学科最高精度 · VIP 专属',
  },
];

const MOCK_VIP_PLUS_CATALOG: AiModelCatalogItem[] = [
  ...MOCK_VIP_CATALOG,
  {
    id: 'claude-3-5-sonnet', provider: 'Anthropic', displayName: 'Claude 3.5 Sonnet',
    vipOnly: true, supportedSubjects: ['math', 'physics', 'english'],
    cost_tier: 'H', avg_latency_ms: 5200, notes: '复杂推理 · VIP_PLUS 专属',
  },
  {
    id: 'private-model', provider: 'Private', displayName: '私有化部署模型',
    vipOnly: true, supportedSubjects: ['math'],
    cost_tier: 'H', avg_latency_ms: 1500, notes: '私有化部署 · 内测 · VIP_PLUS',
  },
];

/**
 * GET /api/ai/models · tier 过滤后的 catalog
 * @param tier 当前用户 tier (null = not yet loaded)
 */
export function useAiCatalog(tier: UserTier | null): AiCatalogState {
  const [state, setState] = useState<AiCatalogState>({
    catalog: [],
    loading: false,
    error: null,
  });

  useEffect(() => {
    if (tier === null) return; // 等待 tier 加载完毕
    if (tier === 'NORMAL') {
      // NORMAL 用户: 不展示 catalog (UI 层防御 · 后端已静默忽略)
      setState({ catalog: [], loading: false, error: null });
      return;
    }

    let cancelled = false;
    setState((prev) => ({ ...prev, loading: true }));

    const fetchCatalog = async () => {
      try {
        // 真实实现: const resp = await aiClient.getModels()
        await new Promise((r) => setTimeout(r, 300));
        if (!cancelled) {
          setState({
            catalog: tier === 'VIP_PLUS' ? MOCK_VIP_PLUS_CATALOG : MOCK_VIP_CATALOG,
            loading: false,
            error: null,
          });
        }
      } catch (err) {
        if (!cancelled) {
          setState({ catalog: [], loading: false, error: err as Error });
        }
      }
    };

    fetchCatalog();
    return () => { cancelled = true; };
  }, [tier]);

  return state;
}
