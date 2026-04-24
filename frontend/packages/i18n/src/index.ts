import zhCN from './zh-CN.json';
import enUS from './en-US.json';

export { zhCN, enUS };
export type Locale = 'zh-CN' | 'en-US';
export const DEFAULT_LOCALE: Locale = 'zh-CN';

export const DICTIONARIES: Record<Locale, Record<string, string>> = {
  'zh-CN': zhCN as Record<string, string>,
  'en-US': enUS as Record<string, string>,
};

/** 简易 t 函数 · 占位符 {name} 替换 · 生产走 i18next / vue-i18n 自行封装. */
export function t(locale: Locale, key: string, vars: Record<string, string | number> = {}): string {
  const dict = DICTIONARIES[locale];
  const tmpl = dict?.[key] ?? DICTIONARIES[DEFAULT_LOCALE][key] ?? key;
  return Object.entries(vars).reduce(
    (acc, [k, v]) => acc.replace(`{${k}}`, String(v)),
    tmpl,
  );
}
