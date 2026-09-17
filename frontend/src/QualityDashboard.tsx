import React, { useState, useEffect } from 'react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, LineChart, Line, AreaChart, Area } from 'recharts'
import Gauge from './components/Gauge'
import MetricCard from './components/MetricCard'
import { fetchQualityMetrics, fetchQualityHistory } from './api'

interface QualityMetric {
  key: string
  name: string
  value: number | string
  rating?: 'A' | 'B' | 'C' | 'D' | 'E'
  trend?: 'up' | 'down' | 'stable'
}

interface QualityDashboardProps {
  componentKey: string
}

const QualityDashboard: React.FC<QualityDashboardProps> = ({ componentKey }) => {
  const [metrics, setMetrics] = useState<QualityMetric[]>([])
  const [history, setHistory] = useState<any[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadData()
  }, [componentKey])

  const loadData = async () => {
    try {
      const [metricsData, historyData] = await Promise.all([
        fetchQualityMetrics(componentKey),
        fetchQualityHistory(componentKey)
      ])
      setMetrics(metricsData)
      setHistory(historyData)
    } catch (error) {
      console.error('Failed to load quality data:', error)
    } finally {
      setLoading(false)
    }
  }

  if (loading) {
    return <div className="loading">Loading quality dashboard...</div>
  }

  return (
    <div className="quality-dashboard">
      <header>
        <h1>Quality Overview</h1>
        <p className="subtitle">Project health at a glance</p>
      </header>

      <section className="metrics-grid">
        <MetricCard
          title="Reliability"
          value={metrics.find(m => m.key === 'reliability_rating')?.value || 'A'}
          rating={metrics.find(m => m.key === 'reliability_rating')?.rating}
          trend={metrics.find(m => m.key === 'reliability_rating')?.trend}
        />
        <MetricCard
          title="Security"
          value={metrics.find(m => m.key === 'security_rating')?.value || 'A'}
          rating={metrics.find(m => m.key === 'security_rating')?.rating}
          trend={metrics.find(m => m.key === 'security_rating')?.trend}
        />
        <MetricCard
          title="Maintainability"
          value={metrics.find(m => m.key === 'sqale_rating')?.value || 'A'}
          rating={metrics.find(m => m.key === 'sqale_rating')?.rating}
          trend={metrics.find(m => m.key === 'sqale_rating')?.trend}
        />
        <MetricCard
          title="Coverage"
          value={`${metrics.find(m => m.key === 'coverage')?.value || 0}%`}
          rating={metrics.find(m => m.key === 'coverage')?.rating}
          trend={metrics.find(m => m.key === 'coverage')?.trend}
        />
        <MetricCard
          title="Duplications"
          value={`${metrics.find(m => m.key === 'duplicated_lines_density')?.value || 0}%`}
          rating={metrics.find(m => m.key === 'duplicated_lines_density')?.rating}
          trend={metrics.find(m => m.key === 'duplicated_lines_density')?.trend}
        />
        <MetricCard
          title="Mutation Score"
          value={`${metrics.find(m => m.key === 'mutation_score')?.value || 0}%`}
          rating={metrics.find(m => m.key === 'mutation_score')?.rating}
          trend={metrics.find(m => m.key === 'mutation_score')?.trend}
        />
      </section>

      <section className="charts-grid">
        <div className="chart-card">
          <h2>Quality History (Last 30 Days)</h2>
          <ResponsiveContainer width="100%" height={300}>
            <AreaChart data={history}>
              <defs>
                <linearGradient id="colorCoverage" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#8884d8" stopOpacity={0.3}/>
                  <stop offset="95%" stopColor="#8884d8" stopOpacity={0}/>
                </linearGradient>
                <linearGradient id="colorMutation" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#00C49F" stopOpacity={0.3}/>
                  <stop offset="95%" stopColor="#00C49F" stopOpacity={0}/>
                </linearGradient>
              </defs>
              <XAxis dataKey="date" />
              <YAxis domain={[0, 100]} />
              <CartesianGrid strokeDasharray="3 3" />
              <Tooltip />
              <Area type="monotone" dataKey="coverage" stroke="#8884d8" fillOpacity={1} fill="url(#colorCoverage)" name="Coverage %" />
              <Area type="monotone" dataKey="mutation_score" stroke="#00C49F" fillOpacity={1} fill="url(#colorMutation)" name="Mutation Score %" />
            </AreaChart>
          </ResponsiveContainer>
        </div>

        <div className="chart-card">
          <h2>Issues by Severity</h2>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={[
              { severity: 'Blocker', count: metrics.find(m => m.key === 'blocker_violations')?.value || 0 },
              { severity: 'Critical', count: metrics.find(m => m.key === 'critical_violations')?.value || 0 },
              { severity: 'Major', count: metrics.find(m => m.key === 'major_violations')?.value || 0 },
              { severity: 'Minor', count: metrics.find(m => m.key === 'minor_violations')?.value || 0 },
              { severity: 'Info', count: metrics.find(m => m.key === 'info_violations')?.value || 0 },
            ]}>
              <XAxis dataKey="severity" />
              <YAxis />
              <CartesianGrid strokeDasharray="3 3" />
              <Tooltip />
              <Bar dataKey="count" fill="#ff4444" name="Issues" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </section>
    </div>
  )
}

export default QualityDashboard