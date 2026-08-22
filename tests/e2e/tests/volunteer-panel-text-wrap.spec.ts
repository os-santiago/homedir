import { expect, test } from '@playwright/test';

/**
 * UI Regression test for issue #1486:
 * Volunteer panel: hd-list-content text gets squished instead of wrapping
 *
 * This test verifies that long unbroken text in the volunteer panel
 * (event titles and status/metadata) properly wraps within their containers
 * at narrow viewport widths.
 */
test.describe('Volunteer panel text wrapping', () => {
  test.beforeEach(async ({ page }) => {
    // Start with a narrow viewport to test text wrapping
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/private/profile');
    await page.waitForLoadState('networkidle');
  });

  test('long event title wraps in volunteer recent applications list', async ({ page }) => {
    // Navigate to the volunteer panel tab
    await page.click('[data-profile-nav="volunteer-panel"]');
    await page.waitForTimeout(500); // Wait for tab switch animation

    // Check that the volunteer panel is visible
    const volunteerPanel = page.locator('#volunteer-panel');
    await expect(volunteerPanel).toBeVisible();

    // Get the first hd-log-entry in the recent applications list
    const logEntry = volunteerPanel.locator('.hd-list-log .hd-log-entry').first();

    if (await logEntry.count() > 0) {
      const content = logEntry.locator('.hd-list-content');
      const strong = content.locator('strong');

      // Verify the content container has min-width: 0 (allows shrinking)
      const contentStyles = await content.evaluate((el) => window.getComputedStyle(el));
      expect(contentStyles.minWidth).toBe('0px');

      // Verify the strong element has overflow-wrap: break-word
      const strongStyles = await strong.evaluate((el) => window.getComputedStyle(el));
      expect(strongStyles.overflowWrap).toBe('break-word');

      // Verify text doesn't overflow the container
      const contentBox = await content.boundingBox();
      const strongBox = await strong.boundingBox();

      if (contentBox && strongBox) {
        // The strong element should not be wider than its container
        expect(strongBox.width).toBeLessThanOrEqual(contentBox.width + 2); // Allow 2px rounding
      }
    }
  });

  test('long event title wraps in volunteer open calls list', async ({ page }) => {
    // Navigate to the volunteer panel tab
    await page.click('[data-profile-nav="volunteer-panel"]');
    await page.waitForTimeout(500);

    const volunteerPanel = page.locator('#volunteer-panel');
    await expect(volunteerPanel).toBeVisible();

    // Get the first hd-log-entry in the open calls list (second list)
    const openCallsList = volunteerPanel.locator('.hd-list-log').nth(1);
    const logEntry = openCallsList.locator('.hd-log-entry').first();

    if (await logEntry.count() > 0) {
      const content = logEntry.locator('.hd-list-content');
      const strong = content.locator('strong');

      // Verify overflow-wrap is applied
      const strongStyles = await strong.evaluate((el) => window.getComputedStyle(el));
      expect(strongStyles.overflowWrap).toBe('break-word');

      // Verify text doesn't overflow
      const contentBox = await content.boundingBox();
      const strongBox = await strong.boundingBox();

      if (contentBox && strongBox) {
        expect(strongBox.width).toBeLessThanOrEqual(contentBox.width + 2);
      }
    }
  });

  test('hd-list-meta text wraps at narrow viewport', async ({ page }) => {
    // Navigate to the volunteer panel tab
    await page.click('[data-profile-nav="volunteer-panel"]');
    await page.waitForTimeout(500);

    const volunteerPanel = page.locator('#volunteer-panel');
    await expect(volunteerPanel).toBeVisible();

    // Check meta elements in recent applications list
    const metaElements = volunteerPanel.locator('.hd-list-log .hd-list-meta');

    for (let i = 0; i < Math.min(await metaElements.count(), 3); i++) {
      const meta = metaElements.nth(i);
      const metaStyles = await meta.evaluate((el) => window.getComputedStyle(el));
      expect(metaStyles.overflowWrap).toBe('break-word');

      // Verify meta doesn't overflow its container
      const content = meta.locator('..'); // parent .hd-list-content
      const contentBox = await content.boundingBox();
      const metaBox = await meta.boundingBox();

      if (contentBox && metaBox) {
        expect(metaBox.width).toBeLessThanOrEqual(contentBox.width + 2);
      }
    }
  });

  test('hd-log-entry switches to column layout at narrow viewport', async ({ page }) => {
    // At 375px width, the grid should be single column
    await page.click('[data-profile-nav="volunteer-panel"]');
    await page.waitForTimeout(500);

    const volunteerPanel = page.locator('#volunteer-panel');
    const logEntry = volunteerPanel.locator('.hd-log-entry').first();

    if (await logEntry.count() > 0) {
      const logEntryStyles = await logEntry.evaluate((el) => window.getComputedStyle(el));
      // At narrow viewport, grid-template-columns should be 1fr
      expect(logEntryStyles.gridTemplateColumns).toBe('1fr');
    }
  });
});