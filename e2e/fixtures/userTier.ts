/**
 * S9 · SC-16 VIP AI 模型选择 fixture
 *
 * 三 tier 模型池：
 *   - NORMAL   · 不显示选择器 · 显示 upgrade-hint · 后端静默忽略 aiModelHint（plan §6.2 SC-16）
 *   - VIP      · 显示基础选择器（qwen-vl-max / openai-gpt-4o）
 *   - VIP_PLUS · 显示实验池（含 claude-3-7-sonnet-experimental + 成本/延迟）
 */
import type { UserTier } from './student';

export interface AiModelOption {
  id: string;
  name: string;
  costPerCall?: number;     // VIP_PLUS only
  latencyP95Ms?: number;    // VIP_PLUS only
  experimental?: boolean;
}

export const TIER_MODEL_POOL: Record<UserTier, AiModelOption[]> = {
  NORMAL: [], // 后端默认走 qwen-vl-max，前端不显示选择器
  VIP: [
    { id: 'qwen-vl-max',   name: 'Qwen VL Max（默认）' },
    { id: 'openai-gpt-4o', name: 'OpenAI GPT-4o' },
  ],
  VIP_PLUS: [
    { id: 'qwen-vl-max',                name: 'Qwen VL Max（默认）',     costPerCall: 0.05, latencyP95Ms: 2400 },
    { id: 'openai-gpt-4o',              name: 'OpenAI GPT-4o',           costPerCall: 0.18, latencyP95Ms: 1800 },
    { id: 'zhipu-glm-4v',               name: 'Zhipu GLM-4V',            costPerCall: 0.04, latencyP95Ms: 3100 },
    { id: 'claude-3-7-sonnet-experimental', name: 'Claude 3.7 Sonnet（实验池）', costPerCall: 0.21, latencyP95Ms: 1900, experimental: true },
  ],
};

/**
 * 期望的 P13 AI 子区视觉契约：
 *   - data-sc16-tier 属性必须出现，值 = tier
 *   - NORMAL：sc16-section 存在 + sc16-upgrade-hint 可见 + sc16-model-selector 不存在
 *   - VIP：sc16-model-selector 存在 + 2 个 model-item
 *   - VIP_PLUS：sc16-model-selector 存在 + 4 个 model-item + 每项有 cost/latency 子节点
 */
export interface SC16Expectation {
  showSelector: boolean;
  showUpgradeHint: boolean;
  modelCount: number;
  showCostLatency: boolean;
}

export const SC16_EXPECTATIONS: Record<UserTier, SC16Expectation> = {
  NORMAL:   { showSelector: false, showUpgradeHint: true,  modelCount: 0, showCostLatency: false },
  VIP:      { showSelector: true,  showUpgradeHint: false, modelCount: 2, showCostLatency: false },
  VIP_PLUS: { showSelector: true,  showUpgradeHint: false, modelCount: 4, showCostLatency: true  },
};

/**
 * 给 NORMAL 用户在 P02/P03 强行注入 aiModelHint = 'openai-gpt-4o'，
 * 后端必须静默忽略，**不报 403**（plan §6.2 SC-16 末段）。
 */
export const NORMAL_TIER_HINT_PAYLOAD = {
  aiModelHint: 'openai-gpt-4o',
};
