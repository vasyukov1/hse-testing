const { defineConfig, devices } = require('@playwright/test');

const baseURL = process.env.BASE_URL || 'http://localhost:8091';

module.exports = defineConfig({
  testDir: './tests',
  timeout: 15_000,
  expect: {
    timeout: 5_000,
  },
  reporter: [['list']],
  use: {
    baseURL,
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'java -jar tradingboatmodel-0.2.1-SNAPSHOT.jar',
    url: baseURL,
    reuseExistingServer: true,
    timeout: 30_000,
  },
});
