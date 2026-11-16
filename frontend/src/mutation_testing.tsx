import React from 'react';
import MutationTesting from './MutationTesting';
import { injectTheme } from './theme';

// See quality_dashboard.tsx for the SonarQube page-extension contract.
window.registerExtension('chillcodequality/mutation_testing', (options) => {
  injectTheme();
  const projectKey = options?.component?.key ?? '';
  const branch = options?.branchLike?.name ?? 'main';

  return (
    <MutationTesting key={`${projectKey}/${branch}`} projectKey={projectKey} branch={branch} />
  );
});
