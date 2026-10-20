import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchMutationReport, fetchMutationSummary } from './api';
import MutationTesting from './MutationTesting';

const chartCallbacks = vi.hoisted(() => ({
  pieLabels: [] as string[],
  paddingAngle: null as number | null,
  tooltipResult: null as [string, string] | null,
}));

vi.mock('./api', () => ({
  fetchMutationReport: vi.fn(),
  fetchMutationSummary: vi.fn(),
}));

vi.mock('recharts', () => {
  const withChildren = ({ children }: { children?: React.ReactNode }) => children;
  const empty = () => null;
  return {
    Bar: empty,
    BarChart: empty,
    CartesianGrid: empty,
    Cell: empty,
    Legend: empty,
    Pie: ({
      data,
      label,
      paddingAngle,
    }: {
      data: { name: string; value: number }[];
      label: (entry: { name: string; percent: number }) => string;
      paddingAngle: number;
    }) => {
      const total = data.reduce((sum, entry) => sum + entry.value, 0);
      chartCallbacks.pieLabels = data.map((entry) =>
        label({ name: entry.name, percent: entry.value / total }),
      );
      chartCallbacks.paddingAngle = paddingAngle;
      return null;
    },
    PieChart: withChildren,
    ResponsiveContainer: withChildren,
    Tooltip: ({
      formatter,
    }: {
      formatter: (value: number | string, name: string) => [string, string];
    }) => {
      chartCallbacks.tooltipResult = formatter(2, 'Killed');
      return null;
    },
    XAxis: empty,
    YAxis: empty,
  };
});

const completeSummary = {
  score: 95,
  total: 3,
  killed: 2,
  survived: 1,
  noCoverage: 0,
  timeout: 0,
  ignored: 0,
  tool: 'stryker',
  language: 'typescript',
};

const reportWithFile = {
  files: [
    {
      path: 'src/mutant.ts',
      language: 'typescript',
      metrics: { score: 66.7, killed: 2, survived: 1, noCoverage: 0 },
      mutants: [
        {
          id: 'mutant-1',
          mutatorName: 'BooleanLiteral',
          replacement: 'false',
          status: 'KILLED',
          statusReason: 'Killed by test',
          coveredBy: ['MutationTest'],
          static: false,
        },
      ],
    },
  ],
};

describe('MutationTesting', () => {
  beforeEach(() => {
    chartCallbacks.pieLabels = [];
    chartCallbacks.paddingAngle = null;
    chartCallbacks.tooltipResult = null;
    vi.mocked(fetchMutationSummary).mockReset();
    vi.mocked(fetchMutationReport).mockReset();
    vi.mocked(fetchMutationSummary).mockResolvedValue(completeSummary);
    vi.mocked(fetchMutationReport).mockResolvedValue({ files: [] });
  });

  afterEach(() => vi.restoreAllMocks());

  it('loads branch-specific results and opens then closes a file mutant table', async () => {
    vi.mocked(fetchMutationReport).mockResolvedValueOnce(reportWithFile);
    render(<MutationTesting projectKey="mutation-project" branch="feature/x" />);

    expect(screen.getByText(/Loading mutation testing results/)).toBeDefined();
    expect(await screen.findByText('Mutation Testing')).toBeDefined();
    expect(chartCallbacks.pieLabels).toEqual(['Killed: 66.7%', 'Survived: 33.3%']);
    expect(chartCallbacks.tooltipResult).toEqual(['2 Mutants', 'Killed']);
    expect(chartCallbacks.paddingAngle).toBe(3);
    expect(screen.getByText('stryker · typescript · 3 mutants')).toBeDefined();
    expect(screen.getByText('Files (1)')).toBeDefined();
    expect(fetchMutationSummary).toHaveBeenCalledWith('mutation-project', 'feature/x');
    expect(fetchMutationReport).toHaveBeenCalledWith('mutation-project', 'feature/x');

    fireEvent.click(screen.getByText('src/mutant.ts'));
    expect(screen.getByText('Mutants in src/mutant.ts')).toBeDefined();
    expect(screen.getByText('mutant-1')).toBeDefined();
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(screen.queryByText('Mutants in src/mutant.ts')).toBeNull();
  });

  it.each([
    [95, 'A', 'var(--cq-green)'],
    [90, 'A', 'var(--cq-green)'],
    [89.9, 'B', 'var(--cq-blue)'],
    [75, 'B', 'var(--cq-blue)'],
    [74.9, 'C', 'var(--cq-amber)'],
    [60, 'C', 'var(--cq-amber)'],
    [59.9, 'D', 'var(--cq-red)'],
    [40, 'D', 'var(--cq-red)'],
    [39.9, 'E', 'var(--cq-red-deep)'],
  ])('shows rating %s as %s', async (score, rating, color) => {
    vi.mocked(fetchMutationSummary).mockResolvedValueOnce({ ...completeSummary, score });
    render(<MutationTesting projectKey="rating-project" />);

    expect(await screen.findByText(`${rating} rating`)).toBeDefined();
    expect(document.querySelector('.cq-gauge-card')?.getAttribute('style')).toContain(color);
    expect(screen.getByText(`${Number(score).toFixed(1)}%`)).toBeDefined();
  });

  it('uses optional file values when an older report omits file metrics and mutants', async () => {
    vi.mocked(fetchMutationReport).mockResolvedValueOnce({
      files: [{ path: 'src/legacy.ts', language: 'typescript' }],
    });
    render(<MutationTesting projectKey="legacy-report-project" />);

    expect(await screen.findByText('Files (1)')).toBeDefined();
    const row = screen.getByRole('row', { name: /src\/legacy\.ts/ });
    expect(row.textContent).toContain('N/A%');
    expect(row.textContent).toContain('0');

    fireEvent.click(screen.getByText('src/legacy.ts'));
    expect(screen.getByText('Mutants in src/legacy.ts')).toBeDefined();
    expect(screen.getByText('No mutants to display')).toBeDefined();
  });

  it('uses zero padding for a single nonzero mutant status', async () => {
    vi.mocked(fetchMutationSummary).mockResolvedValueOnce({
      ...completeSummary,
      total: 2,
      killed: 2,
      survived: 0,
    });
    render(<MutationTesting projectKey="single-status-project" />);

    expect(await screen.findByText('Mutation Testing')).toBeDefined();
    expect(chartCallbacks.pieLabels).toEqual(['Killed: 100.0%']);
    expect(chartCallbacks.paddingAngle).toBe(0);
  });

  it('shows the empty state when the mutation summary is absent', async () => {
    vi.mocked(fetchMutationSummary).mockResolvedValueOnce(null);
    render(<MutationTesting projectKey="empty-project" />);

    expect(
      await screen.findByText('No mutation testing data available for this project'),
    ).toBeDefined();
  });

  it('handles a report with no files and no mutants without rendering a file table', async () => {
    vi.mocked(fetchMutationSummary).mockResolvedValueOnce({
      ...completeSummary,
      score: 0,
      total: 0,
      killed: 0,
      survived: 0,
    });
    vi.mocked(fetchMutationReport).mockResolvedValueOnce(null);
    render(<MutationTesting projectKey="no-files-project" />);

    expect(await screen.findByText('Mutation Testing')).toBeDefined();
    expect(screen.getByText('Files (0)')).toBeDefined();
    expect(screen.getByText('No mutant data available')).toBeDefined();
  });

  it('logs a rejected request and exits the loading state', async () => {
    vi.mocked(fetchMutationSummary).mockRejectedValueOnce(new Error('offline'));
    const error = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(<MutationTesting projectKey="offline-project" />);

    expect(
      await screen.findByText('No mutation testing data available for this project'),
    ).toBeDefined();
    expect(error).toHaveBeenCalledWith('Failed to load mutation data:', expect.any(Error));
  });
});
