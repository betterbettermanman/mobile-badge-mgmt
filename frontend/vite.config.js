import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 43124,
    proxy: {
      '/api': {
        target: 'http://localhost:8092',
        changeOrigin: true
      }
    }
  }
})
