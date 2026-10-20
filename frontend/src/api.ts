import axios from 'axios';

const API_BASE = '/api';

type QualityHistoryPoint = {
  date: string;
  coverage?: number;
  mutation_score?: number;
};

type SonarHistoryMeasure = {
  metric: string;
  history?: Array<{ date: string; value?: string | number | null }>;
};

export async function fetchQualityMetrics(componentKey: string): Promise<any[]> {
  try {
    const response = await axios.get(`${API_BASE}/measures/component`, {
      params: {
        component: componentKey,
        metricKeys: [
          'reliability_rating',
          'security_rating',
          'sqale_rating',
          'coverage',
          'duplicated_lines_density',
          'mutation_score',
          'blocker_violations',
          'critical_violations',
          'major_violations',
          'minor_violations',
          'info_violations',
        ].join(','),
      },
    });
    return response.data.component?.measures || [];
  } catch (error) {
    console.error('Failed to fetch quality metrics:', error);
    return [];
  }
}

export async function fetchQualityHistory(componentKey: string): Promise<QualityHistoryPoint[]> {
  try {
    const response = await axios.get(`${API_BASE}/measures/search_history`, {
      params: {
        component: componentKey,
        metrics: 'coverage,mutation_score',
        from: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
      },
    });
    const byDate = new Map<string, QualityHistoryPoint>();
    const measures: SonarHistoryMeasure[] = response.data.measures || [];

    for (const measure of measures) {
      if (measure.metric !== 'coverage' && measure.metric !== 'mutation_score') {
        continue;
      }
      for (const point of measure.history || []) {
        const row: QualityHistoryPoint = byDate.get(point.date) || { date: point.date };
        if (point.value != null) {
          const value = Number(point.value);
          if (Number.isFinite(value)) {
            row[measure.metric] = value;
          }
        }
        byDate.set(point.date, row);
      }
    }

    return [...byDate.values()];
  } catch (error) {
    console.error('Failed to fetch quality history:', error);
    return [];
  }
}

export async function fetchMutationSummary(
  projectKey: string,
  branch: string = 'main',
): Promise<any> {
  try {
    const response = await axios.get(`${API_BASE}/chillcode_mutation/summary`, {
      params: { projectKey, branch },
    });
    const data = response.data;
    if (!data || data.hasReport === false) {
      return null;
    }
    // Backend field names (mutationScore, totalMutants, killedMutants, ...)
    // don't match what MutationTesting.tsx expects (score, total, killed,
    // ...) - map them here so the component's summary.score etc. actually
    // resolve instead of silently rendering the empty state forever.
    return {
      score: data.mutationScore,
      total: data.totalMutants,
      killed: data.killedMutants,
      survived: data.survivedMutants,
      noCoverage: data.noCoverageMutants,
      timeout: data.timeoutMutants,
      ignored: data.ignoredMutants,
      tool: data.tool,
      language: data.language,
    };
  } catch (error) {
    console.error('Failed to fetch mutation summary:', error);
    return null;
  }
}

export async function fetchMutationReport(
  projectKey: string,
  branch: string = 'main',
): Promise<any> {
  try {
    const response = await axios.get(`${API_BASE}/chillcode_mutation/download`, {
      params: { projectKey, branch },
    });
    return response.data;
  } catch (error) {
    console.error('Failed to fetch mutation report:', error);
    return null;
  }
}
