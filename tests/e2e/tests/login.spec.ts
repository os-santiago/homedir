import { expect, test } from '@playwright/test';

/**
 * Login flow against the local dev profile.
 *
 * The `dev` profile disables OIDC and enables Quarkus form auth with embedded
 * users (see application.properties):
 *   - admin@example.org / adminpass (roles admin,user)
 *   - user@example.com / userpass (role user)
 */
test.describe('login flow', () => {
  test('logs in with embedded dev user and lands on profile', async ({ page }) => {
    await page.goto('/login.html');

    await expect(page.locator('h1.dev-login-title')).toHaveText('HomeDir');
    await expect(page.locator('form.dev-login-form')).toBeVisible();

    await page.locator('#username').fill('user@example.com');
    await page.locator('#password').fill('userpass');
    await Promise.all([
      page.waitForURL('**/profile'),
      page.locator('button[type="submit"]').click(),
    ]);

    await expect(page).toHaveURL(/\/profile/);
  });

  test('rejects invalid credentials', async ({ page }) => {
    await page.goto('/login.html');

    await page.locator('#username').fill('user@example.com');
    await page.locator('#password').fill('wrong-password');
    await page.locator('button[type="submit"]').click();

    // Form auth redirects back to the error page on failure.
    await expect(page).toHaveURL(/login\.html/);
  });
});
