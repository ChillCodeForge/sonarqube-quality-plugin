import React from 'react'
import { createRoot } from 'react-dom/client'
import QualityDashboard from './QualityDashboard'

const container = document.getElementById('chillcode-quality-dashboard')
if (container) {
  createRoot(container).render(<QualityDashboard />)
}