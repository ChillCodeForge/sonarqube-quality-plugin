import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// SonarQube loads each page-extension .js file as a plain classic <script>
// (not type="module"), and each bundle MUST be self-contained - see
// SonarSource/sonar-custom-plugin-example README: "1 entry file per
// extension page" / "the final result MUST adhere to: entry file located
// in static/". Vite's default ES-module output with shared chunks
// (import "./api-hash.js") breaks under a classic script tag, so we force
// IIFE format with no code-splitting: build each page as its own fully
// self-contained bundle via separate library-mode invocations, selected by
// the PAGE env var (see package.json build script).
const page = process.env.PAGE
if (!page) {
  throw new Error('Set PAGE=quality_dashboard|mutation_testing before building')
}

export default defineConfig({
  plugins: [react({ jsxRuntime: 'classic' })],
  // Some bundled deps (React, Recharts) read process.env.NODE_ENV at
  // runtime; there is no Node "process" global in the browser, and
  // SonarQube's page-extension sandbox doesn't provide one either -
  // without this the IIFE bundle throws "process is not defined" on
  // load and nothing ever renders (silent white page, no visible error
  // unless you check the browser console).
  define: {
    'process.env.NODE_ENV': JSON.stringify('production'),
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: false,
    sourcemap: false,
    lib: {
      entry: path.resolve(__dirname, `src/${page}.tsx`),
      name: `ChillCode_${page}`,
      formats: ['iife'],
      fileName: () => `${page}.js`,
    },
    rollupOptions: {
      // Use SonarQube's own globally-exposed React (window.React,
      // window.ReactDOM) instead of bundling our own copy: two different
      // React instances in the same page silently fail to render each
      // other's elements (confirmed: SonarQube ships React 19.2.4, our
      // bundle shipped React 18.2.0 - registerExtension's callback ran
      // and returned a valid element, but nothing appeared on screen).
      // Force the classic JSX transform (React.createElement) instead of
      // the automatic runtime's separate "react/jsx-runtime" module: that
      // module reads React's internal __CLIENT_INTERNALS in a way that
      // doesn't line up with SonarQube's externally-provided React 19
      // instance (confirmed error: "Cannot read properties of undefined
      // (reading 'ReactCurrentOwner')"). Classic transform only needs the
      // plain `React` global we already externalize.
      external: ['react', 'react-dom', 'react-dom/client'],
      output: {
        extend: true,
        globals: {
          react: 'React',
          'react-dom': 'ReactDOM',
          'react-dom/client': 'ReactDOM',
        },
      },
    },
  },
})
