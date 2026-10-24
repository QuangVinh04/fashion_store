import { defineConfig } from 'vitest/config'
import path from 'path'

export default defineConfig({
  oxc: {
    jsx: { runtime: 'automatic' }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    testTimeout: 15000
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@core': path.resolve(__dirname, './src/@core')
    }
  }
})
