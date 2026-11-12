import React, { useEffect, useMemo, useState } from 'react';
import FileList from './components/FileList';
import Icon from './components/Icon';
import MutantBar from './components/MutantBar';
import MutantList from './components/MutantList';
import RatingBadge from './components/RatingBadge';
import { fetchMutationReport, fetchMutationSummary } from './api';
import {
  STATUS_LABELS,
  STATUS_ORDER,
  countsFromTotals,
  filterFiles,
  formatCount,
  formatPercent,
  plural,
  ratingOf,
  reportDate,
  sortFiles,
  statusOf,
  toFileView,
  type FileSort,
  type FileView,
  type MutationFile,
  type StatusKey,
} from './mutants';

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

interface MutationTestingProps {
  projectKey: string;
  branch?: string;
}

type Tab = StatusKey | 'all';

// A file opens on what still needs a test; killed mutants come last.
const TAB_ORDER: readonly StatusKey[] = [
  'survived',
  'noCoverage',
  'timeout',
  'unknown',
  'ignored',
  'killed',
];

const OPEN_STATUSES: readonly StatusKey[] = ['survived', 'noCoverage', 'timeout', 'unknown'];

function tabsOf(file: FileView): Tab[] {
  return [...TAB_ORDER.filter((status) => file.counts[status] > 0), 'all'];
}

function defaultTab(file: FileView): Tab {
  return OPEN_STATUSES.find((status) => file.counts[status] > 0) ?? 'all';
}

const MutationTesting: React.FC<MutationTestingProps> = ({ projectKey, branch = 'main' }) => {
  const [summary, setSummary] = useState<MutationSummary | null>(null);
  const [files, setFiles] = useState<MutationFile[]>([]);
  const [reportedOn, setReportedOn] = useState<Date | null>(null);
  const [phase, setPhase] = useState<'loading' | 'ready' | 'failed'>('loading');
  const [selectedPath, setSelectedPath] = useState<string | null>(null);
  const [filter, setFilter] = useState('');
  const [sort, setSort] = useState<FileSort>('survivors');
  const [tab, setTab] = useState<Tab | null>(null);

  useEffect(() => {
    loadData();
  }, [projectKey, branch]);

  const loadData = async () => {
    setPhase('loading');

    try {
      const [summaryData, reportData] = await Promise.all([
        fetchMutationSummary(projectKey, branch),
        fetchMutationReport(projectKey, branch),
      ]);

      setSummary(summaryData);
      setFiles(Array.isArray(reportData?.files) ? reportData.files : []);
      setReportedOn(reportDate(reportData?.timestamp));
      setPhase('ready');
    } catch (error) {
      console.error('Failed to load mutation data:', error);
      setPhase('failed');
    }
  };

  const views = useMemo(() => files.map(toFileView), [files]);
  const visible = useMemo(() => sortFiles(filterFiles(views, filter), sort), [views, filter, sort]);

  if (phase === 'loading') {
    return (
      <div className="cq-root">
        <div className="cq-card cq-pad" aria-busy="true">
          <span className="cq-sr-only">Loading mutation testing results…</span>
          <span className="cq-skeleton" style={{ width: '30%', height: 28, marginTop: 0 }} />
          <span className="cq-skeleton" />
          <span className="cq-skeleton" style={{ width: '82%' }} />
          <span className="cq-skeleton" style={{ width: '64%' }} />
        </div>
      </div>
    );
  }

  if (phase === 'failed') {
    return (
      <div className="cq-root">
        <div className="cq-card">
          <div className="cq-state cq-state-failed" role="alert">
            <Icon name="alert" />
            <h1 className="cq-h2">The mutation report did not load</h1>
            <p>SonarQube did not answer the request for {branch}.</p>
            <button type="button" className="cq-button" onClick={loadData}>
              <Icon name="retry" />
              Try again
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (summary?.score == null) {
    return (
      <div className="cq-root">
        <div className="cq-card">
          <div className="cq-state">
            <Icon name="file" />
            <h1 className="cq-h2">No mutation report for {branch}</h1>
            <p>
              Upload one from CI with{' '}
              <code className="cq-code">scripts/upload-mutation-report.sh</code>
            </p>
          </div>
        </div>
      </div>
    );
  }

  const counts = countsFromTotals(summary);
  const selected = visible.find((file) => file.path === selectedPath) ?? visible[0] ?? null;
  const tabs = selected ? tabsOf(selected) : [];
  const activeTab = selected && tab && tabs.includes(tab) ? tab : selected && defaultTab(selected);
  const shownMutants =
    selected?.mutants.filter(
      (mutant) => activeTab === 'all' || statusOf(mutant.status) === activeTab,
    ) ?? [];

  return (
    <div className="cq-root">
      <header className="cq-head">
        <div>
          <h1 className="cq-h1">Mutation Testing</h1>
          <p className="cq-meta">
            <span className="cq-tool">{summary.tool}</span>
            {` · ${plural(summary.total, 'mutant')} in ${plural(views.length, 'file')}`}
            {reportedOn &&
              ` · report of ${reportedOn.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })}`}
          </p>
        </div>
        <div className="cq-score">
          <div>
            <div className="cq-label">Mutation score</div>
            <div className="cq-big">{formatPercent(summary.score)}</div>
          </div>
          <RatingBadge rating={ratingOf(summary.score)} large />
        </div>
      </header>

      <section className="cq-card cq-pad" aria-label="Mutants by status">
        <MutantBar counts={counts} size="large" />
        <dl className="cq-legend">
          {STATUS_ORDER.filter((status) => counts[status] > 0).map((status) => (
            <div key={status}>
              <Icon name={status} status={status} />
              <dt>{STATUS_LABELS[status]}</dt>
              <dd>{formatCount(counts[status])}</dd>
            </div>
          ))}
        </dl>
      </section>

      {views.length === 0 ? (
        <section className="cq-card">
          <div className="cq-state">
            <Icon name="file" />
            <h2 className="cq-h2">This report lists no files</h2>
          </div>
        </section>
      ) : (
        <section className="cq-card cq-split">
          <FileList
            files={visible}
            selectedPath={selected?.path ?? null}
            filter={filter}
            sort={sort}
            onSelect={(path) => {
              setSelectedPath(path);
              setTab(null);
            }}
            onFilter={setFilter}
            onSort={setSort}
          />
          {selected && (
            <section aria-label={`Mutants in ${selected.path}`}>
              <div className="cq-detailhead">
                <h2 className="cq-path" title={selected.path}>
                  <span className="cq-path-dir">{selected.directory}</span>
                  <span className="cq-path-name">{selected.name}</span>
                </h2>
                <b>{selected.score === null ? 'N/A' : formatPercent(selected.score)}</b>
                {selected.rating && <RatingBadge rating={selected.rating} />}
              </div>
              <div className="cq-detailbar">
                <MutantBar counts={selected.counts} />
              </div>
              <div className="cq-tabs" role="tablist" aria-label="Mutants shown">
                {tabs.map((option) => (
                  <button
                    key={option}
                    type="button"
                    role="tab"
                    className="cq-tab"
                    aria-selected={option === activeTab}
                    onClick={() => setTab(option)}
                  >
                    {option !== 'all' && <Icon name={option} status={option} />}
                    {option === 'all' ? 'All' : STATUS_LABELS[option]}
                    <span className="cq-count">
                      {formatCount(option === 'all' ? selected.total : selected.counts[option])}
                    </span>
                  </button>
                ))}
              </div>
              <MutantList key={`${selected.path}:${activeTab}`} mutants={shownMutants} />
            </section>
          )}
        </section>
      )}
    </div>
  );
};

export default MutationTesting;
