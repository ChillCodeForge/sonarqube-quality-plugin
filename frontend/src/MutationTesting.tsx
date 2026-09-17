import React, { useState, useEffect } from 'react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts'
import Gauge from './components/Gauge'
import MetricCard from './components/MetricCard'
import MutantTable from './components/MutantTable'
import { fetchMutationReport, fetchMutationSummary } from './api'

interface MutationSummary {
  score: number
  total: number
  killed: number
  survived: number
  noCoverage: number
  timeout: number
  ignored: number
  tool: string
  language: string
}

interface MutationFile {
  path: string
  language: string
  mutants: any[]
  metrics: any
}

const MutationTesting: React.FC = () => {
  const [summary, setSummary] = useState<MutationSummary | null>(null)
  const [files, setFiles] = useState<MutationFile[]>([])
  const [selectedFile, setSelectedFile] = useState<MutationFile | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadData()
  }, [])

  const loadData = async () => {
    try {
      const [summaryData, reportData] = await Promise.all([
        fetchMutationSummary(),
        fetchMutationReport()
      ])
      setSummary(summaryData)
      if (reportData?.files) {
        setFiles(reportData.files)
      }
    } catch (error) {
      console.error('Failed to load mutation data:', error)
    } finally {
      setLoading(false)
    }
  }

  if (loading) {
    return <div className="loading">Loading mutation testing results...</div>
  }

  if (!summary) {
    return <div className="empty">No mutation testing data available for this project</div>
  }

  const COLORS = ['#00C49F', '#FF4444', '#FFA500', '#8884D8', '#0088FE']

  return (
    <div className="mutation-testing">
      <header>
        <h1>Mutation Testing</h1>
        <p className="subtitle">
          {summary.tool} / {summary.language} — {summary.total} mutants, {summary.score.toFixed(1)}% score
        </p>
      </header>

      <section className="summary-grid">
        <MetricCard title="Mutation Score" value={`${summary.score.toFixed(1)}%`} rating={getRating(summary.score)} />
        <MetricCard title="Total Mutants" value={summary.total} />
        <MetricCard title="Killed" value={summary.killed} color="#00C49F" />
        <MetricCard title="Survived" value={summary.survived} color="#FF4444" />
        <MetricCard title="No Coverage" value={summary.noCoverage} color="#FFA500" />
        <MetricCard title="Timeout" value={summary.timeout} color="#8884D8" />
        <MetricCard title="Ignored" value={summary.ignored} />
      </section>

      <section className="charts-grid">
        <div className="chart-card">
          <h2>Mutant Status Distribution</h2>
          <ResponsiveContainer width="100%" height={300}>
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
                innerRadius={60}
                outerRadius={100}
                dataKey="value"
                nameKey="name"
                label={({ name, percent }) => `${name}: ${(percent * 100).toFixed(1)}%`}
              >
                {['#00C49F', '#FF4444', '#FFA500', '#8884D8', '#0088FE'].map((color, i) => (
                  <Cell key={`cell-${i}`} fill={color} />
                ))}
              </Pie>
              <Tooltip />
            </PieChart>
          </ResponsiveContainer>
        </div>

        <div className="chart-card">
          <h2>Mutation Score Trend</h2>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={[
              { label: 'Current', score: summary.score },
            ]}>
              <XAxis dataKey="label" />
              <YAxis domain={[0, 100]} />
              <CartesianGrid strokeDasharray="3 3" />
              <Tooltip />
              <Bar dataKey="score" fill="#00C49F" name="Score %" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </section>

      <section className="files-section">
        <h2>Files ({files.length})</h2>
        <div className="files-table-container">
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
                <tr key={index} onClick={() => setSelectedFile(file)} className={selectedFile === file ? 'selected' : ''}>
                  <td>{file.path}</td>
                  <td>{file.language}</td>
                  <td>{file.mutants?.length || 0}</td>
                  <td>{file.metrics?.score?.toFixed(1) || 'N/A'}%</td>
                  <td style={{ color: '#00C49F' }}>{file.metrics?.killed || 0}</td>
                  <td style={{ color: '#FF4444' }}>{file.metrics?.survived || 0}</td>
                  <td style={{ color: '#FFA500' }}>{file.metrics?.noCoverage || 0}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {selectedFile && (
        <section className="mutants-detail">
          <div className="detail-header">
            <h3>Mutants in {selectedFile.path}</h3>
            <button onClick={() => setSelectedFile(null)}>Close</button>
          </div>
          <MutantTable mutants={selectedFile.mutants || []} />
        </section>
      )}
    </div>
  )
}

function getRating(score: number): 'A' | 'B' | 'C' | 'D' | 'E' {
  if (score >= 90) return 'A'
  if (score >= 75) return 'B'
  if (score >= 60) return 'C'
  if (score >= 40) return 'D'
  return 'E'
}

export default MutationTesting