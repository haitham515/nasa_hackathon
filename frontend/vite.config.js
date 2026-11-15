import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// In dev, /api (REST + Server-Sent Events) is proxied to the Spring Boot backend.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: process.env.ASTRA_BACKEND_URL || 'http://localhost:8080', changeOrigin: true },
    },
  },
})
