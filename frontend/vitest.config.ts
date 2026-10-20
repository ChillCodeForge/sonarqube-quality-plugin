import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { defineConfig } from 'vitest/config';

const frontendRoot = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(frontendRoot, '..');

export default defineConfig({
  root: repositoryRoot,
  plugins: [react({ jsxRuntime: 'classic' })],
  resolve: {
    alias: [
      {
        find: /^react($|\/)/,
        replacement: `${path.resolve(frontendRoot, 'node_modules/react')}$1`,
      },
      {
        find: /^react-dom($|\/)/,
        replacement: `${path.resolve(frontendRoot, 'node_modules/react-dom')}$1`,
      },
      { find: '@', replacement: path.resolve(frontendRoot, 'src') },
    ],
  },
  test: {
    globals: true,
    environment: 'jsdom',
    include: ['frontend/src/**/*.{test,spec}.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      reportsDirectory: 'frontend/coverage',
      include: ['frontend/src/**/*.{ts,tsx}'],
      exclude: ['frontend/src/**/*.test.{ts,tsx}', 'frontend/src/**/*.spec.{ts,tsx}'],
    },
  },
});
