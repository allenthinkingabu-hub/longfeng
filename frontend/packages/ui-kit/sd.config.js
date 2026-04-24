/**
 * Sd.1 · Style Dictionary config · design/system/tokens/*.json → src/tokens.{css,wxss,ts}
 *
 * §16.2 Sd.1 要求：
 *   - 6 组 token 源 JSON（color / typography / spacing / radius / shadow / motion）
 *   - 三端输出一致 · 由 verify-tokens.sh CI 断言
 *   - 小程序平台 saturation +12% / shadow_alpha +0.04（见 color.json platform_overrides）
 */
export default {
  source: ['../../../design/system/tokens/*.json'],
  platforms: {
    css: {
      transformGroup: 'css',
      buildPath: 'src/',
      files: [
        {
          destination: 'tokens.css',
          format: 'css/variables',
          options: { outputReferences: false },
        },
      ],
    },
    wxss: {
      transformGroup: 'css',
      buildPath: 'src/',
      files: [
        {
          destination: 'tokens.wxss',
          format: 'css/variables',
          options: { outputReferences: false },
        },
      ],
    },
    js: {
      transformGroup: 'js',
      buildPath: 'src/',
      files: [
        {
          destination: 'tokens.ts',
          format: 'javascript/es6',
        },
      ],
    },
  },
};
