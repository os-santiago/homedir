const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const JS_DIR = path.join(__dirname, '..', '..', 'quarkus-app', 'src', 'main', 'resources', 'META-INF', 'resources', 'js');
const UTILS_SRC = fs.readFileSync(path.join(JS_DIR, 'utils.js'), 'utf8');

// utils.js is an IIFE that attaches HomeDirUtils to `window`. We evaluate it
// against a minimal DOM mock so formatDate can read document.documentElement.lang
// and navigator.language just like it does in the browser.
function loadUtils(lang) {
  const documentMock = { documentElement: { lang: lang || '' } };
  const navigatorMock = { language: 'en-US' };
  const windowMock = {};
  // eslint-disable-next-line no-new-func
  const fn = new Function('window', 'document', 'navigator', UTILS_SRC + '\nreturn window;');
  return fn(windowMock, documentMock, navigatorMock);
}

const VALID_DATE = '2026-03-15T13:45:00Z';

test('formatDate default preset (date) returns non-empty formatted string', () => {
  const { HomeDirUtils } = loadUtils('en');
  const out = HomeDirUtils.formatDate(VALID_DATE);
  assert.ok(out, 'expected non-empty output for valid date');
  assert.ok(out.includes('2026'), `expected year in output, got: ${out}`);
  assert.ok(out.includes('Mar'), `expected short month in output, got: ${out}`);
});

test('formatDate datetime preset includes time components', () => {
  const { HomeDirUtils } = loadUtils('en');
  const out = HomeDirUtils.formatDate(VALID_DATE, 'datetime');
  assert.ok(out, 'expected non-empty output for datetime preset');
  // datetime uses toLocaleString which includes hour/minute — verify digits present
  assert.ok(/\d{2}:\d{2}/.test(out), `expected HH:MM time in output, got: ${out}`);
});

test('formatDate short preset returns month and day only', () => {
  const { HomeDirUtils } = loadUtils('en');
  const out = HomeDirUtils.formatDate(VALID_DATE, 'short');
  assert.ok(out, 'expected non-empty output for short preset');
  assert.ok(out.includes('Mar'), `expected short month in output, got: ${out}`);
  // short preset should NOT include the year
  assert.ok(!out.includes('2026'), `short preset should not include year, got: ${out}`);
});

test('formatDate returns empty string for falsy input', () => {
  const { HomeDirUtils } = loadUtils('en');
  assert.strictEqual(HomeDirUtils.formatDate(null), '');
  assert.strictEqual(HomeDirUtils.formatDate(undefined), '');
  assert.strictEqual(HomeDirUtils.formatDate(''), '');
  assert.strictEqual(HomeDirUtils.formatDate(0), '');
});

test('formatDate returns empty string for invalid date strings', () => {
  const { HomeDirUtils } = loadUtils('en');
  assert.strictEqual(HomeDirUtils.formatDate('invalid'), '');
  assert.strictEqual(HomeDirUtils.formatDate('not-a-date'), '');
});

test('formatDate accepts custom Intl options', () => {
  const { HomeDirUtils } = loadUtils('en');
  const out = HomeDirUtils.formatDate(VALID_DATE, { weekday: 'long' });
  assert.ok(out, 'expected non-empty output for custom options');
  // 2026-03-15 is a Sunday
  assert.ok(/sunday/i.test(out), `expected weekday name in output, got: ${out}`);
});

test('formatDate falls back to date preset for unknown preset name', () => {
  const { HomeDirUtils } = loadUtils('en');
  const out = HomeDirUtils.formatDate(VALID_DATE, 'nonexistent');
  assert.ok(out, 'expected non-empty output for unknown preset (fallback)');
  assert.ok(out.includes('2026'), `fallback should use date preset with year, got: ${out}`);
});

test('formatDate respects document language for locale', () => {
  const { HomeDirUtils } = loadUtils('es');
  const out = HomeDirUtils.formatDate(VALID_DATE, 'short');
  assert.ok(out, 'expected non-empty output for es locale');
  // Spanish short month for March is "mar"
  assert.ok(/mar/i.test(out), `expected Spanish month abbreviation, got: ${out}`);
});
