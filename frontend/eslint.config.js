import { base } from '@chillcode/eslint-config';
import typescript from '@chillcode/eslint-config/typescript';
import react from '@chillcode/eslint-config/react';
import globals from 'globals';
import reactRefresh from 'eslint-plugin-react-refresh';

export default [
  { ignores: ['dist', 'coverage'] },
  ...base,
  ...typescript,
  ...react,
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.browser,
    },
    plugins: {
      'react-refresh': reactRefresh,
    },
    rules: {
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
    },
  },
];
