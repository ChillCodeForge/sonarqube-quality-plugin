import React from 'react'
import { createRoot } from 'react-dom/client'
import MutationTesting from './MutationTesting'

const container = document.getElementById('chillcode-mutation-testing')
if (container) {
  createRoot(container).render(<MutationTesting />)
}