/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Backend origin used by the dev server proxy. The frontend never talks to Discord
// directly and never needs CORS: the backend serves session cookies and CSRF, and the
// dev server proxies the auth/API paths to Spring Boot so requests stay same-origin.
const BACKEND_ORIGIN = process.env.GUILDOS_BACKEND_ORIGIN ?? 'http://localhost:8080';

// Paths that must reach the backend during local development.
const PROXIED_PATHS = ['/api', '/oauth2', '/login', '/logout'];

const proxy = Object.fromEntries(
  PROXIED_PATHS.map((path) => [
    path,
    {
      target: BACKEND_ORIGIN,
      changeOrigin: true,
      // Auth redirects (302) from Spring Security must be forwarded to the browser,
      // not followed by the proxy, so the OAuth handshake works end to end.
      followRedirects: false,
    },
  ]),
);

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy,
  },
  preview: {
    port: 4173,
    proxy,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: false,
    restoreMocks: true,
  },
});
