import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // 브라우저 입장에서 모든 요청이 localhost:5173 → 같은 출처라 CORS·SameSite 문제 없음
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
  },
})
