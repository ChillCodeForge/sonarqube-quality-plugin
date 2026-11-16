import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
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
      'Open issues by severity –',
    );
    expect(issueCount('Blocker')?.textContent).toBe('1');
    expect(issueCount('Critical')?.textContent).toBe('–');
    expect(issueCount('Major')?.className).toBe('cq-severity-count');
    expect(issueCount('Minor')?.className).toBe('cq-severity-count cq-zero');
    expect(issueCount('Minor')?.textContent).toBe('–');
  });

  it('shows a dash, never a made-up value, for a measure SonarQube did not return', async () => {
    vi.mocked(fetchQualityHistory).mockResolvedValueOnce([
      { date: '2026-10-21T08:00:00+0000', coverage: 91.4 },
    ]);
    render(<QualityDashboard componentKey="empty-project" />);

    expect(await screen.findByText('Quality Dashboard')).toBeDefined();
    expect(screen.getAllByText('–')).toHaveLength(12);
    expect(screen.queryByText('A')).toBeNull();
    expect(screen.getByText('1 analysis in the last 30 days')).toBeDefined();
    expect(issueCount('Info')?.textContent).toBe('–');
    expect(screen.getByText('Open issues by severity').textContent).toBe(
      'Open issues by severity –',
    );
  });

  it('keeps a true zero as 0 but shows a dash, and no total, when a severity measure is missing', async () => {
    vi.mocked(fetchQualityMetrics).mockResolvedValueOnce([
      { key: 'blocker_violations', value: 0 },
      { key: 'critical_violations', value: 2 },
      { key: 'major_violations', value: 0 },
      { key: 'minor_violations', value: 1 },
    ]);
    render(<QualityDashboard componentKey="partial-project" />);

    expect(await screen.findByText('Quality Dashboard')).toBeDefined();
    expect(issueCount('Blocker')?.textContent).toBe('0');
    expect(issueCount('Critical')?.textContent).toBe('2');
    expect(issueCount('Info')?.textContent).toBe('–');
    expect(screen.getByText('Open issues by severity').textContent).toBe(
      'Open issues by severity –',
    );
  });

  it('sums the total only when every severity is known, zeros included', async () => {
    vi.mocked(fetchQualityMetrics).mockResolvedValueOnce(
      ['blocker', 'critical', 'major', 'minor', 'info'].map((name, index) => ({
        key: `${name}_violations`,
        value: index === 0 ? 0 : index,
      })),
    );
    render(<QualityDashboard componentKey="full-project" />);

    expect(await screen.findByText('Quality Dashboard')).toBeDefined();
    expect(screen.getByText('Open issues by severity').textContent).toBe(
      'Open issues by severity 10',
    );
  });

  describe('switching projects', () => {
    type Metrics = Awaited<ReturnType<typeof fetchQualityMetrics>>;

    const deferred = () => {
      let resolve: (metrics: Metrics) => void = () => undefined;

      const promise = new Promise<Metrics>((done) => {
        resolve = done;
      });

      return { promise, resolve };
    };

    it('shows loading, never the previous project, while the next one loads', async () => {
      const next = deferred();
      vi.mocked(fetchQualityMetrics)
        .mockResolvedValueOnce([{ key: 'coverage', value: 11 }])
        .mockReturnValueOnce(next.promise);
      const view = render(<QualityDashboard componentKey="project-a" />);

      await waitFor(() => expect(tile('Coverage').textContent).toBe('Coverage11.0%'));

      view.rerender(<QualityDashboard componentKey="project-b" />);
      expect(screen.getByText(/Loading quality dashboard/)).toBeDefined();
      expect(screen.queryByText(/Quality Dashboard/)).toBeNull();

      next.resolve([{ key: 'coverage', value: 22 }]);
      await waitFor(() => expect(tile('Coverage').textContent).toBe('Coverage22.0%'));
    });

    it('lets a slow answer for the old project never override the current one', async () => {
      const slowA = deferred();
      const fastB = deferred();
      vi.mocked(fetchQualityMetrics)
        .mockReturnValueOnce(slowA.promise)
        .mockReturnValueOnce(fastB.promise);
      const view = render(<QualityDashboard componentKey="project-a" />);

      view.rerender(<QualityDashboard componentKey="project-b" />);
      fastB.resolve([{ key: 'coverage', value: 22 }]);
      await waitFor(() => expect(tile('Coverage').textContent).toBe('Coverage22.0%'));

      slowA.resolve([{ key: 'coverage', value: 11 }]);
      await new Promise((resolve) => setTimeout(resolve, 0));
      expect(tile('Coverage').textContent).toBe('Coverage22.0%');
      expect(screen.queryByText('11.0%')).toBeNull();
    });

    it('drops a failed answer for the old project and keeps loading the current one', async () => {
      const error = vi.spyOn(console, 'error').mockImplementation(() => undefined);
      const next = deferred();
      let failA: (reason: Error) => void = () => undefined;
      vi.mocked(fetchQualityMetrics)
        .mockReturnValueOnce(
          new Promise<Metrics>((_resolve, reject) => {
            failA = reject;
          }),
        )
        .mockReturnValueOnce(next.promise);
      const view = render(<QualityDashboard componentKey="project-a" />);

      view.rerender(<QualityDashboard componentKey="project-b" />);
      failA(new Error('late'));
      await new Promise((resolve) => setTimeout(resolve, 0));

      expect(screen.getByText(/Loading quality dashboard/)).toBeDefined();
      expect(error).not.toHaveBeenCalled();
      next.resolve([]);
      await waitFor(() => expect(screen.queryByText(/Loading quality dashboard/)).toBeNull());
    });

    it('ignores an answer that arrives after unmount', async () => {
      const error = vi.spyOn(console, 'error').mockImplementation(() => undefined);
      const pending = deferred();
      vi.mocked(fetchQualityMetrics).mockReturnValueOnce(pending.promise);
      render(<QualityDashboard componentKey="project-a" />).unmount();

      pending.resolve([]);
      await new Promise((resolve) => setTimeout(resolve, 0));
      expect(error).not.toHaveBeenCalled();
    });
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
