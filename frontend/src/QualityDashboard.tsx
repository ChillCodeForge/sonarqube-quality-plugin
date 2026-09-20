import React, { useState, useEffect } from 'react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, AreaChart, Area } from 'recharts'
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
    return (
      <div className="cq-root">
        <div className="cq-loading">
          <span className="cq-spinner" />
          Loading quality dashboard…
        </div>
      </div>
    )
  }

  return (
    <div className="cq-root">
      <header className="cq-header">
        <div className="cq-title-block">
          <div className="cq-icon-badge">✨</div>
          <div>
            <h1>Quality Overview</h1>
            <p className="cq-subtitle">Project health at a glance</p>
          </div>
        </div>
      </header>

      <section className="cq-metrics-grid">
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
          color="#2f7ff0"
        />
        <MetricCard
          title="Duplications"
          value={`${metrics.find(m => m.key === 'duplicated_lines_density')?.value || 0}%`}
          rating={metrics.find(m => m.key === 'duplicated_lines_density')?.rating}
          trend={metrics.find(m => m.key === 'duplicated_lines_density')?.trend}
          color="#7c6ff0"
        />
        <MetricCard
          title="Mutation Score"
          value={`${metrics.find(m => m.key === 'mutation_score')?.value || 0}%`}
          rating={metrics.find(m => m.key === 'mutation_score')?.rating}
          trend={metrics.find(m => m.key === 'mutation_score')?.trend}
          color="#12b886"
        />
      </section>

      <section className="cq-charts-grid">
        <div className="cq-chart-card">
          <h2>Quality History (Last 30 Days)</h2>
          <ResponsiveContainer width="100%" height={280}>
            <AreaChart data={history}>
              <defs>
                <linearGradient id="colorCoverage" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#2f7ff0" stopOpacity={0.35}/>
                  <stop offset="95%" stopColor="#2f7ff0" stopOpacity={0}/>
                </linearGradient>
                <linearGradient id="colorMutation" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#12b886" stopOpacity={0.35}/>
                  <stop offset="95%" stopColor="#12b886" stopOpacity={0}/>
                </linearGradient>
              </defs>
              <XAxis dataKey="date" tick={{ fill: 'var(--cq-text-muted)', fontSize: 12 }} axisLine={{ stroke: 'var(--cq-border)' }} />
              <YAxis domain={[0, 100]} tick={{ fill: 'var(--cq-text-muted)', fontSize: 12 }} axisLine={{ stroke: 'var(--cq-border)' }} />
              <CartesianGrid strokeDasharray="3 3" stroke="var(--cq-border)" />
              <Tooltip contentStyle={{ borderRadius: 10, border: '1px solid var(--cq-border)' }} />
              <Area type="monotone" dataKey="coverage" stroke="#2f7ff0" strokeWidth={2} fillOpacity={1} fill="url(#colorCoverage)" name="Coverage %" />
              <Area type="monotone" dataKey="mutation_score" stroke="#12b886" strokeWidth={2} fillOpacity={1} fill="url(#colorMutation)" name="Mutation Score %" />
            </AreaChart>
          </ResponsiveContainer>
        </div>

        <div className="cq-chart-card">
          <h2>Issues by Severity</h2>
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={[
              { severity: 'Blocker', count: metrics.find(m => m.key === 'blocker_violations')?.value || 0 },
              { severity: 'Critical', count: metrics.find(m => m.key === 'critical_violations')?.value || 0 },
              { severity: 'Major', count: metrics.find(m => m.key === 'major_violations')?.value || 0 },
              { severity: 'Minor', count: metrics.find(m => m.key === 'minor_violations')?.value || 0 },
              { severity: 'Info', count: metrics.find(m => m.key === 'info_violations')?.value || 0 },
            ]}>
              <XAxis dataKey="severity" tick={{ fill: 'var(--cq-text-muted)', fontSize: 12 }} axisLine={{ stroke: 'var(--cq-border)' }} />
              <YAxis tick={{ fill: 'var(--cq-text-muted)', fontSize: 12 }} axisLine={{ stroke: 'var(--cq-border)' }} />
              <CartesianGrid strokeDasharray="3 3" stroke="var(--cq-border)" />
              <Tooltip contentStyle={{ borderRadius: 10, border: '1px solid var(--cq-border)' }} />
              <Bar dataKey="count" fill="#f2495c" name="Issues" radius={[8, 8, 0, 0]} maxBarSize={56} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </section>
    </div>
  )
}

export default QualityDashboard
