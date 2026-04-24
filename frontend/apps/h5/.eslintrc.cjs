// S7 · V-S7-03 · H5 ESLint · 消费 workspace eslint-plugin-local
module.exports = {
  root: true,
  parser: '@typescript-eslint/parser',
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
    ecmaFeatures: { jsx: true },
  },
  plugins: ['local'],
  rules: {
    'local/testid-required': 'error',
  },
  ignorePatterns: ['dist', 'node_modules', '*.config.ts', '*.config.js', '*.config.cjs'],
};
