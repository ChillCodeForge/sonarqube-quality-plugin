import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  injectTheme: vi.fn(),
  QualityDashboard: vi.fn(() => null),
  MutationTesting: vi.fn(() => null),
}));

vi.mock('./theme', () => ({ injectTheme: mocks.injectTheme }));
vi.mock('./QualityDashboard', () => ({ default: mocks.QualityDashboard }));
vi.mock('./MutationTesting', () => ({ default: mocks.MutationTesting }));

type ExtensionOptions =
  | {
      component?: { key?: string } | null;
      branchLike?: { name?: string } | null;
    }
  | undefined;
type ExtensionCallback = (options: ExtensionOptions) => React.ReactElement;
type RegisterExtension = (key: string, callback: ExtensionCallback) => void;

let registerExtension: ReturnType<typeof vi.fn<RegisterExtension>>;
const windowWithRegistration = window as unknown as {
  registerExtension?: RegisterExtension;
};
const originalRegisterExtension = windowWithRegistration.registerExtension;

beforeEach(() => {
  vi.resetModules();
  vi.clearAllMocks();
  registerExtension = vi.fn<RegisterExtension>();
  windowWithRegistration.registerExtension = registerExtension;
});

afterEach(() => {
  if (originalRegisterExtension) {
    windowWithRegistration.registerExtension = originalRegisterExtension;
  } else {
    delete windowWithRegistration.registerExtension;
  }
});

describe('SonarQube page extension entrypoints', () => {
  it('registers and invokes the quality dashboard with the component key', async () => {
    await import('./quality_dashboard');

    expect(registerExtension).toHaveBeenCalledOnce();
    expect(registerExtension.mock.calls[0][0]).toBe('chillcodequality/quality_dashboard');
    const callback = registerExtension.mock.calls[0][1];
    const element = callback({ component: { key: 'project:app' } });

    expect(mocks.injectTheme).toHaveBeenCalledOnce();
    expect(element.type).toBe(mocks.QualityDashboard);
    expect(element.props).toEqual({ componentKey: 'project:app' });

    const missingComponent = callback({ component: null });
    expect(missingComponent.props).toEqual({ componentKey: '' });
    expect(mocks.injectTheme).toHaveBeenCalledTimes(2);
  });

  it('registers mutation testing with the selected branch and falls back for missing options', async () => {
    await import('./mutation_testing');

    expect(registerExtension).toHaveBeenCalledOnce();
    expect(registerExtension.mock.calls[0][0]).toBe('chillcodequality/mutation_testing');
    const callback = registerExtension.mock.calls[0][1];
    const selectedBranch = callback({
      component: { key: 'project:app' },
      branchLike: { name: 'feature/reports' },
    });
    expect(selectedBranch.type).toBe(mocks.MutationTesting);
    expect(selectedBranch.props).toEqual({ projectKey: 'project:app', branch: 'feature/reports' });

    const defaultBranch = callback(undefined);
    expect(defaultBranch.props).toEqual({ projectKey: '', branch: 'main' });
    expect(mocks.injectTheme).toHaveBeenCalledTimes(2);
  });
});
