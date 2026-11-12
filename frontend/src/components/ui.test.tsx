import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import FileList from './FileList';
import Icon from './Icon';
import MutantBar from './MutantBar';
import MutantList from './MutantList';
import RatingBadge from './RatingBadge';
import { emptyCounts, toFileView, type Mutant } from '../mutants';

const mutant: Mutant = {
  id: 'mutant-1',
  mutatorName: 'ConditionalBoundaryMutator',
  replacement: 'value >= 0',
  status: 'KILLED',
  statusReason: 'ExampleTest > rejects a negative value\nstack trace',
  location: { start: { line: 12, column: 4 }, end: { line: 12, column: 9 } },
  coveredBy: ['ExampleTest.shouldCheckValue'],
  static: true,
};

describe('Icon', () => {
  it('is decorative unless it is given a title', () => {
    const { container, rerender } = render(<Icon name="search" />);

    expect(container.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');

    rerender(<Icon name="survived" status="survived" title="Survived" />);
    expect(screen.getByRole('img', { name: 'Survived' }).getAttribute('data-status')).toBe(
      'survived',
    );
  });
});

describe('MutantBar', () => {
  it('draws one segment per status that has mutants, sized by its count', () => {
    render(<MutantBar counts={{ ...emptyCounts(), killed: 1408, survived: 71 }} size="large" />);
    const bar = screen.getByRole('img', { name: '1,408 killed, 71 survived' });

    expect(bar.className).toBe('cq-bar cq-bar-large');
    expect([...bar.children].map((segment) => segment.getAttribute('data-status'))).toEqual([
      'killed',
      'survived',
    ]);
    expect((bar.children[1] as HTMLElement).style.flexGrow).toBe('71');
  });

  it('says so when there is nothing to draw', () => {
    render(<MutantBar counts={emptyCounts()} />);

    expect(screen.getByRole('img', { name: 'No mutants' }).className).toBe('cq-bar cq-bar-medium');
  });
});

describe('RatingBadge', () => {
  it('carries its rating for the theme and can be drawn large', () => {
    const { rerender } = render(<RatingBadge rating="B" />);

    expect(screen.getByText('B').getAttribute('data-rating')).toBe('B');
    expect(screen.getByText('B').className).toBe('cq-rating');

    rerender(<RatingBadge rating="A" large />);
    expect(screen.getByTitle('Rating A').className).toBe('cq-rating cq-rating-large');
  });
});

describe('MutantList', () => {
  it('renders an empty state when there are no mutants', () => {
    render(<MutantList mutants={[]} />);

    expect(screen.getByText('No mutants to display')).toBeDefined();
  });

  it('shows the status, replacement, line and what the report says about the mutant', () => {
    render(<MutantList mutants={[mutant]} />);

    expect(screen.getByTitle('Mutant mutant-1')).toBeDefined();
    expect(screen.getByRole('img', { name: 'Killed' })).toBeDefined();
    expect(screen.getByText('value >= 0')).toBeDefined();
    expect(screen.getByText('L12:4')).toBeDefined();
    expect(
      screen.getByText(
        'ExampleTest > rejects a negative value · Covered by ExampleTest.shouldCheckValue · Static',
      ),
    ).toBeDefined();
  });

  it('leaves out what a mutant does not carry', () => {
    const { container } = render(
      <MutantList
        mutants={[
          {
            id: 'bare',
            mutatorName: 'BlockStatement',
            status: 'CUSTOM_STATUS',
            coveredBy: ['a', 'b'],
          },
          { id: 'barest', mutatorName: 'Regex', status: 'SURVIVED' },
        ]}
      />,
    );

    expect(screen.getByRole('img', { name: 'CUSTOM_STATUS' })).toBeDefined();
    expect(screen.getByText('Covered by 2 tests')).toBeDefined();
    expect(screen.getAllByText('–')).toHaveLength(2);
    expect(container.querySelectorAll('code')).toHaveLength(0);
    expect(container.querySelectorAll('.cq-mutant-note')).toHaveLength(1);
  });

  it('shows a long file a hundred mutants at a time', () => {
    const many = Array.from({ length: 150 }, (_, index) => ({ ...mutant, id: `m-${index}` }));
    const { container } = render(<MutantList mutants={many} />);

    expect(container.querySelectorAll('li.cq-mutant')).toHaveLength(100);
    expect(screen.getByText(/100 of 150 shown/)).toBeDefined();

    fireEvent.click(screen.getByRole('button', { name: 'Show more' }));
    expect(container.querySelectorAll('li.cq-mutant')).toHaveLength(150);
    expect(screen.queryByRole('button', { name: 'Show more' })).toBeNull();
  });
});

describe('FileList', () => {
  const files = [
    toFileView({
      path: 'src/measure/steps.core.mjs',
      metrics: { score: 84.3, total: 484, killed: 413, survived: 71 },
    }),
    toFileView({ path: 'clean.mjs', metrics: { score: 100, total: 5, killed: 5 } }),
    toFileView({ path: 'src/legacy.ts' }),
  ];

  const renderList = (shown = files, filter = '') => {
    const handlers = { onSelect: vi.fn(), onFilter: vi.fn(), onSort: vi.fn() };

    render(
      <FileList
        files={shown}
        selectedPath="src/measure/steps.core.mjs"
        filter={filter}
        sort="survivors"
        {...handlers}
      />,
    );

    return handlers;
  };

  it('names each file with its survivors and score, and marks the selected one', () => {
    renderList();
    const selected = screen.getByRole('button', { name: /steps\.core\.mjs/ });

    expect(selected.getAttribute('aria-current')).toBe('true');
    expect(selected.textContent).toContain('71 survived');
    expect(selected.textContent).toContain('84.3%');
    expect(selected.textContent).toContain('src/measure/');
    expect(
      screen.getByRole('button', { name: /clean\.mjs/ }).getAttribute('aria-current'),
    ).toBeNull();
    expect(screen.getByRole('button', { name: /clean\.mjs/ }).textContent).toContain('100%');
    expect(screen.getByRole('button', { name: /legacy\.ts/ }).textContent).toContain('N/A');
  });

  it('reports a selection, a filter and a sort to its owner', () => {
    const handlers = renderList();

    fireEvent.click(screen.getByRole('button', { name: /clean\.mjs/ }));
    fireEvent.change(screen.getByLabelText('Filter files'), { target: { value: 'legacy' } });
    fireEvent.change(screen.getByLabelText('Sort files'), { target: { value: 'score' } });

    expect(handlers.onSelect).toHaveBeenCalledWith('clean.mjs');
    expect(handlers.onFilter).toHaveBeenCalledWith('legacy');
    expect(handlers.onSort).toHaveBeenCalledWith('score');
  });

  it('says which filter matched nothing', () => {
    renderList([], 'nowhere');

    expect(screen.getByText('No file matches “nowhere”')).toBeDefined();
  });
});
