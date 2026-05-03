// S8 miniapp · 微信订阅消息封装
// 模板 ID 由微信公众平台后台分配 · 此处用 placeholder · 留 user 配真值
// 落地计划 §S8 FE-08 行: "微信订阅消息申请"

export const TEMPLATE_IDS = {
  // 复习提醒模板 (review reminder) · 占位 · user 配置后替换
  reviewReminder: 'TPL_REVIEW_REMIND_PLACEHOLDER',
  // 考试日提醒模板 (exam day) · 占位
  examDay: 'TPL_EXAM_DAY_PLACEHOLDER',
  // 家庭分享提醒 · 占位
  familyShare: 'TPL_FAMILY_SHARE_PLACEHOLDER',
};

/** 一次性弹出系统订阅消息授权弹窗 · 用户确认后回 accept */
export function requestSubscribe(templateIds: string[]): Promise<Record<string, 'accept' | 'reject' | 'ban'>> {
  return new Promise((resolve, reject) => {
    wx.requestSubscribeMessage({
      tmplIds: templateIds,
      success: (res) => {
        const out: Record<string, 'accept' | 'reject' | 'ban'> = {};
        for (const k in res) {
          if (k === 'errMsg') continue;
          const v = (res as unknown as Record<string, string>)[k];
          if (v === 'accept' || v === 'reject' || v === 'ban') out[k] = v;
        }
        resolve(out);
      },
      fail: (e) => reject(e),
    });
  });
}

/** 上报授权结果到后端 · backend notification-service 据此放行/抑制推送 */
export async function reportSubscribeStatus(status: Record<string, 'accept' | 'reject' | 'ban'>): Promise<void> {
  const { api } = await import('./api');
  try {
    await api.post('/notifications/subscribe-status', { status });
  } catch {
    // 静默失败 · 用户体验优先 · 下次启动会再尝试
  }
}
