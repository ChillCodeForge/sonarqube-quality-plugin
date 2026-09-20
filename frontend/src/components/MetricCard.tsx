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
    A: '#12b886',
    B: '#2f7ff0',
    C: '#f5a623',
    D: '#f2495c',
    E: '#c92a2a',
  }

  const trendIcon = {
    up: '▲',
    down: '▼',
    stable: '●'
  }

  const trendColor = {
    up: '#12b886',
    down: '#f2495c',
    stable: '#7c6ff0',
  }

  const accent = color || (rating ? ratingColors[rating] : undefined)

  return (
    <div
      className="cq-metric-card"
      style={accent ? ({ '--cq-accent': accent } as React.CSSProperties) : undefined}
    >
      <div className="cq-metric-title">{title}</div>
      <div className="cq-metric-value" style={{ color: color || 'inherit' }}>
        {value}
        {rating && (
          <span className="cq-metric-rating" style={{ backgroundColor: ratingColors[rating] }}>
            {rating}
          </span>
        )}
      </div>
      {trend && (
        <div className="cq-metric-trend" style={{ color: trendColor[trend] }}>
          {trendIcon[trend]} {trend}
        </div>
      )}
    </div>
  )
}

export default MetricCard
