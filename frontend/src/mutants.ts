export type Rating = 'A' | 'B' | 'C' | 'D' | 'E';

export type StatusKey = 'killed' | 'timeout' | 'survived' | 'noCoverage' | 'unknown' | 'ignored';

export type StatusCounts = Record<StatusKey, number>;

export interface MutantLocation {
  start: { line: number; column: number };
  end?: { line: number; column: number };
}

export interface Mutant {
  id: string;
  mutatorName: string;
  replacement?: string;
  status: string;
  statusReason?: string;
  location?: MutantLocation;
  coveredBy?: string[];
  static?: boolean;
}

export interface MutationFile {
  path: string;
  language?: string;
  mutants?: Mutant[];
  metrics?: Partial<Record<'score' | 'total' | Exclude<StatusKey, 'unknown'>, number>>;
}

export interface FileView {
  path: string;
  directory: string;
  name: string;
  total: number;
  counts: StatusCounts;
  score: number | null;
  rating: Rating | null;
  mutants: Mutant[];
}

// Detected mutants come first, so a bar drawn in this order fills from the
// left by exactly the mutation score.
export const STATUS_ORDER: readonly StatusKey[] = [
  'killed',
  'timeout',
  'survived',
  'noCoverage',
  'unknown',
  'ignored',
];

export const STATUS_LABELS: Record<StatusKey, string> = {
  killed: 'Killed',
  timeout: 'Timeout',
  survived: 'Survived',
  noCoverage: 'No coverage',
  unknown: 'Unknown',
  ignored: 'Ignored',
};

const REPORT_STATUSES = new Map<string, StatusKey>([
  ['KILLED', 'killed'],
  ['SURVIVED', 'survived'],
  ['NO_COVERAGE', 'noCoverage'],
  ['TIMEOUT', 'timeout'],
  ['IGNORED', 'ignored'],
]);

const REPORT_STATUS_LABELS = new Map<string, string>([
  ['ERROR', 'Error'],
  ['COMPILE_ERROR', 'Compile error'],
  ['RUNTIME_ERROR', 'Runtime error'],
  ['UNKNOWN', 'Unknown'],
]);

export function statusOf(status: string): StatusKey {
  return REPORT_STATUSES.get(status) ?? 'unknown';
}

export function statusLabel(status: string): string {
  const key = statusOf(status);

  return key === 'unknown' ? (REPORT_STATUS_LABELS.get(status) ?? status) : STATUS_LABELS[key];
}

export function emptyCounts(): StatusCounts {
  return { killed: 0, timeout: 0, survived: 0, noCoverage: 0, unknown: 0, ignored: 0 };
}

// A summary or a file's metrics name five statuses and a total; whatever the
// total holds beyond them (errors, unviable mutants) is shown as unknown.
export function countsFromTotals(
  totals: Partial<Record<'total' | Exclude<StatusKey, 'unknown'>, number>>,
): StatusCounts {
  const counts = emptyCounts();
  let named = 0;

  for (const key of STATUS_ORDER) {
    if (key !== 'unknown') {
      counts[key] = totals[key] ?? 0;
      named += counts[key];
    }
  }

  counts.unknown = Math.max(0, (totals.total ?? 0) - named);

  return counts;
}

export function sumCounts(counts: StatusCounts): number {
  return STATUS_ORDER.reduce((sum, key) => sum + counts[key], 0);
}

export function ratingOf(score: number): Rating {
  if (score >= 90) return 'A';

  if (score >= 75) return 'B';

  if (score >= 60) return 'C';

  if (score >= 40) return 'D';

  return 'E';
}

// Mutants read top to bottom like the file they change; one without a
// location goes last.
function byLocation(a: Mutant, b: Mutant): number {
  const [first, second] = [a.location?.start, b.location?.start];

  return (
    (first?.line ?? Infinity) - (second?.line ?? Infinity) ||
    (first?.column ?? 0) - (second?.column ?? 0)
  );
}

export function toFileView(file: MutationFile): FileView {
  const mutants = [...(file.mutants ?? [])].sort(byLocation);
  let counts = countsFromTotals(file.metrics ?? {});

  if (mutants.length > 0) {
    counts = emptyCounts();

    for (const mutant of mutants) {
      counts[statusOf(mutant.status)] += 1;
    }
  }

  const slash = file.path.lastIndexOf('/');
  const score = file.metrics?.score ?? null;

  return {
    path: file.path,
    directory: file.path.slice(0, slash + 1),
    name: file.path.slice(slash + 1),
    total: sumCounts(counts),
    counts,
    score,
    rating: score === null ? null : ratingOf(score),
    mutants,
  };
}

export type FileSort = 'survivors' | 'score' | 'mutants' | 'path';

export const FILE_SORTS: readonly { readonly value: FileSort; readonly label: string }[] = [
  { value: 'survivors', label: 'Most survivors' },
  { value: 'score', label: 'Lowest score' },
  { value: 'mutants', label: 'Most mutants' },
  { value: 'path', label: 'Path' },
];

const FILE_ORDERS: Record<FileSort, (a: FileView, b: FileView) => number> = {
  survivors: (a, b) => b.counts.survived - a.counts.survived || b.total - a.total,
  score: (a, b) => (a.score ?? 101) - (b.score ?? 101),
  mutants: (a, b) => b.total - a.total,
  path: (a, b) => a.path.localeCompare(b.path),
};

export function sortFiles(files: FileView[], sort: FileSort): FileView[] {
  return [...files].sort(FILE_ORDERS[sort]);
}

export function filterFiles(files: FileView[], filter: string): FileView[] {
  const needle = filter.trim().toLowerCase();

  return needle ? files.filter((file) => file.path.toLowerCase().includes(needle)) : files;
}

// The stored report carries its timestamp as [year, month, day, ...] (how the
// backend serializes it) or, from other producers, as an ISO string.
export function reportDate(timestamp: unknown): Date | null {
  if (Array.isArray(timestamp) && timestamp.length >= 3) {
    return new Date(Number(timestamp[0]), Number(timestamp[1]) - 1, Number(timestamp[2]));
  }

  if (typeof timestamp === 'string') {
    const date = new Date(timestamp);

    return Number.isNaN(date.getTime()) ? null : date;
  }

  return null;
}

export function plural(count: number, noun: string): string {
  return `${formatCount(count)} ${noun}${count === 1 ? '' : 's'}`;
}

export function formatCount(value: number): string {
  return value.toLocaleString('en-US');
}

export function formatPercent(value: number): string {
  return value === 100 ? '100%' : `${value.toFixed(1)}%`;
}
