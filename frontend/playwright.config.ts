import { defineConfig, devices } from '@playwright/test';

const backend = process.env.E2E_BACKEND_URL;
const port = process.env.E2E_FRONTEND_PORT;
if (!backend || !port) throw new Error('Run npm run test:e2e to provision the isolated backend.');
const baseURL = `http://127.0.0.1:${port}`;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: true,
  timeout: 30_000,
  globalTimeout: 180_000,
  reporter: [['list'], ['json', { outputFile: 'test-results/report.json' }]],
  use: { baseURL, trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: `npm run dev -- --host 127.0.0.1 --port ${port} --strictPort`,
    url: baseURL,
    reuseExistingServer: false,
    env: { VITE_API_PROXY_TARGET: backend, VITE_API_BASE_URL: '', VITE_AUTH_ENABLED: 'true' },
    timeout: 30_000,
    gracefulShutdown: { signal: 'SIGTERM', timeout: 3_000 }
  }
});
