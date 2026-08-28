import { test, expect } from '@playwright/test';

/**
 * WCAG 2.4.7 / 1.4.11 — Focus indicator visibility and contrast tests.
 *
 * Verifies that interactive elements have a visible focus indicator with
 * sufficient contrast (3:1 minimum) when navigated via keyboard.
 */

/**
 * Parse a CSS color string into RGB channels.
 * Handles hex (#rrggbb, #rgb), rgb(), and rgba() formats.
 * Returns null for named colors or unparseable values.
 */
function parseColor(color: string): [number, number, number] | null {
  color = color.trim();
  // hex #rrggbb
  const hex6 = color.match(/^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i);
  if (hex6) {
    return [parseInt(hex6[1], 16), parseInt(hex6[2], 16), parseInt(hex6[3], 16)];
  }
  // hex #rgb
  const hex3 = color.match(/^#([0-9a-f])([0-9a-f])([0-9a-f])$/i);
  if (hex3) {
    return [
      parseInt(hex3[1] + hex3[1], 16),
      parseInt(hex3[2] + hex3[2], 16),
      parseInt(hex3[3] + hex3[3], 16),
    ];
  }
  // rgb(r, g, b) or rgba(r, g, b, a)
  const rgbMatch = color.match(/^rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/);
  if (rgbMatch) {
    return [parseInt(rgbMatch[1]), parseInt(rgbMatch[2]), parseInt(rgbMatch[3])];
  }
  return null;
}

/**
 * Calculate the relative luminance of an RGB color per WCAG 2.1.
 * https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
 */
function relativeLuminance(r: number, g: number, b: number): number {
  const toLinear = (c: number) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * toLinear(r) + 0.7152 * toLinear(g) + 0.0722 * toLinear(b);
}

/**
 * Calculate the contrast ratio between two RGB colors per WCAG 2.1.
 * Returns a ratio between 1:1 and 21:1.
 */
function contrastRatio(
  c1: [number, number, number],
  c2: [number, number, number],
): number {
  const l1 = relativeLuminance(c1[0], c1[1], c1[2]);
  const l2 = relativeLuminance(c2[0], c2[1], c2[2]);
  const lighter = Math.max(l1, l2);
  const darker = Math.min(l1, l2);
  return (lighter + 0.05) / (darker + 0.05);
}

/**
 * Get the effective background color *behind* an element by walking up the DOM
 * starting from the element's parent. This is the adjacent color against which
 * an outline (drawn outside the border) is measured for contrast.
 * Skips semi-transparent backgrounds (alpha < 0.9) to find a more opaque ancestor.
 */
async function getEffectiveBackground(
  page: import('@playwright/test').Page,
  selector: string,
): Promise<[number, number, number]> {
  return page.locator(selector).evaluate((el) => {
    // Start from parent — the outline is drawn outside the element's border,
    // so contrast is measured against what's behind/around the element.
    let current: Element | null = el.parentElement;
    while (current) {
      const bg = window.getComputedStyle(current).backgroundColor;
      if (bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') {
        // Extract alpha if present
        const rgbaMatch = bg.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*([\d.]+))?\s*\)/);
        if (rgbaMatch) {
          const alpha = rgbaMatch[4] !== undefined ? parseFloat(rgbaMatch[4]) : 1.0;
          // Only use this background if it's sufficiently opaque
          if (alpha >= 0.9) {
            return [parseInt(rgbaMatch[1]), parseInt(rgbaMatch[2]), parseInt(rgbaMatch[3])];
          }
        }
      }
      current = current.parentElement;
    }
    // Fallback: body background or black
    const bodyBg = window.getComputedStyle(document.body).backgroundColor;
    const m = bodyBg.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/);
    if (m) return [parseInt(m[1]), parseInt(m[2]), parseInt(m[3])];
    return [0, 0, 0];
  });
}

/**
 * Assert that an element has a visible focus indicator with ≥ 3:1 contrast.
 * Checks outline width, style, and contrast ratio against effective background.
 */
async function assertFocusIndicator(
  page: import('@playwright/test').Page,
  selector: string,
  elementName: string,
) {
  const el = page.locator(selector);
  const outline = await el.evaluate((node) => {
    const cs = window.getComputedStyle(node);
    return {
      outlineWidth: cs.outlineWidth,
      outlineStyle: cs.outlineStyle,
      outlineColor: cs.outlineColor,
    };
  });

  // 1. Outline must not be none or zero-width
  expect(outline.outlineStyle, `${elementName}: outline style must not be none`).not.toBe('none');
  expect(outline.outlineWidth, `${elementName}: outline width must not be 0px`).not.toBe('0px');

  // 2. Calculate contrast ratio of outline color vs effective background
  const outlineRgb = parseColor(outline.outlineColor);
  const bgRgb = await getEffectiveBackground(page, selector);

  if (outlineRgb) {
    const ratio = contrastRatio(outlineRgb, bgRgb);
    expect(
      ratio,
      `${elementName}: outline contrast ratio must be ≥ 3:1 (got ${ratio.toFixed(2)}:1)`,
    ).toBeGreaterThanOrEqual(3.0);
  }
}

test.describe('Focus indicators — WCAG AA compliance (#1459)', () => {
  test('nav links have visible focus-visible outline with 3:1 contrast', async ({ page }) => {
    await page.goto('/');
    // Tab through focusable elements until we reach a nav link
    let foundNav = false;
    for (let i = 0; i < 15; i++) {
      await page.keyboard.press('Tab');
      const focusedTag = await page.locator(':focus-visible').evaluate((el) => {
        return el.tagName + ' ' + (el.className || '');
      });
      if (focusedTag.includes('nav-link') || focusedTag.includes('hd-nav-link')) {
        foundNav = true;
        break;
      }
    }
    expect(foundNav, 'Should reach a nav link via keyboard Tab').toBe(true);
    await assertFocusIndicator(page, ':focus-visible', 'nav link');
  });

  test('skip-link has visible focus outline with 3:1 contrast', async ({ page }) => {
    await page.goto('/');
    await page.keyboard.press('Tab');
    const focusedInfo = await page.locator(':focus-visible').evaluate((el) => ({
      isSkipLink: el.classList.contains('skip-link') || el.getAttribute('href') === '#main-content',
      className: el.className,
    }));
    expect(focusedInfo.isSkipLink, 'First Tab target should be the skip-link').toBe(true);
    await assertFocusIndicator(page, ':focus-visible', 'skip-link');
  });

  test('login button has visible focus-visible outline with 3:1 contrast', async ({ page }) => {
    await page.goto('/');
    const loginBtn = page.locator('.hd-login-btn, .login-btn-nav').first();
    await expect(loginBtn).toBeVisible();
    await loginBtn.focus();
    await assertFocusIndicator(page, '.hd-login-btn, .login-btn-nav', 'login button');
  });

  test('locale select has visible focus-visible outline with 3:1 contrast', async ({ page }) => {
    await page.goto('/');
    const localeSelect = page.locator('.header-locale-select').first();
    await expect(localeSelect).toBeVisible();
    await localeSelect.focus();
    await assertFocusIndicator(page, '.header-locale-select', 'locale select');
  });

  test('dev login form inputs have visible focus outline with 3:1 contrast', async ({ page }) => {
    await page.goto('/login.html');
    const emailInput = page.locator('.dev-form-input').first();
    await expect(emailInput).toBeVisible();
    await emailInput.focus();
    // Use nth(0) to avoid strict mode violation (there are 2 .dev-form-input elements)
    const outline = await emailInput.evaluate((node) => {
      const cs = window.getComputedStyle(node);
      return {
        outlineWidth: cs.outlineWidth,
        outlineStyle: cs.outlineStyle,
        outlineColor: cs.outlineColor,
      };
    });
    expect(outline.outlineStyle, 'dev login input: outline style must not be none').not.toBe('none');
    expect(outline.outlineWidth, 'dev login input: outline width must not be 0px').not.toBe('0px');
    // Dev login page has its own dark background — verify contrast
    const outlineRgb = parseColor(outline.outlineColor);
    if (outlineRgb) {
      const bgRgb = await page.locator('.dev-form-input').first().evaluate((el) => {
        let current: Element | null = el.parentElement;
        while (current) {
          const bg = window.getComputedStyle(current).backgroundColor;
          if (bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') {
            const rgbaMatch = bg.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*([\d.]+))?\s*\)/);
            if (rgbaMatch) {
              const alpha = rgbaMatch[4] !== undefined ? parseFloat(rgbaMatch[4]) : 1.0;
              if (alpha >= 0.9) {
                return [parseInt(rgbaMatch[1]), parseInt(rgbaMatch[2]), parseInt(rgbaMatch[3])];
              }
            }
          }
          current = current.parentElement;
        }
        return [0, 0, 0];
      });
      const ratio = contrastRatio(outlineRgb, bgRgb);
      expect(
        ratio,
        `dev login input: outline contrast ratio must be ≥ 3:1 (got ${ratio.toFixed(2)}:1)`,
      ).toBeGreaterThanOrEqual(3.0);
    }
  });

  test('global :focus-visible audit on rendered interactive elements', async ({ page }) => {
    await page.goto('/');
    // Audit rendered interactive elements on the page, not a detached anchor
    const interactiveSelectors = [
      'a[href]',
      'button',
      'input',
      'select',
      'textarea',
      '[tabindex]',
    ];
    for (const selector of interactiveSelectors) {
      const elements = page.locator(selector);
      const count = await elements.count();
      for (let i = 0; i < Math.min(count, 5); i++) {
        const el = elements.nth(i);
        if (!(await el.isVisible())) continue;
        await el.focus();
        const outline = await el.evaluate((node) => {
          const cs = window.getComputedStyle(node);
          return {
            outlineWidth: cs.outlineWidth,
            outlineStyle: cs.outlineStyle,
            outlineColor: cs.outlineColor,
            tag: node.tagName,
            className: node.className || '',
          };
        });
        // Every visible interactive element must have a non-none outline
        expect(
          outline.outlineStyle,
          `${outline.tag}.${outline.className}: outline must not be none`,
        ).not.toBe('none');
        expect(
          outline.outlineWidth,
          `${outline.tag}.${outline.className}: outline width must not be 0px`,
        ).not.toBe('0px');
      }
    }
  });
});
