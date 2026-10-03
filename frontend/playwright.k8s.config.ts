import { defineConfig, devices } from '@playwright/test';

if (!process.env.E2E_ADMIN_EMAIL || !process.env.E2E_ADMIN_PASSWORD) {
  throw new Error('Provide the Kubernetes laboratory administrator credentials.');
}

export default defineConfig({
  testDir: './e2e',
  testMatch: 'commerce.spec.ts',
  grep: /commercial flow updates stock and dashboard without reload/,
  workers: 1,
  retries: 0,
  forbidOnly: true,
  timeout: 60_000,
  reporter: 'list',
  use: {
    ...devices['Desktop Chrome'],
    baseURL: process.env.E2E_K8S_URL || 'http://127.0.0.1:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  }
});
