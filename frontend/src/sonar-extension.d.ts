// SonarQube page-extension contract: the webapp exposes registerExtension and
// calls back with the page options (component: the current project, branchLike:
// the selected branch or pull request).
interface SonarExtensionOptions {
  component?: { key?: string };
  branchLike?: { name?: string };
}

interface Window {
  registerExtension: (
    key: string,
    callback: (options: SonarExtensionOptions) => import('react').ReactElement,
  ) => void;
}
