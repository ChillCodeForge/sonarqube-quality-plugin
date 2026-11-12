import React from 'react';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchMutationReport, fetchMutationSummary } from './api';
import MutationTesting from './MutationTesting';

vi.mock('./api', () => ({
  fetchMutationReport: vi.fn(),
  fetchMutationSummary: vi.fn(),
}));

const completeSummary = {
  score: 95,
  total: 6,
  killed: 3,
  survived: 2,
  noCoverage: 0,
  timeout: 0,
  ignored: 0,
  tool: 'stryker',
  language: 'typescript',
};

const mutant = (id: string, status: string, mutatorName: string) => ({
  id,
  mutatorName,
  replacement: 'false',
  status,
  location: { start: { line: 4, column: 2 } },
});

const report = {
  timestamp: [2026, 10, 3, 1, 39],
  files: [
    {
      path: 'src/clean.ts',
      metrics: { score: 100, total: 1, killed: 1 },
      mutants: [mutant('clean-1', 'KILLED', 'BooleanLiteral')],
    },
    {
      path: 'src/checks/mutant.ts',
      metrics: { score: 50, total: 4, killed: 2, survived: 2 },
      mutants: [
        mutant('m-1', 'SURVIVED', 'Regex'),
        mutant('m-2', 'SURVIVED', 'BlockStatement'),
        mutant('m-3', 'KILLED', 'StringLiteral'),
        mutant('m-4', 'KILLED', 'ArrowFunction'),
      ],
    },
  ],
};

const detail = () => screen.getByRole('region', { name: /^Mutants in / });
const tab = (name: RegExp) => screen.getByRole('tab', { name });

describe('MutationTesting', () => {
  beforeEach(() => {
    vi.mocked(fetchMutationSummary).mockReset();
    vi.mocked(fetchMutationReport).mockReset();
    vi.mocked(fetchMutationSummary).mockResolvedValue(completeSummary);
    vi.mocked(fetchMutationReport).mockResolvedValue({ files: [] });
  });

  afterEach(() => vi.restoreAllMocks());

  it('loads the branch report and opens on the file with the most survivors', async () => {
    vi.mocked(fetchMutationReport).mockResolvedValueOnce(report);
    render(<MutationTesting projectKey="mutation-project" branch="feature/x" />);

    expect(screen.getByText(/Loading mutation testing results/)).toBeDefined();
    expect(await screen.findByText('Mutation Testing')).toBeDefined();
    expect(fetchMutationSummary).toHaveBeenCalledWith('mutation-project', 'feature/x');
    expect(fetchMutationReport).toHaveBeenCalledWith('mutation-project', 'feature/x');

    expect(screen.getByText('stryker').parentElement?.textContent).toMatch(
      /^stryker · 6 mutants in 2 files · report of .*2026/,
    );
    expect(
      screen.getByRole('img', { name: '3 killed, 2 survived, 1 unknown' }).className,
    ).toContain('cq-bar-large');
    expect(screen.getByRole('region', { name: 'Mutants in src/checks/mutant.ts' })).toBeDefined();
    expect(tab(/Survived/).getAttribute('aria-selected')).toBe('true');
    expect(within(detail()).getByText('Regex')).toBeDefined();
    expect(within(detail()).queryByText('StringLiteral')).toBeNull();
  });

  it('switches the status shown, then the file, and starts the new file on its own default', async () => {
    vi.mocked(fetchMutationReport).mockResolvedValueOnce(report);
    render(<MutationTesting projectKey="mutation-project" />);
    await screen.findByText('Mutation Testing');

    fireEvent.click(tab(/Killed/));
    expect(within(detail()).getByText('StringLiteral')).toBeDefined();
    expect(within(detail()).queryByText('Regex')).toBeNull();

    fireEvent.click(tab(/All/));
    expect(within(detail()).getAllByRole('listitem')).toHaveLength(4);

    fireEvent.click(screen.getByRole('button', { name: /clean\.ts/ }));
    expect(screen.getByRole('region', { name: 'Mutants in src/clean.ts' })).toBeDefined();
    expect(tab(/All/).getAttribute('aria-selected')).toBe('true');
    expect(within(detail()).getByText('BooleanLiteral')).toBeDefined();
  });

  it('filters and sorts the file list, falling back to the first file left', async () => {
    vi.mocked(fetchMutationReport).mockResolvedValueOnce(report);
    render(<MutationTesting projectKey="mutation-project" />);
    await screen.findByText('Mutation Testing');
    const fileNames = () =>
      within(screen.getByRole('complementary', { name: 'Files' }))
        .getAllByRole('button')
        .map((button) => button.getAttribute('title'));

    expect(fileNames()).toEqual(['src/checks/mutant.ts', 'src/clean.ts']);

    fireEvent.change(screen.getByLabelText('Sort files'), { target: { value: 'path' } });
    expect(fileNames()).toEqual(['src/checks/mutant.ts', 'src/clean.ts']);
    fireEvent.change(screen.getByLabelText('Sort files'), { target: { value: 'mutants' } });
    expect(fileNames()).toEqual(['src/checks/mutant.ts', 'src/clean.ts']);

    fireEvent.change(screen.getByLabelText('Filter files'), { target: { value: 'clean' } });
    expect(fileNames()).toEqual(['src/clean.ts']);
    expect(screen.getByRole('region', { name: 'Mutants in src/clean.ts' })).toBeDefined();

    fireEvent.change(screen.getByLabelText('Filter files'), { target: { value: 'nowhere' } });
    expect(screen.getByText('No file matches “nowhere”')).toBeDefined();
    expect(screen.queryByRole('region', { name: /^Mutants in / })).toBeNull();
  });

  it.each([
    [95, 'A'],
    [89.9, 'B'],
    [74.9, 'C'],
    [59.9, 'D'],
    [39.9, 'E'],
  ])('rates a score of %s as %s', async (score, rating) => {
    vi.mocked(fetchMutationSummary).mockResolvedValueOnce({ ...completeSummary, score });
    render(<MutationTesting projectKey="rating-project" />);

    expect((await screen.findByTitle(`Rating ${rating}`)).className).toContain('cq-rating-large');
    expect(screen.getByText(`${score.toFixed(1)}%`)).toBeDefined();
  });

  it('shows what it can for an older report that omits file metrics and mutants', async () => {
    vi.mocked(fetchMutationReport).mockResolvedValueOnce({
      files: [{ path: 'src/legacy.ts', language: 'typescript' }],
    });
    render(<MutationTesting projectKey="legacy-report-project" />);

    await screen.findByText('Mutation Testing');
    expect(screen.getByText('stryker').parentElement?.textContent).toBe(
      'stryker · 6 mutants in 1 file',
    );
    expect(within(detail()).getByText('N/A')).toBeDefined();
    expect(within(detail()).queryByTitle(/^Rating /)).toBeNull();
    expect(tab(/All/).textContent).toBe('All0');
    expect(screen.getByText('No mutants to display')).toBeDefined();
  });

  it('says a report lists no files, whether the download is empty, absent or misshapen', async () => {
    vi.mocked(fetchMutationSummary).mockResolvedValue({
      ...completeSummary,
      score: 0,
      total: 0,
      killed: 0,
      survived: 0,
    });
    vi.mocked(fetchMutationReport).mockResolvedValueOnce(null);
    const { unmount } = render(<MutationTesting projectKey="no-files-project" />);

    expect(await screen.findByText('This report lists no files')).toBeDefined();
    expect(screen.getByRole('img', { name: 'No mutants' })).toBeDefined();
    unmount();

    vi.mocked(fetchMutationReport).mockResolvedValueOnce({ files: { 'src/app.ts': [] } });
    render(<MutationTesting projectKey="raw-stryker-project" />);
    expect(await screen.findByText('This report lists no files')).toBeDefined();
  });

  it('names the branch when it has no mutation report', async () => {
    vi.mocked(fetchMutationSummary).mockResolvedValueOnce(null);
    render(<MutationTesting projectKey="empty-project" branch="release" />);

    expect(await screen.findByText('No mutation report for release')).toBeDefined();
    expect(screen.getByText('scripts/upload-mutation-report.sh')).toBeDefined();
  });

  it('logs a rejected request, says the report did not load, and loads it on a retry', async () => {
    vi.mocked(fetchMutationSummary).mockRejectedValueOnce(new Error('offline'));
    const error = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(<MutationTesting projectKey="offline-project" />);

    expect((await screen.findByRole('alert')).textContent).toContain(
      'The mutation report did not load',
    );
    expect(error).toHaveBeenCalledWith('Failed to load mutation data:', expect.any(Error));

    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('Mutation Testing')).toBeDefined();
    expect(fetchMutationSummary).toHaveBeenCalledTimes(2);
  });
});
