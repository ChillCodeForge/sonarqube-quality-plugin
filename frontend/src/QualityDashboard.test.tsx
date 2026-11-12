import React from 'react';
import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchQualityHistory, fetchQualityMetrics } from './api';
import QualityDashboard from './QualityDashboard';

const captureHistory = vi.hoisted(() => vi.fn());

vi.mock('./api', () => ({
  fetchQualityHistory: vi.fn(),
  fetchQualityMetrics: vi.fn(),
}));

vi.mock('./components/HistoryChart', () => ({
  default: ({ history }: { history: unknown }) => {
    captureHistory(history);

    return null;
  },
}));

const tile = (label: string) => screen.getByText(label).parentElement as HTMLElement;

const issueCount = (label: string) =>
  (screen.getByText(label).parentElement as HTMLElement).querySelector('.cq-severity-count');

describe('QualityDashboard', () => {
  beforeEach(() => {
    captureHistory.mockClear();
    vi.mocked(fetchQualityMetrics).mockReset();
    vi.mocked(fetchQualityHistory).mockReset();
    vi.mocked(fetchQualityMetrics).mockResolvedValue([]);
    vi.mocked(fetchQualityHistory).mockResolvedValue([]);
  });

  afterEach(() => vi.restoreAllMocks());

  it('shows loading while both Sonar requests are pending, then renders the measures', async () => {
    type Metrics = Awaited<ReturnType<typeof fetchQualityMetrics>>;

    type History = Awaited<ReturnType<typeof fetchQualityHistory>>;

    let finishMetrics!: (metrics: Metrics) => void;
    let finishHistory!: (history: History) => void;
    vi.mocked(fetchQualityMetrics).mockReturnValue(
      new Promise<Metrics>((resolve) => {
        finishMetrics = resolve;
      }),
    );
    vi.mocked(fetchQualityHistory).mockReturnValue(
      new Promise<History>((resolve) => {
        finishHistory = resolve;
      }),
    );

    render(<QualityDashboard componentKey="quality-project" />);
    expect(screen.getByText(/Loading quality dashboard/)).toBeDefined();

    finishMetrics([
      { key: 'reliability_rating', value: 2 },
      { key: 'security_rating', value: 1 },
      { key: 'sqale_rating', value: 9 },
      { key: 'coverage', value: 91.4 },
      { key: 'duplicated_lines_density', value: 2 },
      { key: 'mutation_score', value: 84 },
      { key: 'blocker_violations', value: 1 },
      { key: 'major_violations', value: 3 },
    ]);
    finishHistory([
      { date: '2026-10-21T08:00:00+0000', coverage: 91.4, mutation_score: 84 },
      { date: '2026-10-20T08:00:00+0000', coverage: 90 },
    ]);

    expect(await screen.findByText('Quality Dashboard')).toBeDefined();
    expect(fetchQualityMetrics).toHaveBeenCalledWith('quality-project');
    expect(fetchQualityHistory).toHaveBeenCalledWith('quality-project');
    expect(screen.getByText(/^Last analysis .*2026/)).toBeDefined();

    expect(tile('Reliability').textContent).toBe('ReliabilityB');
    expect(tile('Reliability').getAttribute('data-rating')).toBe('B');
    expect(tile('Security').getAttribute('data-rating')).toBe('A');
    expect(tile('Maintainability').textContent).toBe('Maintainability–');
    expect(tile('Maintainability').getAttribute('data-rating')).toBeNull();
    expect(tile('Coverage').textContent).toBe('Coverage91.4%');
    expect(tile('Coverage').getAttribute('data-rating')).toBeNull();
    expect(tile('Duplications').textContent).toBe('Duplications2.0%');
    expect(tile('Mutation score').textContent).toBe('Mutation score84.0%Rating B');
    expect(tile('Mutation score').getAttribute('data-rating')).toBe('B');

    expect(screen.getByText('2 analyses in the last 30 days')).toBeDefined();
    expect(captureHistory).toHaveBeenLastCalledWith([
      { date: '2026-10-21T08:00:00+0000', coverage: 91.4, mutation_score: 84 },
      { date: '2026-10-20T08:00:00+0000', coverage: 90 },
    ]);

    expect(screen.getByText('Open issues by severity').textContent).toBe(
      'Open issues by severity4',
    );
    expect(issueCount('Blocker')?.textContent).toBe('1');
    expect(issueCount('Major')?.className).toBe('cq-severity-count');
    expect(issueCount('Minor')?.className).toBe('cq-severity-count cq-zero');
  });

  it('shows a dash, never a made-up value, for a measure SonarQube did not return', async () => {
    vi.mocked(fetchQualityHistory).mockResolvedValueOnce([
      { date: '2026-10-21T08:00:00+0000', coverage: 91.4 },
    ]);
    render(<QualityDashboard componentKey="empty-project" />);

    expect(await screen.findByText('Quality Dashboard')).toBeDefined();
    expect(screen.getAllByText('–')).toHaveLength(6);
    expect(screen.queryByText('A')).toBeNull();
    expect(screen.getByText('1 analysis in the last 30 days')).toBeDefined();
    expect(issueCount('Info')?.textContent).toBe('0');
  });

  it('logs failed loading requests and still leaves the loading state', async () => {
    vi.mocked(fetchQualityMetrics).mockRejectedValueOnce(new Error('offline'));
    const error = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    render(<QualityDashboard componentKey="offline-project" />);

    expect(await screen.findByText('Quality Dashboard')).toBeDefined();
    expect(screen.getByText('Project health at a glance')).toBeDefined();
    expect(error).toHaveBeenCalledWith('Failed to load quality data:', expect.any(Error));
  });
});
