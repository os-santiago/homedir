const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const JS_DIR = path.join(__dirname, '..', '..', 'quarkus-app', 'src', 'main', 'resources', 'META-INF', 'resources', 'js');

function readSource(file) {
  return fs.readFileSync(path.join(JS_DIR, file), 'utf8');
}

function loadEscapeHtml(src) {
  const match = src.match(/function escapeHtml\(value\) \{[\s\S]*?\n\}/);
  assert.ok(match, 'escapeHtml helper not found in bundle');
  return new Function('return ' + match[0] + ';')();
}

const CORE_BUNDLE = readSource('core-bundle.js');
const escapeHtml = loadEscapeHtml(CORE_BUNDLE);

test('escapeHtml neutralizes classic DOM XSS payloads', () => {
  const payloads = [
    '<img src=x onerror=alert(1)>',
    '<script>alert(document.cookie)</script>',
    '"><script>alert(1)</script>',
    '<svg/onload=alert(1)>',
    '<iframe src="https://evil.example"></iframe>',
    '<img src=x onerror=alert(1)>',
    '\x27; alert(1); //',
    '&lt;img src=x onerror=alert(1)&gt;'
  ];
  for (const payload of payloads) {
    const out = escapeHtml(payload);
    assert.ok(!out.includes('<'), `raw "<" survived escaping for: ${payload} -> ${out}`);
    assert.ok(!out.includes('>'), `raw ">" survived escaping for: ${payload} -> ${out}`);
    assert.ok(!out.includes('<script'), `raw <script survived for: ${payload} -> ${out}`);
  }
});

test('escapeHtml preserves legitimate plain-text notification messages', () => {
  assert.strictEqual(escapeHtml('Everything saved successfully'), 'Everything saved successfully');
  assert.strictEqual(escapeHtml('✅ Registrado correctamente'), '✅ Registrado correctamente');
  assert.strictEqual(escapeHtml('100% & done'), '100% &amp; done');
  assert.strictEqual(escapeHtml('a < b > c'), 'a &lt; b &gt; c');
  assert.strictEqual(escapeHtml(''), '');
  assert.strictEqual(escapeHtml(null), '');
  assert.strictEqual(escapeHtml(undefined), '');
});

test('notification sink escapes URL-derived messages (core-bundle.js)', () => {
  const sink = CORE_BUNDLE.match(/note\.innerHTML\s*=\s*([^;]+);/);
  assert.ok(sink, 'notification sink not found in core-bundle.js');
  assert.ok(
    sink[1].includes('escapeHtml'),
    `notification sink is NOT sanitized: note.innerHTML = ${sink[1]}`
  );
});

test('app.js source and core-bundle.js are both sanitized', () => {
  const APP_SOURCE = readSource('app.js');
  const appSink = APP_SOURCE.match(/note\.innerHTML\s*=\s*([^;]+);/);
  assert.ok(appSink, 'notification sink not found in app.js');
  assert.ok(
    appSink[1].includes('escapeHtml'),
    `app.js notification sink is NOT sanitized: note.innerHTML = ${appSink[1]}`
  );
  assert.ok(loadEscapeHtml(APP_SOURCE)('</script>') === '&lt;/script&gt;');
});

test('handleNotificationsFromUrl feeds raw params but sink sanitizes end-to-end', () => {
  const flow = CORE_BUNDLE.match(/function handleNotificationsFromUrl\(\) \{[\s\S]*?\n\}/);
  assert.ok(flow, 'handleNotificationsFromUrl not found');
  assert.ok(
    /showNotification\('(?:success|error)', params\.get\(/.test(flow[0]),
    'URL params are passed to showNotification as expected'
  );
  const sink = CORE_BUNDLE.match(/note\.innerHTML\s*=\s*([^;]+);/);
  assert.ok(sink[1].includes('escapeHtml'), 'sink must escape before DOM injection');
});
