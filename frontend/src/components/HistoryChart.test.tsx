import React from 'react';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HistoryChart from './HistoryChart';

const chart = vi.hoisted(() => ({
  data: null as unknown,
  lines: [] as { dataKey: string; strokeDasharray?: string }[],
  yDomain: null as unknown,
  dayTick: '',
  valueTick: '',
  tooltipLabel: '',
  tooltipItem: null as unknown,
}));

vi.mock('recharts', () => {
  const withChildren = ({ children }: { children?: React.ReactNode }) => children;

  return {
    CartesianGrid: () => null,
    ResponsiveContainer: withChildren,
    LineChart: ({ data, children }: { data: unknown; children?: React.ReactNode }) => {
      chart.data = data;

      return children;
    },
    Line: (props: { dataKey: string; strokeDasharray?: string }) => {
      chart.lines.push({ dataKey: props.dataKey, strokeDasharray: props.strokeDasharray });

      return null;
    },
    XAxis: ({ tickFormatter }: { tickFormatter: (time: number) => string }) => {
      chart.dayTick = tickFormatter(Date.UTC(2026, 9, 20, 12));

      return null;
    },
    YAxis: (props: { domain: unknown; tickFormatter: (value: number) => string }) => {
      chart.yDomain = props.domain;
      chart.valueTick = props.tickFormatter(90);

      return null;
    },
    Tooltip: (props: {
      labelFormatter: (time: number) => string;
      formatter: (value: number, name: string) => [string, string];
    }) => {
      chart.tooltipLabel = props.labelFormatter(Date.UTC(2026, 9, 20, 12));
      chart.tooltipItem = props.formatter(89.1, 'Mutation score');

      return null;
    },
  };
});

describe('HistoryChart', () => {
  beforeEach(() => {
    chart.data = null;
    chart.lines = [];
  });

  it('draws both series over time, labels them with their latest value, and dashes the second', () => {
    render(
      <HistoryChart
        history={[
          { date: '2026-10-21T00:00:00+0000', coverage: 100, mutation_score: 89.1 },
          { date: '2026-10-20T00:00:00+0000', coverage: 99.5, mutation_score: 88 },
        ]}
      />,
    );

    expect(chart.data).toEqual([
      { time: Date.UTC(2026, 9, 20), coverage: 99.5, mutation_score: 88 },
      { time: Date.UTC(2026, 9, 21), coverage: 100, mutation_score: 89.1 },
    ]);
    expect(screen.getByText('Coverage 100%')).toBeDefined();
    expect(screen.getByText('Mutation score 89.1%')).toBeDefined();
    expect(chart.lines).toEqual([
      { dataKey: 'coverage', strokeDasharray: undefined },
      { dataKey: 'mutation_score', strokeDasharray: '6 4' },
    ]);
    expect(chart.yDomain).toEqual([80, 100]);
    expect(chart.dayTick).toContain('20');
    expect(chart.valueTick).toBe('90%');
    expect(chart.tooltipLabel).toContain('2026');
    expect(chart.tooltipItem).toEqual(['89.1%', 'Mutation score']);
  });

  it('leaves out a series the project has no values for', () => {
    render(<HistoryChart history={[{ date: '2026-10-20T00:00:00+0000', coverage: 91.4 }]} />);

    expect(screen.getByText('Coverage 91.4%')).toBeDefined();
    expect(screen.queryByText(/Mutation score/)).toBeNull();
    expect(chart.lines.map((line) => line.dataKey)).toEqual(['coverage']);
  });

  it('says so instead of drawing empty axes', () => {
    render(<HistoryChart history={[{ date: '2026-10-20T00:00:00+0000' }]} />);

    expect(screen.getByText('No analyses in the last 30 days')).toBeDefined();
    expect(chart.data).toBeNull();
  });
});
