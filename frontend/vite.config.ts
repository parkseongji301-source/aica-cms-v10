import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({
  base: '/next-app/', plugins: [react()],
  build: { outDir: '../src/main/resources/static/next-app', emptyOutDir: true },
  server: {host: '127.0.0.1', proxy: Object.fromEntries(['/api','/admin/','/login','/logout','/css'].map(path => [path, 'http://127.0.0.1:8081']))}
});
