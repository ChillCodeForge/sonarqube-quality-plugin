import { beforeEach, describe, expect, it, vi } from 'vitest';
import { injectTheme } from './theme';

vi.mock('./theme.css?inline', () => ({ default: ':root { --cq-green: #2ee6ac; }' }));

describe('injectTheme', () => {
  beforeEach(() => document.head.replaceChildren());

  it('adds the bundled style once even when multiple pages request it', () => {
    injectTheme();
    const style = document.getElementById('chillcode-quality-theme');

    expect(style?.tagName).toBe('STYLE');
    expect(style?.id).toBe('chillcode-quality-theme');
    expect(style?.textContent).toBe(':root { --cq-green: #2ee6ac; }');

    injectTheme();
    expect(document.querySelectorAll('#chillcode-quality-theme')).toHaveLength(1);
  });
});
