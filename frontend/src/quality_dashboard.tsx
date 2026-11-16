import React from 'react';
import QualityDashboard from './QualityDashboard';
import { injectTheme } from './theme';

// SonarQube page-extension contract (docs.sonarsource.com "Adding pages to
// the webapp" + SonarSource/sonar-custom-plugin-example admin_page/index.js):
// window.registerExtension(fullPageKey, options => <Component/>)
// - fullPageKey = "<plugin_key>/<page_id>", must match PageDefinition's key
// - options.component: current project/component (present because this page
//   has Scope.COMPONENT); options.el / options.currentUser also available.
// SonarQube's own React runtime mounts the returned element - no manual
// ReactDOM/createRoot call needed here.
window.registerExtension('chillcodequality/quality_dashboard', (options) => {
  injectTheme();
  const componentKey = options?.component?.key ?? '';

  return <QualityDashboard key={componentKey} componentKey={componentKey} />;
});
