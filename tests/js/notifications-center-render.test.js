const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const JS_DIR = path.join(
  __dirname, '..', '..',
  'quarkus-app', 'src', 'main', 'resources', 'META-INF', 'resources', 'js'
);

const src = fs.readFileSync(path.join(JS_DIR, 'notifications-center.js'), 'utf8');

test('render() does not filter notifications by today-only date range', () => {
  // The bug (issue #1464) was that render() filtered items to only those
  // created between startOfDay and endOfDay, hiding all older notifications.
  // Verify the day-range filter is gone from render().
  const renderMatch = src.match(/function render\(\) \{[\s\S]*?\n\}/);
  assert.ok(renderMatch, 'render() function not found');
  const renderBody = renderMatch[0];

  // Must NOT contain the startOfDay/endOfDay filter inside render
  assert.ok(
    !renderBody.includes('startOfDay'),
    'render() still filters by startOfDay — today-only bug not fixed'
  );
  assert.ok(
    !renderBody.includes('endOfDay'),
    'render() still filters by endOfDay — today-only bug not fixed'
  );
});

test('render() still filters out dismissed notifications', () => {
  const renderMatch = src.match(/function render\(\) \{[\s\S]*?\n\}/);
  assert.ok(renderMatch, 'render() function not found');
  const renderBody = renderMatch[0];
  assert.ok(
    renderBody.includes('!n.dismissedAt'),
    'render() must still filter out dismissed notifications'
  );
});

test('render() still applies unread filter when currentFilter is unread', () => {
  const renderMatch = src.match(/function render\(\) \{[\s\S]*?\n\}/);
  assert.ok(renderMatch, 'render() function not found');
  const renderBody = renderMatch[0];
  assert.ok(
    renderBody.includes("currentFilter === 'unread'"),
    'render() must still support the unread filter'
  );
});

test('syncUnread still uses today-only range for the badge counter', () => {
  // syncUnread is separate from render and SHOULD keep the today-only
  // filter for the unread badge count — that is intentional.
  const syncMatch = src.match(/function syncUnread\(arr\) \{[\s\S]*?\n\}/);
  assert.ok(syncMatch, 'syncUnread() function not found');
  const syncBody = syncMatch[0];
  assert.ok(
    syncBody.includes('startOfDay'),
    'syncUnread() should still use startOfDay for the badge counter'
  );
});
