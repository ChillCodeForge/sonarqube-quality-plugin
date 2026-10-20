import React from 'react';
import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchQualityHistory, fetchQualityMetrics } from './api';
import QualityDashboard from './QualityDashboard';

const chartCapture = vi.hoisted(() => ({ areaData: null as unknown }));

vi.mock('./api', () => ({
  fetchQualityHistory: vi.fn(),
  fetchQualityMetrics: vi.fn(),
}));

vi.mock('recharts', () => {
  const withChildren = ({ children }: { children?: React.ReactNode }) => children;
  const empty = () => null;
  const areaChart = ({ data }: { data: unknown }) => {
    chartCapture.areaData = data;
    return null;
  };
  return {
    AreaChart: areaChart,
    Bar: empty,
    BarChart: empty,
    CartesianGrid: empty,
    Cell: empty,
    Legend: empty,
    Pie: empty,
    PieChart: empty,
    ResponsiveContainer: withChildren,
    Tooltip: empty,
    XAxis: empty,
    YAxis: empty,
    Area: empty,
  };
});

describe('QualityDashboard', () => {
  beforeEach(() => {
    chartCapture.areaData = null;
    vi.mocked(fetchQualityMetrics).mockReset();
    vi.mocked(fetchQualityHistory).mockReset();
    vi.mocked(fetchQualityMetrics).mockResolvedValue([]);
    vi.mocked(fetchQualityHistory).mockResolvedValue([]);
  });

  afterEach(() => vi.restoreAllMocks());

  it('shows loading while both Sonar requests are pending, then renders metric values', async () => {
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
      { key: 'reliability_rating', value: 'B', rating: 'B', trend: 'down' },
      { key: 'security_rating', value: 'A', rating: 'A', trend: 'stable' },
      { key: 'sqale_rating', value: 'C', rating: 'C', trend: 'up' },
      { key: 'coverage', value: 91.4, rating: 'A', trend: 'up' },
      { key: 'duplicated_lines_density', value: 2, rating: 'A', trend: 'stable' },
      { key: 'mutation_score', value: 84, rating: 'B', trend: 'up' },
      { key: 'blocker_violations', value: 1 },
      { key: 'critical_violations', value: 2 },
      { key: 'major_violations', value: 3 },
      { key: 'minor_violations', value: 4 },
      { key: 'info_violations', value: 5 },
    ]);
    finishHistory([{ date: '2026-10-20', coverage: 91.4, mutation_score: 84 }]);

    expect(await screen.findByText('Quality Overview')).toBeDefined();
    expect(chartCapture.areaData).toEqual([
      { date: '2026-10-20', coverage: 91.4, mutation_score: 84 },
    ]);
    expect(screen.getByText('91.4%')).toBeDefined();
    expect(screen.getByText('2%')).toBeDefined();
    expect(screen.getByText('84%')).toBeDefined();
    expect(fetchQualityMetrics).toHaveBeenCalledWith('quality-project');
    expect(fetchQualityHistory).toHaveBeenCalledWith('quality-project');
    expect(screen.getByText('Issues by Severity')).toBeDefined();
  });

  it('uses fallback values when no metrics are returned', async () => {
    render(<QualityDashboard componentKey="empty-project" />);

    expect(await screen.findByText('Quality Overview')).toBeDefined();
    expect(screen.getAllByText('A').length).toBeGreaterThan(0);
    expect(screen.getAllByText('0%').length).toBe(3);
  });

  it('logs failed loading requests and still leaves the loading state', async () => {
    vi.mocked(fetchQualityMetrics).mockRejectedValueOnce(new Error('offline'));
    const error = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    render(<QualityDashboard componentKey="offline-project" />);

    expect(await screen.findByText('Quality Overview')).toBeDefined();
    expect(error).toHaveBeenCalledWith('Failed to load quality data:', expect.any(Error));
  });
});
