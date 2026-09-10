const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const JS_DIR = path.join(__dirname, '..', '..', 'quarkus-app', 'src', 'main', 'resources', 'META-INF', 'resources', 'js');

function readSource(file) {
  return fs.readFileSync(path.join(JS_DIR, file), 'utf8');
}

const UTILS_SRC = readSource('utils.js');
const CORE_BUNDLE = readSource('core-bundle.js');
function loadEscapeHtml(src) {
  const windowMock = {};
  const fn = new Function('window', UTILS_SRC + '\nreturn window.HomeDirUtils.escapeHtml;');
  return fn(windowMock);
}

const escapeHtml = loadEscapeHtml();
const escapeAttr = escapeHtml;

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

test('escapeHtml handles Unicode/emoji mixed with markup', () => {
  assert.strictEqual(escapeHtml('✅ <b>ok</b>'), '✅ &lt;b&gt;ok&lt;/b&gt;');
  assert.strictEqual(escapeHtml('🎉 ¡Éxito! <strong>100%</strong>'), '🎉 ¡Éxito! &lt;strong&gt;100%&lt;/strong&gt;');
  assert.strictEqual(escapeHtml('тест <a href="#">link</a>'), 'тест &lt;a href=&quot;#&quot;&gt;link&lt;/a&gt;');
});

test('escapeHtml handles long strings with mixed special characters', () => {
  const long = '&'.repeat(200) + '<>"\''.repeat(100) + ' ✅ '.repeat(50);
  const out = escapeHtml(long);
  assert.strictEqual(out, '&amp;'.repeat(200) + '&lt;&gt;&quot;&#039;'.repeat(100) + ' ✅ '.repeat(50));
  assert.ok(out.length > long.length, 'escaped output should be longer than raw input');
});

test('escapeHtml never emits unescaped XSS triggers in output', () => {
  const payloads = [
    '<script>alert(1)</script>',
    '<img src=x onerror=alert(1)>',
    '<svg onload=alert(1)>',
    '<body onload=alert(1)>',
    '"><img src=x onerror=alert(document.domain)>'
  ];
  for (const payload of payloads) {
    const out = escapeHtml(payload);
    assert.ok(!/<[a-z]+\s/i.test(out), `an unescaped tag remains: ${payload} -> ${out}`);
    assert.ok(!/<(script|img|svg|body|iframe)[\s>]/i.test(out), `dangerous tag remains: ${payload} -> ${out}`);
    assert.ok(!/<\s*(script|img|svg|body)[^>]*\son(error|load)\s*=/i.test(out), `event handler remains: ${payload} -> ${out}`);
  }
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

test('bounty-hunters.js escapes dynamic fields to prevent DOM XSS', async () => {
  const bountyHuntersPath = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'META-INF', 'resources', 'js', 'bounty-hunters.js');
  const BOUNTY_HUNTERS_SRC = fs.readFileSync(bountyHuntersPath, 'utf8');

  // Set up DOM mocks
  const leaderboardTable = { innerHTML: '' };
  const documentMock = {
    getElementById: (id) => id === 'bounty-leaderboard-body' ? leaderboardTable : null,
    addEventListener: (event, cb) => {
      if (event === 'DOMContentLoaded') {
        cb();
      }
    }
  };

  // Mock global fetch
  const mockHunters = [
    {
      userId: '<img src=x onerror=alert(1)>',
      totalPoints: '<img src=x onerror=alert(2)>',
      level: '<img src=x onerror=alert(3)>'
    }
  ];
  const globalMock = {
    fetch: () => Promise.resolve({
      json: () => Promise.resolve(mockHunters)
    }),
    console: console,
    HomeDirUtils: { escapeHtml, escapeAttr }
  };

  // Run the script
  const fn = new Function('window', 'document', 'fetch', 'HomeDirUtils', 'console', BOUNTY_HUNTERS_SRC);
  fn(globalMock, documentMock, globalMock.fetch, globalMock.HomeDirUtils, globalMock.console);

  // Wait for promise resolution
  await new Promise(resolve => setTimeout(resolve, 10));

  // Assertions
  const html = leaderboardTable.innerHTML;
  assert.ok(html, 'leaderboard innerHTML should not be empty');
  assert.ok(!html.includes('<img src=x onerror='), 'XSS payload image tags must be escaped');
  assert.ok(html.includes('&lt;img src=x onerror='), 'XSS payload image tags must be HTML-escaped');
});

test('bounty-hunters.js safely extracts and escapes nested object properties to prevent DOM XSS', async () => {
  const bountyHuntersPath = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'META-INF', 'resources', 'js', 'bounty-hunters.js');
  const BOUNTY_HUNTERS_SRC = fs.readFileSync(bountyHuntersPath, 'utf8');

  // Set up DOM mocks
  const leaderboardTable = { innerHTML: '' };
  const documentMock = {
    getElementById: (id) => id === 'bounty-leaderboard-body' ? leaderboardTable : null,
    addEventListener: (event, cb) => {
      if (event === 'DOMContentLoaded') {
        cb();
      }
    }
  };

  // Mock global fetch returning nested objects with XSS payloads
  const mockHunters = [
    {
      userId: 'nested-user',
      totalPoints: {
        value: '<img src=x onerror=alert(points_nested_value_xss)>',
        amount: '<img src=x onerror=alert(points_nested_amount_xss)>'
      },
      level: {
        displayName: '<img src=x onerror=alert(level_nested_display_xss)>',
        rewardFrameId: '<img src=x onerror=alert(level_nested_frame_xss)>'
      }
    }
  ];
  const globalMock = {
    fetch: () => Promise.resolve({
      json: () => Promise.resolve(mockHunters)
    }),
    console: console,
    HomeDirUtils: { escapeHtml, escapeAttr }
  };

  // Run the script
  const fn = new Function('window', 'document', 'fetch', 'HomeDirUtils', 'console', BOUNTY_HUNTERS_SRC);
  fn(globalMock, documentMock, globalMock.fetch, globalMock.HomeDirUtils, globalMock.console);

  // Wait for promise resolution
  await new Promise(resolve => setTimeout(resolve, 10));

  // Assertions
  const html = leaderboardTable.innerHTML;
  assert.ok(html, 'leaderboard innerHTML should not be empty');

  // Verify that no unescaped tags exist from the nested XSS payloads
  assert.ok(!html.includes('<img src=x onerror=alert(points_nested_value_xss)>'), 'nested points XSS payload must be escaped');
  assert.ok(!html.includes('<img src=x onerror=alert(level_nested_display_xss)>'), 'nested level display XSS payload must be escaped');
  assert.ok(!html.includes('<img src=x onerror=alert(level_nested_frame_xss)>'), 'nested level frame XSS payload must be escaped');

  // Verify HTML-escaped representations are present
  assert.ok(html.includes('&lt;img src=x onerror=alert(points_nested_value_xss)&gt;'), 'nested points HTML-escaped');
  assert.ok(html.includes('&lt;img src=x onerror=alert(level_nested_display_xss)&gt;'), 'nested level display HTML-escaped');
  assert.ok(html.includes('&lt;img src=x onerror=alert(level_nested_frame_xss)'), 'nested level frame attribute escaped');
});
