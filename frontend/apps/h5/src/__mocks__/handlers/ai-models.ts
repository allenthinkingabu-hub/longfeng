/**
 * MSW · SC-16 VIP AI 模型选择 mock（B 轨）
 *
 * 端点：
 *   - GET    /api/v1/me/tier              · 当前 tier
 *   - GET    /api/v1/ai-models            · 该 tier 可见模型池
 *   - POST   /api/v1/me/ai-model          · 设置默认模型（VIP/VIP_PLUS）
 *
 * 红线：NORMAL 用户传 aiModelHint → 后端 200 静默忽略 · 不报 403（plan §6.2 SC-16）
 */
import { http, HttpResponse } from 'msw';

type Tier = 'NORMAL' | 'VIP' | 'VIP_PLUS';

const POOL: Record<Tier, Array<{ id: string; name: string; costPerCall?: number; latencyP95Ms?: number; experimental?: boolean }>> = {
  NORMAL: [],
  VIP: [
    { id: 'qwen-vl-max',   name: 'Qwen VL Max（默认）' },
    { id: 'openai-gpt-4o', name: 'OpenAI GPT-4o' },
  ],
  VIP_PLUS: [
    { id: 'qwen-vl-max',                    name: 'Qwen VL Max（默认）',     costPerCall: 0.05, latencyP95Ms: 2400 },
    { id: 'openai-gpt-4o',                  name: 'OpenAI GPT-4o',           costPerCall: 0.18, latencyP95Ms: 1800 },
    { id: 'zhipu-glm-4v',                   name: 'Zhipu GLM-4V',            costPerCall: 0.04, latencyP95Ms: 3100 },
    { id: 'claude-3-7-sonnet-experimental', name: 'Claude 3.7 Sonnet（实验池）', costPerCall: 0.21, latencyP95Ms: 1900, experimental: true },
  ],
};

let currentSelectedModel: string | null = null;

function tierFromAuth(auth: string | null): Tier {
  if (!auth) return 'NORMAL';
  if (auth.includes('vipplus') || auth.includes('VIP_PLUS')) return 'VIP_PLUS';
  if (auth.includes('vip') || auth.includes('VIP')) return 'VIP';
  return 'NORMAL';
}

export const aiModelsHandlers = [
  http.get('/api/v1/me/tier', ({ request }) => {
    const tier = tierFromAuth(request.headers.get('authorization'));
    return HttpResponse.json({ tier });
  }),

  http.get('/api/v1/ai-models', ({ request }) => {
    const tier = tierFromAuth(request.headers.get('authorization'));
    return HttpResponse.json({
      tier,
      currentModel: currentSelectedModel ?? 'qwen-vl-max',
      models: POOL[tier],
    });
  }),

  http.post('/api/v1/me/ai-model', async ({ request }) => {
    const tier = tierFromAuth(request.headers.get('authorization'));
    const body = await request.json() as { modelId?: string };
    if (tier === 'NORMAL') {
      // 静默忽略 · 200 而非 403
      return HttpResponse.json({ ok: true, ignored: true, currentModel: 'qwen-vl-max' });
    }
    const allowed = POOL[tier].some((m) => m.id === body?.modelId);
    if (!allowed) {
      return new HttpResponse(JSON.stringify({ error: 'MODEL_NOT_ALLOWED_FOR_TIER' }), { status: 400 });
    }
    currentSelectedModel = body!.modelId!;
    return HttpResponse.json({ ok: true, currentModel: currentSelectedModel });
  }),

  // analyze 时附带 aiModelHint · NORMAL 必须 200 不报 403
  http.post('/api/ai/analyze', async ({ request }) => {
    const tier = tierFromAuth(request.headers.get('authorization'));
    const body = await request.json() as { aiModelHint?: string } | null;
    const hint = body?.aiModelHint;
    let modelUsed = currentSelectedModel ?? 'qwen-vl-max';
    if (tier !== 'NORMAL' && hint && POOL[tier].some((m) => m.id === hint)) {
      modelUsed = hint;
    }
    // NORMAL 用户传 hint：静默忽略 · 200
    return HttpResponse.json({
      taskId: `task-${Date.now()}`,
      providerHint: modelUsed,
      status: 'ANALYZING',
    });
  }),
];
