import React, { useState } from 'react';
import Icon from './Icon';
import { formatCount, plural, statusLabel, statusOf, type Mutant } from '../mutants';

interface MutantListProps {
  mutants: Mutant[];
}

const PAGE_SIZE = 100;

// What the report says beyond the mutation itself: why it ended as it did
// (a killing test, a runner error), what covers it, and whether it is static.
function noteOf(mutant: Mutant): string {
  const coveredBy = mutant.coveredBy ?? [];
  let covered = '';

  if (coveredBy.length === 1) {
    covered = `Covered by ${coveredBy[0]}`;
  } else if (coveredBy.length > 1) {
    covered = `Covered by ${plural(coveredBy.length, 'test')}`;
  }

  return [mutant.statusReason?.split('\n')[0].trim(), covered, mutant.static ? 'Static' : '']
    .filter(Boolean)
    .join(' · ');
}

const MutantList: React.FC<MutantListProps> = ({ mutants }) => {
  const [shown, setShown] = useState(PAGE_SIZE);

  if (mutants.length === 0) {
    return (
      <div className="cq-state">
        <p>No mutants to display</p>
      </div>
    );
  }

  return (
    <>
      <div className="cq-mutant cq-mutant-head" aria-hidden="true">
        <span>Mutator</span>
        <span className="cq-mutant-replacement">Replaced with</span>
        <span>Line</span>
      </div>
      <ul className="cq-list cq-scroll">
        {mutants.slice(0, shown).map((mutant) => {
          const note = noteOf(mutant);

          return (
            <li key={mutant.id} className="cq-mutant" title={`Mutant ${mutant.id}`}>
              <span className="cq-mutant-name">
                <Icon
                  name={statusOf(mutant.status)}
                  status={statusOf(mutant.status)}
                  title={statusLabel(mutant.status)}
                />
                <span>{mutant.mutatorName}</span>
              </span>
              <span className="cq-mutant-replacement">
                {mutant.replacement && (
                  <code className="cq-code" title={mutant.replacement}>
                    {mutant.replacement}
                  </code>
                )}
              </span>
              <span className="cq-mutant-line">
                {mutant.location
                  ? `L${mutant.location.start.line}:${mutant.location.start.column}`
                  : '–'}
              </span>
              {note && (
                <span className="cq-mutant-note" title={note}>
                  {note}
                </span>
              )}
            </li>
          );
        })}
        {shown < mutants.length && (
          <li className="cq-more">
            {formatCount(shown)} of {formatCount(mutants.length)} shown
            <button type="button" className="cq-button" onClick={() => setShown(shown + PAGE_SIZE)}>
              Show more
            </button>
          </li>
        )}
      </ul>
    </>
  );
};

export default MutantList;
