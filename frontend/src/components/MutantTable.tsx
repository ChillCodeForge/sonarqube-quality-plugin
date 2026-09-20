import React from 'react'

interface Mutant {
  id: string
  mutatorName: string
  replacement: string
  status: string
  statusReason: string
  location?: {
    start: { line: number; column: number }
    end: { line: number; column: number }
  }
  coveredBy: string[]
  static: boolean
}

interface MutantTableProps {
  mutants: Mutant[]
}

const MutantTable: React.FC<MutantTableProps> = ({ mutants }) => {
  const statusColors: Record<string, string> = {
    KILLED: 'var(--cq-green)',
    SURVIVED: 'var(--cq-red)',
    NO_COVERAGE: 'var(--cq-amber)',
    TIMEOUT: 'var(--cq-violet)',
    IGNORED: 'var(--cq-violet)',
    ERROR: 'var(--cq-red)',
    COMPILE_ERROR: 'var(--cq-red)',
    RUNTIME_ERROR: 'var(--cq-red)',
  }

  const statusLabels: Record<string, string> = {
    KILLED: 'Killed',
    SURVIVED: 'Survived',
    NO_COVERAGE: 'No Coverage',
    TIMEOUT: 'Timeout',
    IGNORED: 'Ignored',
    ERROR: 'Error',
    COMPILE_ERROR: 'Compile Error',
    RUNTIME_ERROR: 'Runtime Error',
  }

  if (!mutants || mutants.length === 0) {
    return <div className="cq-empty">No mutants to display</div>
  }

  return (
    <div className="cq-mutant-table-container">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Mutator</th>
            <th>Replacement</th>
            <th>Status</th>
            <th>Location</th>
            <th>Covered By</th>
            <th>Static</th>
          </tr>
        </thead>
        <tbody>
          {mutants.map((mutant) => (
            <tr key={mutant.id}>
              <td className="cq-mutant-id">{mutant.id}</td>
              <td>{mutant.mutatorName}</td>
              <td className="cq-mutant-replacement">{mutant.replacement}</td>
              <td>
                <span
                  className="cq-status-badge"
                  style={{ backgroundColor: statusColors[mutant.status] || 'var(--cq-violet)' }}
                >
                  {statusLabels[mutant.status] || mutant.status}
                </span>
              </td>
              <td>
                {mutant.location ? (
                  <>
                    L{mutant.location.start.line}:C{mutant.location.start.column}
                    {mutant.location.end && (
                      <> – L{mutant.location.end.line}:C{mutant.location.end.column}</>
                    )}
                  </>
                ) : (
                  'N/A'
                )}
              </td>
              <td>{mutant.coveredBy?.join(', ') || '—'}</td>
              <td>{mutant.static ? 'Yes' : 'No'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default MutantTable