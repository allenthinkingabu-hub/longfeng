import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Dev-only middleware:
 *  - SC-13 红线 · 匿名写 API → 403（Playwright APIRequestContext 直发 baseURL，不经 MSW SW）
 *    覆盖：POST /api/v1/wrongbook/items（无 Authorization 时 403）
 *  - 其他 /api 仍然 proxy 到 backend（保留 A 轨能力）
 */
function e2eFallbackPlugin() {
  return {
    name: 'e2e-anon-write-fallback',
    configureServer(server: any) {
      server.middlewares.use((req: any, res: any, next: any) => {
        const url: string = req.url || '';
        const method: string = (req.method || 'GET').toUpperCase();
        // SC-13: 匿名写错题项必须 403
        if (
          method === 'POST' &&
          url.startsWith('/api/v1/wrongbook/items')
        ) {
          const auth = req.headers['authorization'];
          if (!auth || String(auth).toLowerCase().includes('anonymous')) {
            res.statusCode = 403;
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({ error: 'ANONYMOUS_WRITE_FORBIDDEN' }));
            return;
          }
        }
        next();
      });
    },
  };
}

export default defineConfig({
  plugins: [react(), e2eFallbackPlugin()],
  server: {
    port: 5174,
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    target: 'es2020',
    sourcemap: true,
    chunkSizeWarningLimit: 500,
  },
});
