import React from 'react';

type Rating = 'A' | 'B' | 'C' | 'D' | 'E';

interface MetricCardProps {
  title: string;
  value: string | number;
  rating?: Rating;
  trend?: 'up' | 'down' | 'stable';
  color?: string;
}

const MetricCard: React.FC<MetricCardProps> = ({ title, value, rating, trend, color }) => {
  const ratingColors: Record<Rating, string> = {
    A: 'var(--cq-green)',
    B: 'var(--cq-blue)',
    C: 'var(--cq-amber)',
    D: 'var(--cq-red)',
    E: 'var(--cq-red-deep)',
  };

  const trendIcon = {
    up: '▲',
    down: '▼',
    stable: '●',
  };

  const trendColor = {
    up: 'var(--cq-green)',
    down: 'var(--cq-red)',
    stable: 'var(--cq-violet)',
  };

  const accent = color || (rating ? ratingColors[rating] : undefined);

  // SAFETY: React.CSSProperties has no index signature for custom properties; '--cq-accent' is a valid CSS variable.
  const style = accent ? ({ '--cq-accent': accent } as React.CSSProperties) : undefined;

  return (
    <div className="cq-metric-card" style={style}>
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
  );
};

export default MetricCard;
