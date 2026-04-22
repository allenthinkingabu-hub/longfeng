// S0 skeleton · ESLint root config
// 严格模式下 · 禁止 any · 禁止硬编码中文（eslint-plugin-i18next 在 S7 Phase 生效时添加 plugin+rules）
// 当前 S0 只保留最小 rules，避免空文件报错阻塞 CI

/** @type {import('eslint').Linter.Config} */
module.exports = {
  root: true,
  env: { es2022: true, node: true, browser: true },
  ignorePatterns: ['node_modules', 'dist', 'build', '.eslintrc.cjs'],
  rules: {
    // 占位 · S7 Phase 接入 @typescript-eslint + eslint-plugin-i18next 规则集
  }
};
