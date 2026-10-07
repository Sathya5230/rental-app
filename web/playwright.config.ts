import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  use: { ...devices['Pixel 7'], baseURL: 'http://localhost:3100' },
  webServer: { command: 'npm run build && npx next start -p 3100', url: 'http://localhost:3100', timeout: 240_000, reuseExistingServer: false },
});
