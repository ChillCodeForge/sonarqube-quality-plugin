import { describe, expect, it } from 'vitest';
import { chartFloor, latestValue, parseSonarDate, toChartPoints } from './history';

describe('analysis history', () => {
  it('parses the offset SonarQube writes without a colon', () => {
    expect(parseSonarDate('2026-10-13T04:07:22+0200')).toBe(Date.UTC(2026, 9, 13, 2, 7, 22));
    expect(parseSonarDate('2026-10-13T04:07:22Z')).toBe(Date.UTC(2026, 9, 13, 4, 7, 22));
  });

  it('orders points by time and drops a date it cannot read', () => {
    const points = toChartPoints([
      { date: '2026-10-02T00:00:00+0000', coverage: 91 },
      { date: 'yesterday', coverage: 5 },
      { date: '2026-10-01T00:00:00+0000', coverage: 90, mutation_score: 84 },
    ]);

    expect(points.map((point) => point.coverage)).toEqual([90, 91]);
    expect(latestValue(points, 'coverage')).toBe(91);
    expect(latestValue(points, 'mutation_score')).toBe(84);
    expect(latestValue([], 'coverage')).toBeNull();
  });

  it.each([
    [[{ time: 1, coverage: 100, mutation_score: 89.1 }], 80],
    [[{ time: 1, mutation_score: 81.5 }], 70],
    [[{ time: 1, coverage: 66.7 }], 60],
    [[{ time: 1, coverage: 1 }], 0],
    [[{ time: 1 }], 0],
  ])('starts the value axis below the lowest value of %j at %s', (points, floor) => {
    expect(chartFloor(points)).toBe(floor);
  });
});
