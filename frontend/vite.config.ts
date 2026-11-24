import { resolve } from 'node:path';
import type { Plugin } from 'vite';
import { defineConfig } from 'vitest/config';

/** En dev Vite manda todo a /index.html: reescribe /admin/* (sin extension) al index del area privada, como nginx. */
function fallbackAdmin(): Plugin {
  return {
    name: 'fallback-admin',
    configureServer(server) {
      server.middlewares.use((req, _res, next) => {
        const url = req.url ?? '';
        const ruta = url.split('?')[0] ?? '';
        if (ruta.startsWith('/admin/') && !/\.[a-z0-9]+$/i.test(ruta) && ruta !== '/admin/') {
          req.url = '/admin/index.html' + url.slice(ruta.length);
        }
        next();
      });
    },
  };
}

export default defineConfig({
  plugins: [fallbackAdmin()],
  server: {
    port: 5173,
    proxy: {
      '/api': process.env['VITE_API_PROXY'] ?? 'http://localhost:8081',
    },
  },
  build: {
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
        admin: resolve(__dirname, 'admin/index.html'),
      },
    },
  },
  test: {
    environment: 'jsdom',
  },
});
