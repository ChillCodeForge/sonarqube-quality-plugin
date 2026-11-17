import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const css = readFileSync(path.resolve(import.meta.dirname, 'theme.css'), 'utf8');

const rule = (selector: string) => {
  const start = css.indexOf(`${selector} {`);
  const end = css.indexOf('}', start);

  return css.slice(start, end);
};

describe('file path layout', () => {
  it('lets the directory give way first and ellipsizes a file name that is still too long', () => {
    const name = rule('.cq-root .cq-path-name');
    const directory = rule('.cq-root .cq-path-dir');

    expect(name).not.toMatch(/flex:\s*none/);
    expect(name).toMatch(/min-width:\s*0/);
    expect(name).toMatch(/overflow:\s*hidden/);
    expect(name).toMatch(/text-overflow:\s*ellipsis/);
    expect(directory).toMatch(/flex:\s*0 1000 auto/);
  });
});
