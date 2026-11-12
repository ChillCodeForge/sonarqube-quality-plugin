import React from 'react';
import { STATUS_LABELS, STATUS_ORDER, formatCount, type StatusCounts } from '../mutants';

interface MutantBarProps {
  counts: StatusCounts;
  size?: 'small' | 'medium' | 'large';
}

const MutantBar: React.FC<MutantBarProps> = ({ counts, size = 'medium' }) => {
  const shown = STATUS_ORDER.filter((status) => counts[status] > 0);
  const label = shown
    .map((status) => `${formatCount(counts[status])} ${STATUS_LABELS[status].toLowerCase()}`)
    .join(', ');

  return (
    <div className={`cq-bar cq-bar-${size}`} role="img" aria-label={label || 'No mutants'}>
      {shown.map((status) => (
        <i key={status} data-status={status} style={{ flex: `${counts[status]} 1 0` }} />
      ))}
    </div>
  );
};

export default MutantBar;
