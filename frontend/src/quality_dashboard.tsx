import React from 'react'
import QualityDashboard from './QualityDashboard'

// SonarQube page-extension contract (docs.sonarsource.com "Adding pages to
// the webapp" + SonarSource/sonar-custom-plugin-example admin_page/index.js):
// window.registerExtension(fullPageKey, options => <Component/>)
// - fullPageKey = "<plugin_key>/<page_id>", must match PageDefinition's key
// - options.component: current project/component (present because this page
//   has Scope.COMPONENT); options.el / options.currentUser also available.
// SonarQube's own React runtime mounts the returned element - no manual
// ReactDOM/createRoot call needed here.
declare global {
  interface Window {
    registerExtension: (key: string, callback: (options: any) => React.ReactElement) => void
  }
}

window.registerExtension('chillcodequality/quality_dashboard', (options: any) => {
  const componentKey = options?.component?.key ?? ''
  return <QualityDashboard componentKey={componentKey} />
})
