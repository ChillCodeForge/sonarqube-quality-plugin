import React, { useState, useEffect } from 'react';
import HistoryChart from './components/HistoryChart';
import {
  fetchQualityMetrics,
  fetchQualityHistory,
  type QualityHistoryPoint,
  type QualityMeasure,
} from './api';
import { toChartPoints } from './history';
import { formatCount, formatPercent, ratingOf, type Rating } from './mutants';

interface QualityDashboardProps {
  componentKey: string;
}

type TileKind = 'rating' | 'percent' | 'score';

interface TileContent {
  text: string;
  rating: Rating | null;
  note: string | null;
}

const TILES: readonly { readonly key: string; readonly label: string; readonly kind: TileKind }[] =
  [
    { key: 'reliability_rating', label: 'Reliability', kind: 'rating' },
    { key: 'security_rating', label: 'Security', kind: 'rating' },
    { key: 'sqale_rating', label: 'Maintainability', kind: 'rating' },
    { key: 'coverage', label: 'Coverage', kind: 'percent' },
    { key: 'duplicated_lines_density', label: 'Duplications', kind: 'percent' },
    { key: 'mutation_score', label: 'Mutation score', kind: 'score' },
  ];

const SEVERITIES: readonly {
  readonly id: string;
  readonly label: string;
  readonly key: string;
}[] = [
  { id: 'blocker', label: 'Blocker', key: 'blocker_violations' },
  { id: 'critical', label: 'Critical', key: 'critical_violations' },
  { id: 'major', label: 'Major', key: 'major_violations' },
  { id: 'minor', label: 'Minor', key: 'minor_violations' },
  { id: 'info', label: 'Info', key: 'info_violations' },
];

// SonarQube reports a rating as 1 to 5, best first.
const RATINGS: readonly Rating[] = ['A', 'B', 'C', 'D', 'E'];

// A measure SonarQube did not return shows as a dash: a made-up "A" or "0%"
// would read as a real result.
function tileContent(kind: TileKind, value: number | undefined): TileContent {
  if (value === undefined) {
    return { text: '–', rating: null, note: null };
  }

  if (kind === 'rating') {
    const rating = RATINGS[Math.round(value) - 1] ?? null;

    return { text: rating ?? '–', rating, note: null };
  }

  if (kind === 'score') {
    const rating = ratingOf(value);

    return { text: formatPercent(value), rating, note: `Rating ${rating}` };
  }

  return { text: formatPercent(value), rating: null, note: null };
}

export const QualityDashboard: React.FC<QualityDashboardProps> = ({ componentKey }) => {
  const [metrics, setMetrics] = useState<QualityMeasure[]>([]);
  const [history, setHistory] = useState<QualityHistoryPoint[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, [componentKey]);

  const loadData = async () => {
    try {
      const [metricsData, historyData] = await Promise.all([
        fetchQualityMetrics(componentKey),
        fetchQualityHistory(componentKey),
      ]);

      setMetrics(metricsData);
      setHistory(historyData);
    } catch (error) {
      console.error('Failed to load quality data:', error);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="cq-root">
        <div className="cq-card cq-pad" aria-busy="true">
          <span className="cq-sr-only">Loading quality dashboard…</span>
          <span className="cq-skeleton" style={{ width: '30%', height: 28, marginTop: 0 }} />
          <span className="cq-skeleton" />
          <span className="cq-skeleton" style={{ width: '82%' }} />
          <span className="cq-skeleton" style={{ width: '64%' }} />
        </div>
      </div>
    );
  }

  const valueOf = (key: string) => metrics.find((metric) => metric.key === key)?.value;
  const issues = SEVERITIES.map((severity) => ({ ...severity, count: valueOf(severity.key) ?? 0 }));
  const mostIssues = Math.max(1, ...issues.map((severity) => severity.count));
  const analyses = toChartPoints(history);
  const lastAnalysis = analyses.length > 0 ? analyses[analyses.length - 1] : null;

  return (
    <div className="cq-root">
      <header className="cq-head">
        <div>
          <h1 className="cq-h1">Quality Dashboard</h1>
          <p className="cq-meta">
            {lastAnalysis
              ? `Last analysis ${new Date(lastAnalysis.time).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })}`
              : 'Project health at a glance'}
          </p>
        </div>
      </header>

      <section className="cq-tiles" aria-label="Measures">
        {TILES.map((tile) => {
          const content = tileContent(tile.kind, valueOf(tile.key));

          return (
            <div key={tile.key} className="cq-tile" data-rating={content.rating ?? undefined}>
              <span className="cq-tile-label">{tile.label}</span>
              <span className="cq-tile-value">{content.text}</span>
              {content.note && <span className="cq-tile-note">{content.note}</span>}
            </div>
          );
        })}
      </section>

      <div className="cq-cols">
        <section className="cq-card">
          <div className="cq-cardhead">
            <div>
              <h2 className="cq-h2">Coverage and mutation score</h2>
              <span className="cq-help">
                {`${formatCount(history.length)} ${history.length === 1 ? 'analysis' : 'analyses'} in the last 30 days`}
              </span>
            </div>
          </div>
          <HistoryChart history={history} />
        </section>

        <section className="cq-card">
          <div className="cq-cardhead">
            <h2 className="cq-h2">
              Open issues by severity
              <span className="cq-count">
                {formatCount(issues.reduce((sum, severity) => sum + severity.count, 0))}
              </span>
            </h2>
          </div>
          {issues.map((severity) => (
            <div key={severity.id} className="cq-severity" data-severity={severity.id}>
              <i className="cq-severity-dot" />
              <span>{severity.label}</span>
              <span className="cq-severity-track">
                <i style={{ width: `${(severity.count / mostIssues) * 100}%` }} />
              </span>
              <span
                className={severity.count > 0 ? 'cq-severity-count' : 'cq-severity-count cq-zero'}
              >
                {formatCount(severity.count)}
              </span>
            </div>
          ))}
        </section>
      </div>
    </div>
  );
};

export default QualityDashboard;
