// S7 miniapp · i18n · zh-CN/en-US · 与 H5 资源同步（H5 i18next → miniapp 简化 JSON 查询）
import zh from './i18n-zh-CN';
import en from './i18n-en-US';

type Resources = typeof zh;

function getLang(): 'zh-CN' | 'en-US' {
  try {
    const v = wx.getStorageSync('lang');
    return v === 'en-US' ? 'en-US' : 'zh-CN';
  } catch {
    return 'zh-CN';
  }
}

function deepGet(obj: any, path: string): string {
  const parts = path.split('.');
  let cur = obj;
  for (const p of parts) {
    if (cur && typeof cur === 'object' && p in cur) cur = cur[p];
    else return path;
  }
  return typeof cur === 'string' ? cur : path;
}

export function t(key: string, vars?: Record<string, string | number>): string {
  const res: Resources = getLang() === 'en-US' ? (en as Resources) : zh;
  let s = deepGet(res, key);
  if (vars) {
    for (const k in vars) s = s.replace(new RegExp(`{{${k}}}`, 'g'), String(vars[k]));
  }
  return s;
}
