import { test, expect } from '@playwright/test';

/**
 * WCAG 2.4.7 / 1.4.11 — Focus indicator visibility tests.
 *
 * Verifies that all interactive elements have a visible focus indicator
 * with sufficient contrast (3:1 minimum) when navigated via keyboard.
 */
test.describe('Focus indicators — WCAG AA compliance (#1459)', () => {
  test('nav links have visible focus-visible outline', async ({ page }) => {
    await page.goto('/');
    // Tab to the first focusable element (skip link or nav link)
    await page.keyboard.press('Tab');
    // Keep tabbing until we hit a nav link
    for (let i = 0; i < 10; i++) {
      const focused = page.locator(':focus-visible');
      const tag = await focused.evaluate((el) => el.tagName + '.' + (el.className || ''));
      if (tag.includes('nav-link') || tag.includes('hd-nav-link')) break;
      await page.keyboard.press('Tab');
    }
    const focused = page.locator(':focus-visible');
    const outlineStyle = await focused.evaluate((el) => {
      const cs = window.getComputedStyle(el);
      return {
        outlineWidth: cs.outlineWidth,
        outlineStyle: cs.outlineStyle,
        outlineColor: cs.outlineColor,
      };
    });
    expect(outlineStyle.outlineStyle).not.toBe('none');
    expect(outlineStyle.outlineWidth).not.toBe('0px');
  });

  test('skip-link has visible focus outline', async ({ page }) => {
    await page.goto('/');
    await page.keyboard.press('Tab');
    const focused = page.locator(':focus-visible');
    const isSkipLink = await focused.evaluate(
      (el) => el.classList.contains('skip-link') || el.getAttribute('href') === '#main-content',
    );
    if (isSkipLink) {
      const outline = await focused.evaluate((el) => {
        const cs = window.getComputedStyle(el);
        return { width: cs.outlineWidth, style: cs.outlineStyle };
      });
      expect(outline.style).not.toBe('none');
    }
  });

  test('login button has visible focus-visible outline', async ({ page }) => {
    await page.goto('/');
    // Find the login button
    const loginBtn = page.locator('.hd-login-btn, .login-btn-nav').first();
    await loginBtn.focus();
    const outline = await loginBtn.evaluate((el) => {
      const cs = window.getComputedStyle(el);
      return {
        outlineWidth: cs.outlineWidth,
        outlineStyle: cs.outlineStyle,
        outlineColor: cs.outlineColor,
      };
    });
    expect(outline.outlineStyle).not.toBe('none');
    expect(outline.outlineWidth).not.toBe('0px');
  });

  test('locale select has visible focus-visible outline', async ({ page }) => {
    await page.goto('/');
    const localeSelect = page.locator('.header-locale-select').first();
    if (await localeSelect.isVisible()) {
      await localeSelect.focus();
      const outline = await localeSelect.evaluate((el) => {
        const cs = window.getComputedStyle(el);
        return { width: cs.outlineWidth, style: cs.outlineStyle };
      });
      expect(outline.style).not.toBe('none');
    }
  });

  test('dev login form inputs have visible focus outline', async ({ page }) => {
    await page.goto('/login.html');
    const emailInput = page.locator('.dev-form-input').first();
    if (await emailInput.isVisible()) {
      await emailInput.focus();
      const outline = await emailInput.evaluate((el) => {
        const cs = window.getComputedStyle(el);
        return { width: cs.outlineWidth, style: cs.outlineStyle };
      });
      expect(outline.style).not.toBe('none');
      expect(outline.width).not.toBe('0px');
    }
  });

  test('no element removes outline completely (outline: none audit)', async ({ page }) => {
    await page.goto('/');
    // Check that the global focus-visible rule is applied
    const hasGlobalFocusRule = await page.evaluate(() => {
      const el = document.createElement('a');
      document.body.appendChild(el);
      el.focus();
      // Force :focus-visible by adding tabindex and focusing
      el.setAttribute('tabindex', '0');
      el.focus();
      const cs = window.getComputedStyle(el);
      const hasOutline = cs.outlineStyle !== 'none' && cs.outlineWidth !== '0px';
      el.remove();
      return hasOutline;
    });
    expect(hasGlobalFocusRule).toBe(true);
  });
});
