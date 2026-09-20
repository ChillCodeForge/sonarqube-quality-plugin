import React from 'react'
import MutationTesting from './MutationTesting'
import { injectTheme } from './theme'

// See quality_dashboard.tsx for the SonarQube page-extension contract.
declare global {
  interface Window {
    registerExtension: (key: string, callback: (options: any) => React.ReactElement) => void
  }
}

window.registerExtension('chillcodequality/mutation_testing', (options: any) => {
  injectTheme()
  const projectKey = options?.component?.key ?? ''
  const branch = options?.branchLike?.name ?? 'main'
  return <MutationTesting projectKey={projectKey} branch={branch} />
})
