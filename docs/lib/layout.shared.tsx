import type { BaseLayoutProps } from 'fumadocs-ui/layouts/shared';
import { appName } from './shared';

export function baseOptions(): BaseLayoutProps {
  return {
    nav: {
      title: appName,
    },
    // githubUrl: `https://github.com/${gitConfig.user}/${gitConfig.repo}`,
    // (disabled while the source repo is private)
  };
}
