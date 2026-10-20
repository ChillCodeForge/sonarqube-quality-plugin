import axios from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  fetchMutationReport,
  fetchMutationSummary,
  fetchQualityHistory,
  fetchQualityMetrics,
} from './api';

vi.mock('axios', () => ({ default: { get: vi.fn() } }));

describe('SonarQube API helpers', () => {
  beforeEach(() => vi.mocked(axios.get).mockReset());
  afterEach(() => vi.restoreAllMocks());

  it('fetches quality measures and returns an empty list when the component is absent', async () => {
    vi.mocked(axios.get).mockResolvedValueOnce({
      data: { component: { measures: [{ metric: 'coverage' }] } },
    });
    vi.mocked(axios.get).mockResolvedValueOnce({ data: {} });

    await expect(fetchQualityMetrics('app')).resolves.toEqual([{ metric: 'coverage' }]);
    await expect(fetchQualityMetrics('missing')).resolves.toEqual([]);
    expect(axios.get).toHaveBeenNthCalledWith(1, '/api/measures/component', {
      params: expect.objectContaining({
        component: 'app',
        metricKeys: expect.stringContaining('coverage'),
      }),
    });
  });

  it('returns no quality measures when the request fails', async () => {
    vi.mocked(axios.get).mockRejectedValueOnce(new Error('offline'));
    const error = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    await expect(fetchQualityMetrics('app')).resolves.toEqual([]);
    expect(error).toHaveBeenCalledWith('Failed to fetch quality metrics:', expect.any(Error));
  });

  it('joins Sonar history series by analysis date for the dashboard charts', async () => {
    vi.mocked(axios.get).mockResolvedValueOnce({
      data: {
        measures: [
          {
            metric: 'coverage',
            history: [
              { date: '2026-10-01T00:00:00+0200', value: '90.5' },
              { date: '2026-10-02T00:00:00+0200', value: '91.25' },
            ],
          },
          {
            metric: 'mutation_score',
            history: [{ date: '2026-10-01T00:00:00+0200', value: '84' }],
          },
        ],
      },
    });

    await expect(fetchQualityHistory('app')).resolves.toEqual([
      { date: '2026-10-01T00:00:00+0200', coverage: 90.5, mutation_score: 84 },
      { date: '2026-10-02T00:00:00+0200', coverage: 91.25 },
    ]);
  });

  it('ignores unrelated history metrics and non-numeric points', async () => {
    vi.mocked(axios.get).mockResolvedValueOnce({
      data: {
        measures: [
          { metric: 'ncloc', history: [{ date: '2026-10-01', value: '100' }] },
          {
            metric: 'coverage',
            history: [
              { date: '2026-10-02', value: null },
              { date: '2026-10-03', value: 'not-a-number' },
            ],
          },
          { metric: 'mutation_score' },
        ],
      },
    });

    await expect(fetchQualityHistory('app')).resolves.toEqual([
      { date: '2026-10-02' },
      { date: '2026-10-03' },
    ]);
  });

  it('handles absent history and request failures', async () => {
    vi.mocked(axios.get)
      .mockResolvedValueOnce({ data: {} })
      .mockRejectedValueOnce(new Error('offline'));
    vi.spyOn(console, 'error').mockImplementation(() => undefined);

    await expect(fetchQualityHistory('app')).resolves.toEqual([]);
    await expect(fetchQualityHistory('app')).resolves.toEqual([]);
  });

  it('maps mutation summary fields and returns null for missing reports or failures', async () => {
    vi.mocked(axios.get)
      .mockResolvedValueOnce({
        data: {
          mutationScore: 81,
          totalMutants: 11,
          killedMutants: 9,
          survivedMutants: 1,
          noCoverageMutants: 0,
          timeoutMutants: 0,
          ignoredMutants: 1,
          tool: 'stryker',
          language: 'typescript',
        },
      })
      .mockResolvedValueOnce({ data: { hasReport: false } })
      .mockResolvedValueOnce({ data: null })
      .mockRejectedValueOnce(new Error('offline'));
    vi.spyOn(console, 'error').mockImplementation(() => undefined);

    await expect(fetchMutationSummary('app')).resolves.toEqual({
      score: 81,
      total: 11,
      killed: 9,
      survived: 1,
      noCoverage: 0,
      timeout: 0,
      ignored: 1,
      tool: 'stryker',
      language: 'typescript',
    });
    await expect(fetchMutationSummary('app')).resolves.toBeNull();
    await expect(fetchMutationSummary('app')).resolves.toBeNull();
    await expect(fetchMutationSummary('app')).resolves.toBeNull();
  });

  it('fetches mutation reports and returns null if download fails', async () => {
    vi.mocked(axios.get)
      .mockResolvedValueOnce({ data: { files: { 'src/app.ts': [] } } })
      .mockRejectedValueOnce(new Error('offline'));
    vi.spyOn(console, 'error').mockImplementation(() => undefined);

    await expect(fetchMutationReport('app', 'feature')).resolves.toEqual({
      files: { 'src/app.ts': [] },
    });
    await expect(fetchMutationReport('app', 'feature')).resolves.toBeNull();
    expect(axios.get).toHaveBeenCalledWith('/api/chillcode_mutation/download', {
      params: { projectKey: 'app', branch: 'feature' },
    });
  });
});
