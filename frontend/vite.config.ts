import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
    rollupOptions: {
      input: {
        'chillcode-quality-dashboard': 'src/quality-dashboard.tsx',
        'chillcode-mutation-testing': 'src/mutation-testing.tsx',
      },
      output: {
        entryFileNames: '[name].js',
        chunkFileNames: '[name].js',
        assetFileNames: '[name].[ext]',
      },
    },
  },
})