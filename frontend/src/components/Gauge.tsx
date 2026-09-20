import React from 'react'

interface GaugeProps {
  value: number // 0-100
  size?: number
  strokeWidth?: number
  colors?: string[]
  showValue?: boolean
}

const Gauge: React.FC<GaugeProps> = ({
  value = 0,
  size = 120,
  strokeWidth = 12,
  colors = ['#FF4444', '#FFA500', '#00C49F'],
  showValue = true
}) => {
  const radius = (size - strokeWidth) / 2
  const circumference = 2 * Math.PI * radius
  const progress = Math.max(0, Math.min(100, value)) / 100
  const dashOffset = circumference * (1 - progress)

  // Determine color based on value
  let strokeColor = colors[0]
  if (value >= 75) strokeColor = colors[2]
  else if (value >= 50) strokeColor = colors[1]

  return (
    <div className="cq-gauge" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="#e9ecf3"
          strokeWidth={strokeWidth}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={strokeColor}
          strokeWidth={strokeWidth}
          strokeDasharray={circumference}
          strokeDashoffset={dashOffset}
          strokeLinecap="round"
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
          style={{ color: strokeColor, transition: 'stroke-dashoffset 0.6s cubic-bezier(0.4, 0, 0.2, 1)' }}
        />
        {showValue && (
          <text
            x={size / 2}
            y={size / 2 + 6}
            textAnchor="middle"
            dominantBaseline="middle"
            fontSize={size * 0.2}
            fontWeight="bold"
            fill={strokeColor}
          >
            {value.toFixed(0)}%
          </text>
        )}
      </svg>
    </div>
  )
}

export default Gauge