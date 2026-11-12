import React from 'react';
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { QualityHistoryPoint } from '../api';
import { chartFloor, latestValue, toChartPoints, type SeriesKey } from '../history';
import { formatPercent } from '../mutants';

interface HistoryChartProps {
  history: QualityHistoryPoint[];
}

// The second series is dashed as well as colored, so the two lines stay
// apart without relying on color.
const SERIES: readonly {
  readonly key: SeriesKey;
  readonly label: string;
  readonly color: string;
  readonly dash?: string;
}[] = [
  { key: 'coverage', label: 'Coverage', color: 'var(--cq-series-1)' },
  { key: 'mutation_score', label: 'Mutation score', color: 'var(--cq-series-2)', dash: '6 4' },
];

const AXIS_TICK = { fill: 'var(--cq-text-subtle)', fontSize: 12 };

function formatDay(time: number): string {
  return new Date(time).toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
}

function formatMoment(time: number): string {
  return new Date(time).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

const HistoryChart: React.FC<HistoryChartProps> = ({ history }) => {
  const points = toChartPoints(history);
  const series = SERIES.filter((entry) => latestValue(points, entry.key) !== null);

  if (series.length === 0) {
    return (
      <div className="cq-state">
        <p>No analyses in the last 30 days</p>
      </div>
    );
  }

  return (
    <div className="cq-chart">
      <ul className="cq-chart-legend">
        {series.map((entry) => (
          <li key={entry.key}>
            <svg viewBox="0 0 22 4" aria-hidden="true">
              <path d="M0 2h22" stroke={entry.color} strokeDasharray={entry.dash} />
            </svg>
            {/* SAFETY: series keeps only the entries latestValue found a value for. */}
            {`${entry.label} ${formatPercent(latestValue(points, entry.key) as number)}`}
          </li>
        ))}
      </ul>
      <ResponsiveContainer width="100%" height={240}>
        <LineChart data={points} margin={{ top: 8, right: 24, bottom: 0, left: 0 }}>
          <CartesianGrid vertical={false} stroke="var(--cq-line-soft)" />
          <XAxis
            dataKey="time"
            type="number"
            scale="time"
            domain={['dataMin', 'dataMax']}
            tickFormatter={formatDay}
            tick={AXIS_TICK}
            tickLine={false}
            axisLine={{ stroke: 'var(--cq-line-bold)' }}
            minTickGap={48}
          />
          <YAxis
            domain={[chartFloor(points), 100]}
            tickFormatter={(value: number) => `${value}%`}
            tick={AXIS_TICK}
            tickLine={false}
            axisLine={false}
            width={48}
          />
          <Tooltip
            labelFormatter={formatMoment}
            formatter={(value: number | string, name: string) => [`${value}%`, name]}
            contentStyle={{
              borderRadius: 8,
              border: '1px solid var(--cq-line)',
              background: 'var(--cq-card)',
            }}
            labelStyle={{ color: 'var(--cq-text-strong)', fontWeight: 600 }}
            itemStyle={{ color: 'var(--cq-text)' }}
          />
          {series.map((entry) => (
            <Line
              key={entry.key}
              dataKey={entry.key}
              name={entry.label}
              stroke={entry.color}
              strokeWidth={2}
              strokeDasharray={entry.dash}
              dot={false}
              connectNulls
              isAnimationActive={false}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
};

export default HistoryChart;
