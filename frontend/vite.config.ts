import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwind from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwind()],
  resolve: {
    alias: {
      '@': '/src',
    },
  },
  server: { port: 5173, strictPort: true, proxy: { '/api': 'http://127.0.0.1:18080' } },
  preview: { proxy: { '/api': 'http://127.0.0.1:18080' } },
});

