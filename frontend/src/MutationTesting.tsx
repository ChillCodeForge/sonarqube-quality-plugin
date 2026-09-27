import React, { useState, useEffect } from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
} from 'recharts';
import Gauge from './components/Gauge';
import MetricCard from './components/MetricCard';
import MutantTable from './components/MutantTable';
import { fetchMutationReport, fetchMutationSummary } from './api';

interface MutationSummary {
  score: number;
  total: number;
  killed: number;
  survived: number;
  noCoverage: number;
  timeout: number;
  ignored: number;
  tool: string;
  language: string;
}

interface MutationFile {
  path: string;
  language: string;
  mutants: any[];
  metrics: any;
}

interface MutationTestingProps {
  projectKey: string;
  branch?: string;
}

const COLORS = {
  killed: 'var(--cq-green)',
  survived: 'var(--cq-red)',
  noCoverage: 'var(--cq-amber)',
  timeout: 'var(--cq-violet)',
  ignored: 'var(--cq-blue)',
};

const MutationTesting: React.FC<MutationTestingProps> = ({ projectKey, branch = 'main' }) => {
  const [summary, setSummary] = useState<MutationSummary | null>(null);
  const [files, setFiles] = useState<MutationFile[]>([]);
  const [selectedFile, setSelectedFile] = useState<MutationFile | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, [projectKey, branch]);

  const loadData = async () => {
    try {
      const [summaryData, reportData] = await Promise.all([
        fetchMutationSummary(projectKey, branch),
        fetchMutationReport(projectKey, branch),
      ]);
      setSummary(summaryData);
      if (reportData?.files) {
        setFiles(reportData.files);
      }
    } catch (error) {
      console.error('Failed to load mutation data:', error);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="cq-root">
        <div className="cq-loading">
          <span className="cq-spinner" />
          Loading mutation testing results…
        </div>
      </div>
    );
  }

  if (!summary || summary.score === undefined || summary.score === null) {
    return (
      <div className="cq-root">
        <div className="cq-empty">No mutation testing data available for this project</div>
      </div>
    );
  }

  return (
    <div className="cq-root">
      <header className="cq-header">
        <div className="cq-title-block">
          <div className="cq-icon-badge">🧬</div>
          <div>
            <h1>Mutation Testing</h1>
            <p className="cq-subtitle">
              {summary.tool} · {summary.language} · {summary.total} mutants
            </p>
          </div>
        </div>
        <span className="cq-pill">{getRating(summary.score)} rating</span>
      </header>

      <section className="cq-summary-grid">
        <div
          className="cq-metric-card cq-gauge-card"
          style={{ '--cq-accent': ratingColor(getRating(summary.score)) } as React.CSSProperties}
        >
          <Gauge
            value={summary.score}
            size={84}
            strokeWidth={9}
            colors={[COLORS.survived, COLORS.noCoverage, COLORS.killed]}
          />
          <div>
            <div className="cq-metric-title">Mutation Score</div>
            <div className="cq-metric-value">
              {summary.score.toFixed(1)}%
              <span
                className="cq-metric-rating"
                style={{ backgroundColor: ratingColor(getRating(summary.score)) }}
              >
                {getRating(summary.score)}
              </span>
            </div>
          </div>
        </div>
        <MetricCard title="Total Mutants" value={summary.total} />
        <MetricCard title="Killed" value={summary.killed} color={COLORS.killed} />
        <MetricCard title="Survived" value={summary.survived} color={COLORS.survived} />
        <MetricCard title="No Coverage" value={summary.noCoverage} color={COLORS.noCoverage} />
        <MetricCard title="Timeout" value={summary.timeout} color={COLORS.timeout} />
        <MetricCard title="Ignored" value={summary.ignored} color={COLORS.ignored} />
      </section>

      <section className="cq-charts-grid">
        <div className="cq-chart-card">
          <h2>Mutant Status Distribution</h2>
          <ResponsiveContainer width="100%" height={280}>
            <PieChart>
              <Pie
                data={[
                  { name: 'Killed', value: summary.killed },
                  { name: 'Survived', value: summary.survived },
                  { name: 'No Coverage', value: summary.noCoverage },
                  { name: 'Timeout', value: summary.timeout },
                  { name: 'Ignored', value: summary.ignored },
                ]}
                cx="50%"
                cy="50%"
                innerRadius={62}
                outerRadius={98}
                paddingAngle={2}
                cornerRadius={6}
                dataKey="value"
                nameKey="name"
                label={({ name, percent }) => `${name}: ${(percent * 100).toFixed(1)}%`}
                style={{ fill: 'var(--cq-text)' }}
              >
                {[
                  COLORS.killed,
                  COLORS.survived,
                  COLORS.noCoverage,
                  COLORS.timeout,
                  COLORS.ignored,
                ].map((color, i) => (
                  <Cell key={`cell-${i}`} fill={color} stroke="var(--cq-surface)" strokeWidth={2} />
                ))}
              </Pie>
              <Tooltip
                contentStyle={{
                  borderRadius: 10,
                  border: '1px solid var(--cq-border)',
                  background: 'var(--cq-surface)',
                  color: 'var(--cq-text)',
                }}
              />
            </PieChart>
          </ResponsiveContainer>
        </div>

        <div className="cq-chart-card">
          <h2>Mutation Score</h2>
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={[{ label: 'Current', score: summary.score }]}>
              <XAxis
                dataKey="label"
                tick={{ fill: 'var(--cq-text-muted)', fontSize: 12 }}
                axisLine={{ stroke: 'var(--cq-border)' }}
              />
              <YAxis
                domain={[0, 100]}
                tick={{ fill: 'var(--cq-text-muted)', fontSize: 12 }}
                axisLine={{ stroke: 'var(--cq-border)' }}
              />
              <CartesianGrid strokeDasharray="3 3" stroke="var(--cq-border)" />
              <Tooltip
                contentStyle={{
                  borderRadius: 10,
                  border: '1px solid var(--cq-border)',
                  background: 'var(--cq-surface)',
                  color: 'var(--cq-text)',
                }}
              />
              <Bar
                dataKey="score"
                fill={COLORS.killed}
                name="Score %"
                radius={[8, 8, 0, 0]}
                maxBarSize={72}
              />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </section>

      <section className="cq-files-section">
        <h2>Files ({files.length})</h2>
        <div className="cq-files-table-container">
          <table>
            <thead>
              <tr>
                <th>File</th>
                <th>Language</th>
                <th>Mutants</th>
                <th>Score</th>
                <th>Killed</th>
                <th>Survived</th>
                <th>No Coverage</th>
              </tr>
            </thead>
            <tbody>
              {files.map((file, index) => (
                <tr
                  key={index}
                  onClick={() => setSelectedFile(file)}
                  className={selectedFile === file ? 'cq-selected' : ''}
                >
                  <td>{file.path}</td>
                  <td>{file.language}</td>
                  <td>{file.mutants?.length || 0}</td>
                  <td>{file.metrics?.score?.toFixed(1) || 'N/A'}%</td>
                  <td style={{ color: COLORS.killed, fontWeight: 600 }}>
                    {file.metrics?.killed || 0}
                  </td>
                  <td style={{ color: COLORS.survived, fontWeight: 600 }}>
                    {file.metrics?.survived || 0}
                  </td>
                  <td style={{ color: COLORS.noCoverage, fontWeight: 600 }}>
                    {file.metrics?.noCoverage || 0}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {selectedFile && (
        <section className="cq-mutants-detail">
          <div className="cq-detail-header">
            <h3>Mutants in {selectedFile.path}</h3>
            <button className="cq-close-btn" onClick={() => setSelectedFile(null)}>
              Close
            </button>
          </div>
          <MutantTable mutants={selectedFile.mutants || []} />
        </section>
      )}
    </div>
  );
};

function getRating(score: number): 'A' | 'B' | 'C' | 'D' | 'E' {
  if (score >= 90) return 'A';
  if (score >= 75) return 'B';
  if (score >= 60) return 'C';
  if (score >= 40) return 'D';
  return 'E';
}

function ratingColor(rating: 'A' | 'B' | 'C' | 'D' | 'E'): string {
  const colors: Record<string, string> = {
    A: 'var(--cq-green)',
    B: 'var(--cq-blue)',
    C: 'var(--cq-amber)',
    D: 'var(--cq-red)',
    E: 'var(--cq-red-deep)',
  };
  return colors[rating];
}

export default MutationTesting;
