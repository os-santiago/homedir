import { defineConfig, devices } from '@playwright/test';

/**
 * E2E test configuration for Homedir.
 *
 * Tests run against a locally started Quarkus instance (dev profile), which
 * exposes:
 *   - form login at /login.html with embedded users
 *   - the public pages (/, /eventos, /comunidad, ...)
 *   - the global notifications WebSocket at /ws/global-notifications
 *
 * Start Quarkus with:
 *   cd quarkus-app && ./mvnw quarkus:dev
 *
 * Then run:
 *   npm run test:e2e
 */
export default defineConfig({
  testDir: './tests',
  // CI forces a single worker to serialize against the one Quarkus instance,
  // so there is no parallelism there; only parallelize locally.
  fullyParallel: !process.env.CI,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI
    ? [['html', { open: 'never' }], ['github']]
    : [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:8080',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
