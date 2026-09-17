import React from 'react'

interface MetricCardProps {
  title: string
  value: string | number
  rating?: 'A' | 'B' | 'C' | 'D' | 'E'
  trend?: 'up' | 'down' | 'stable'
  color?: string
}

const MetricCard: React.FC<MetricCardProps> = ({
  title,
  value,
  rating,
  trend,
  color
}) => {
  const ratingColors: Record<string, string> = {
    A: '#00C49F',
    B: '#88C0D0',
    C: '#FFA500',
    D: '#FF8C00',
    E: '#FF4444',
  }

  const trendIcon = {
    up: '▲',
    down: '▼',
    stable: '●'
  }

  const trendColor = {
    up: '#00C49F',
    down: '#FF4444',
    stable: '#8884D8',
  }

  return (
    <div className="metric-card">
      <div className="metric-title">{title}</div>
      <div className="metric-value" style={{ color: color || 'inherit' }}>
        {value}
      </div>
      {rating && (
        <div className="metric-rating" style={{ backgroundColor: ratingColors[rating] }}>
          {rating}
        </div>
      )}
      {trend && (
        <div className="metric-trend" style={{ color: trendColor[trend] }}>
          {trendIcon[trend]} {trend}
        </div>
      )}
    </div>
  )
}

export default MetricCard