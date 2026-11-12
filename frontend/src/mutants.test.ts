import { describe, expect, it } from 'vitest';
import {
  countsFromTotals,
  filterFiles,
  formatCount,
  formatPercent,
  plural,
  ratingOf,
  reportDate,
  sortFiles,
  statusLabel,
  statusOf,
  sumCounts,
  toFileView,
  type Mutant,
} from './mutants';

const mutant = (id: string, status: string): Mutant => ({ id, mutatorName: 'Regex', status });

describe('mutant statuses', () => {
  it.each([
    ['KILLED', 'killed', 'Killed'],
    ['SURVIVED', 'survived', 'Survived'],
    ['NO_COVERAGE', 'noCoverage', 'No coverage'],
    ['TIMEOUT', 'timeout', 'Timeout'],
    ['IGNORED', 'ignored', 'Ignored'],
    ['RUNTIME_ERROR', 'unknown', 'Runtime error'],
    ['UNKNOWN', 'unknown', 'Unknown'],
    ['CUSTOM_STATUS', 'unknown', 'CUSTOM_STATUS'],
  ])('reads %s as %s, labeled %s', (status, key, label) => {
    expect(statusOf(status)).toBe(key);
    expect(statusLabel(status)).toBe(label);
  });

  it('counts what a total holds beyond its named statuses as unknown', () => {
    const counts = countsFromTotals({ total: 10, killed: 6, survived: 2, timeout: 1 });

    expect(counts).toEqual({
      killed: 6,
      timeout: 1,
      survived: 2,
      noCoverage: 0,
      unknown: 1,
      ignored: 0,
    });
    expect(sumCounts(counts)).toBe(10);
    expect(countsFromTotals({ killed: 3 }).unknown).toBe(0);
  });
});

describe('ratingOf', () => {
  it.each([
    [95, 'A'],
    [90, 'A'],
    [89.9, 'B'],
    [75, 'B'],
    [74.9, 'C'],
    [60, 'C'],
    [59.9, 'D'],
    [40, 'D'],
    [39.9, 'E'],
  ])('rates %s as %s', (score, rating) => {
    expect(ratingOf(score)).toBe(rating);
  });
});

describe('toFileView', () => {
  it('counts a file from its mutants and splits its path', () => {
    const view = toFileView({
      path: 'src/measure/steps.core.mjs',
      metrics: { score: 84.3, killed: 99 },
      mutants: [mutant('1', 'KILLED'), mutant('2', 'SURVIVED'), mutant('3', 'UNKNOWN')],
    });

    expect(view.directory).toBe('src/measure/');
    expect(view.name).toBe('steps.core.mjs');
    expect(view.total).toBe(3);
    expect(view.counts.killed).toBe(1);
    expect(view.counts.unknown).toBe(1);
    expect(view.rating).toBe('B');
  });

  it('orders mutants by line then column, with one that has no location last', () => {
    const at = (id: string, line: number, column: number): Mutant => ({
      ...mutant(id, 'SURVIVED'),
      location: { start: { line, column } },
    });
    const view = toFileView({
      path: 'a.ts',
      mutants: [at('c', 63, 22), mutant('nowhere', 'SURVIVED'), at('b', 63, 7), at('a', 19, 43)],
    });

    expect(view.mutants.map((entry) => entry.id)).toEqual(['a', 'b', 'c', 'nowhere']);
  });

  it('falls back to the metrics, then to nothing, for a file without mutants', () => {
    const fromMetrics = toFileView({ path: 'legacy.ts', metrics: { total: 4, survived: 4 } });
    const bare = toFileView({ path: 'bare.ts' });

    expect(fromMetrics.directory).toBe('');
    expect(fromMetrics.counts.survived).toBe(4);
    expect(fromMetrics.score).toBeNull();
    expect(fromMetrics.rating).toBeNull();
    expect(bare.total).toBe(0);
    expect(bare.mutants).toEqual([]);
  });
});

describe('file lists', () => {
  const files = [
    toFileView({ path: 'b.ts', metrics: { score: 50, total: 9, survived: 1, killed: 8 } }),
    toFileView({ path: 'a.ts', metrics: { score: 90, total: 20, survived: 1, killed: 19 } }),
    toFileView({ path: 'src/C.ts', metrics: { total: 3, survived: 3 } }),
  ];
  const paths = (sorted: typeof files) => sorted.map((file) => file.path);

  it('sorts by survivors, score, mutant count and path without touching the input', () => {
    expect(paths(sortFiles(files, 'survivors'))).toEqual(['src/C.ts', 'a.ts', 'b.ts']);
    expect(paths(sortFiles(files, 'score'))).toEqual(['b.ts', 'a.ts', 'src/C.ts']);
    expect(paths(sortFiles(files, 'mutants'))).toEqual(['a.ts', 'b.ts', 'src/C.ts']);
    expect(paths(sortFiles(files, 'path'))).toEqual(['a.ts', 'b.ts', 'src/C.ts']);
    expect(paths(files)).toEqual(['b.ts', 'a.ts', 'src/C.ts']);
  });

  it('puts a file with no score after every scored one, wherever it started', () => {
    const [scored, , unscored] = files;

    expect(paths(sortFiles([unscored, scored], 'score'))).toEqual(['b.ts', 'src/C.ts']);
    expect(paths(sortFiles([scored, unscored], 'score'))).toEqual(['b.ts', 'src/C.ts']);
  });

  it('filters on any part of the path, whatever its case', () => {
    expect(paths(filterFiles(files, ' SRC/c '))).toEqual(['src/C.ts']);
    expect(filterFiles(files, '  ')).toBe(files);
  });
});

describe('reportDate', () => {
  it('reads the backend timestamp array and an ISO string, and nothing else', () => {
    expect(reportDate([2026, 10, 3, 1, 39])?.getMonth()).toBe(9);
    expect(reportDate('2026-10-03T01:39:17Z')?.getFullYear()).toBe(2026);
    expect(reportDate('not a date')).toBeNull();
    expect(reportDate([2026, 10])).toBeNull();
    expect(reportDate(undefined)).toBeNull();
  });
});

describe('formatting', () => {
  it('groups thousands, keeps one decimal, and counts with the right noun', () => {
    expect(formatCount(19559)).toBe('19,559');
    expect(formatPercent(89.05)).toBe('89.0%');
    expect(formatPercent(0)).toBe('0.0%');
    expect(formatPercent(100)).toBe('100%');
    expect(plural(1, 'mutant')).toBe('1 mutant');
    expect(plural(1938, 'mutant')).toBe('1,938 mutants');
  });
});
