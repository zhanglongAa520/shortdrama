import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'
import { realpathSync } from 'node:fs'

export default defineConfig({
  plugins: [vue()],
  server: {
    fs: {
      strict: true,
      allow: [fileURLToPath(new URL('.', import.meta.url)), realpathSync(fileURLToPath(new URL('./node_modules', import.meta.url)))],
    },
    proxy: { '/api': 'http://127.0.0.1:8080' },
  },
})
