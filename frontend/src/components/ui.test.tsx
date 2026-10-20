import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import Gauge from './Gauge';
import MetricCard from './MetricCard';
import MutantTable from './MutantTable';

const mutant = {
  id: 'mutant-1',
  mutatorName: 'ConditionalBoundaryMutator',
  replacement: 'value >= 0',
  status: 'KILLED',
  statusReason: 'Killed by a test',
  location: { start: { line: 12, column: 4 }, end: { line: 12, column: 9 } },
  coveredBy: ['ExampleTest.shouldCheckValue'],
  static: true,
};

describe('Gauge', () => {
  it('clamps its progress and displays the supplied value', () => {
    const { container } = render(<Gauge value={120} size={100} strokeWidth={10} />);
    const circles = container.querySelectorAll('circle');

    expect(screen.getByText('120%')).toBeDefined();
    expect(circles[1].getAttribute('stroke-dashoffset')).toBe('0');
    expect(circles[1].getAttribute('stroke')).toBe('var(--cq-green)');
  });

  it('uses amber at the middle rating and can hide the value', () => {
    const { container } = render(
      <Gauge value={60} colors={['red', 'amber', 'green']} showValue={false} />,
    );

    expect(screen.queryByText('60%')).toBeNull();
    expect(container.querySelectorAll('circle')[1].getAttribute('stroke')).toBe('amber');
  });

  it('uses the lowest color below the middle rating', () => {
    const { container } = render(<Gauge value={25} colors={['red', 'amber', 'green']} />);

    expect(container.querySelectorAll('circle')[1].getAttribute('stroke')).toBe('red');
  });
});

describe('MetricCard', () => {
  it('renders a rated metric and its trend', () => {
    render(<MetricCard title="Reliability" value={92} rating="A" trend="up" />);

    expect(screen.getByText('Reliability')).toBeDefined();
    expect(screen.getByText('92')).toBeDefined();
    expect(screen.getByText('A')).toBeDefined();
    expect(screen.getByText('▲ up')).toBeDefined();
  });

  it('allows a custom accent and renders the other trend variants', () => {
    const { container, rerender } = render(
      <MetricCard title="Coverage" value="—" color="purple" trend="down" />,
    );

    expect(container.querySelector('.cq-metric-card')?.getAttribute('style')).toContain('purple');
    expect(screen.getByText('▼ down')).toBeDefined();

    rerender(<MetricCard title="Coverage" value={0} trend="stable" />);
    expect(screen.getByText('● stable')).toBeDefined();
  });
});

describe('MutantTable', () => {
  it('renders an empty state when there are no mutants', () => {
    render(<MutantTable mutants={[]} />);

    expect(screen.getByText('No mutants to display')).toBeDefined();
  });

  it('shows known mutant status, location, tests, and static status', () => {
    render(<MutantTable mutants={[mutant]} />);

    expect(screen.getByText('mutant-1')).toBeDefined();
    expect(screen.getByText('Killed')).toBeDefined();
    expect(screen.getByText('L12:C4 – L12:C9')).toBeDefined();
    expect(screen.getByText('ExampleTest.shouldCheckValue')).toBeDefined();
    expect(screen.getByText('Yes')).toBeDefined();
  });

  it('falls back for unknown status and missing optional values', () => {
    render(
      <MutantTable
        mutants={[
          {
            ...mutant,
            id: 'unknown-mutant',
            status: 'CUSTOM_STATUS',
            location: undefined,
            coveredBy: [],
            static: false,
          },
        ]}
      />,
    );

    expect(screen.getByText('CUSTOM_STATUS')).toBeDefined();
    expect(screen.getByText('N/A')).toBeDefined();
    expect(screen.getByText('—')).toBeDefined();
    expect(screen.getByText('No')).toBeDefined();
  });
});
