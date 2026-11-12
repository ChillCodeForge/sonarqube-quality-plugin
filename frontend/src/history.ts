import type { QualityHistoryPoint } from './api';

export type SeriesKey = 'coverage' | 'mutation_score';

export interface ChartPoint {
  time: number;
  coverage?: number;
  mutation_score?: number;
}

// SonarQube writes analysis dates as 2026-10-13T04:07:22+0200. The offset has
// no colon, which Date only parses reliably once one is put back.
export function parseSonarDate(date: string): number {
  return new Date(date.replace(/([+-]\d{2})(\d{2})$/, '$1:$2')).getTime();
}

export function toChartPoints(history: QualityHistoryPoint[]): ChartPoint[] {
  return history
    .map((point) => ({
      time: parseSonarDate(point.date),
      coverage: point.coverage,
      mutation_score: point.mutation_score,
    }))
    .filter((point) => Number.isFinite(point.time))
    .sort((a, b) => a.time - b.time);
}

export function latestValue(points: ChartPoint[], key: SeriesKey): number | null {
  for (let index = points.length - 1; index >= 0; index -= 1) {
    const value = points[index][key];

    if (value !== undefined) {
      return value;
    }
  }

  return null;
}

// Where the value axis starts: the ten below the lowest value, and one ten
// further when the lowest value would otherwise sit on the axis itself.
export function chartFloor(points: ChartPoint[]): number {
  const values = points.flatMap((point) =>
    [point.coverage, point.mutation_score].filter((value) => value !== undefined),
  );

  if (values.length === 0) {
    return 0;
  }

  const lowest = Math.min(...values);
  const floor = Math.floor(lowest / 10) * 10;

  return Math.max(0, lowest - floor < 2 ? floor - 10 : floor);
}
