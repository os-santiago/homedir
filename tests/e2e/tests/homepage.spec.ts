import { expect, test } from '@playwright/test';

/**
 * Smoke tests for the main public pages: they must render, serve their JS
 * bundles, and bootstrap the authentication state.
 */
test.describe('homepage and main pages', () => {
  test('loads the homepage with auth bootstrap and JS bundle', async ({ page }) => {
    const pageErrors: string[] = [];
    page.on('pageerror', (err) => pageErrors.push(err.message));

    await page.goto('/');
    await expect(page).toHaveTitle(/Home/i);

    const authBootstrap = await page
      .locator('script')
      .evaluateAll((scripts) =>
        scripts.some((s) => (s.textContent || '').includes('window.userAuthenticated')),
      );
    expect(authBootstrap).toBeTruthy();

    // The core-bundle.js must be loaded and executed (it sets COLLECTIBLES).
    await page.waitForLoadState('networkidle');
    const collectiblesDefined = await page.evaluate(() => !!window.COLLECTIBLES);
    expect(collectiblesDefined).toBeTruthy();

    expect(pageErrors).toEqual([]);
  });

  test('serves critical static assets', async ({ page, request }) => {
    for (const asset of ['/js/core-bundle.js', '/js/app.js', '/css/homedir.css']) {
      const res = await request.get(asset);
      expect(res.status(), `asset ${asset}`).toBe(200);
    }
  });

  test('loads public pages', async ({ page }) => {
    for (const path of ['/eventos', '/comunidad', '/proyectos'] as const) {
      await page.goto(path);
      await expect(page).toHaveURL(new RegExp(path.replace('/', '\\/')));
      await page.waitForLoadState('networkidle');
      expect((await page.content()).length).toBeGreaterThan(0);
    }
  });
});
