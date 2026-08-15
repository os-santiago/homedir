const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const JS_DIR = path.join(
  __dirname, '..', '..',
  'quarkus-app', 'src', 'main', 'resources', 'META-INF', 'resources', 'js'
);

const SRC = fs.readFileSync(path.join(JS_DIR, 'notifications-center.js'), 'utf8');

// --- Minimal DOM mock factory ---
function makeEl(tag) {
  const el = {
    tagName: (tag || 'div').toUpperCase(),
    children: [],
    classList: {
      _set: new Set(),
      add(...c) { c.forEach(x => this._set.add(x)); },
      remove(...c) { c.forEach(x => this._set.delete(x)); },
      toggle(c, force) {
        if (force === true) this._set.add(c);
        else if (force === false) this._set.delete(c);
        else this._set.has(c) ? this._set.delete(c) : this._set.add(c);
        return this._set.has(c);
      },
      contains(c) { return this._set.has(c); },
    },
    _attrs: {},
    _listeners: {},
    dataset: {},
    style: {},
    innerHTML: '',
    id: '',
    checked: false,
    setAttribute(k, v) { this._attrs[k] = String(v); },
    getAttribute(k) { return this._attrs[k] || null; },
    addEventListener(ev, cb) {
      (this._listeners[ev] = this._listeners[ev] || []).push(cb);
    },
    appendChild(child) { this.children.push(child); return child; },
    querySelectorAll(sel) { return []; },
    querySelector(sel) { return null; },
    showModal() {},
    close() {},
    focus() {},
    scrollIntoView() {},
  };
  return el;
}

function setupDom(notifications) {
  const store = {};
  const localStorage = {
    getItem(k) { return store[k] || null; },
    setItem(k, v) { store[k] = String(v); },
    removeItem(k) { delete store[k]; },
  };
  if (notifications) {
    localStorage.setItem('ef_global_notifs', JSON.stringify(notifications));
  }

  const root = makeEl('section');
  root.classList.add('notifications-center');
  const listEl = makeEl('div'); listEl.id = 'notif-list';
  const emptyEl = makeEl('div'); emptyEl.id = 'empty';
  const markAllBtn = makeEl('button'); markAllBtn.id = 'markAllRead';
  const deleteBtn = makeEl('button'); deleteBtn.id = 'deleteSelected';
  const selectAllBtn = makeEl('button'); selectAllBtn.id = 'selectAll';
  const confirmDlg = makeEl('dialog'); confirmDlg.id = 'confirmDeleteAll';
  const actionsRight = makeEl('div'); actionsRight.classList.add('actions-right');

  const byId = { 'notif-list': listEl, 'empty': emptyEl, 'markAllRead': markAllBtn, 'deleteSelected': deleteBtn, 'selectAll': selectAllBtn, 'confirmDeleteAll': confirmDlg };

  const documentMock = {
    querySelector(sel) {
      if (sel === '.notifications-center') return root;
      if (sel === '.actions-right') return actionsRight;
      return null;
    },
    getElementById(id) { return byId[id] || null; },
    _docListeners: {},
    addEventListener(ev, cb) {
      (this._docListeners[ev] = this._docListeners[ev] || []).push(cb);
    },
    dispatchEvent(ev) {
      (this._docListeners[ev.type] || []).forEach(cb => cb(ev));
    },
    createElement(tag) { return makeEl(tag); },
  };

  // Patch listEl.querySelectorAll to scan rendered children
  listEl.querySelectorAll = function (sel) {
    const results = [];
    for (const child of this.children) {
      if (sel === '.js-select' && child._attrs['data-id'] && child.tagName === 'INPUT') results.push(child);
      if (sel === '.js-select:checked' && child.checked) results.push(child);
    }
    return results;
  };

  const windowMock = {
    __NOTIF_I18N__: {},
    HomeDirUtils: {
      escapeHtml(s) { return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); },
      escapeAttr(s) { return String(s).replace(/"/g, '&quot;'); },
    },
    location: { hash: '', origin: 'https://homedir.test' },
    updateUnreadFromLocal: null,
  };

  return { store, localStorage, documentMock, windowMock, listEl, emptyEl, root, actionsRight };
}

function loadModule(dom) {
  const fn = new Function('window', 'document', 'localStorage', 'navigator',
    SRC + '\nreturn { accept: window.__EF_GLOBAL_NOTIF_ACCEPT__ };');
  return fn(dom.windowMock, dom.documentMock, dom.localStorage, { language: 'en-US' });
}

// --- Tests ---

test('render() shows notifications from previous days (today-only filter removed)', () => {
  const oldDate = Date.now() - 7 * 24 * 60 * 60 * 1000; // 7 days ago
  const dom = setupDom([
    { id: 'old-1', title: 'Old notification', message: 'From last week', createdAt: oldDate },
    { id: 'today-1', title: 'Today notification', message: 'From today', createdAt: Date.now() },
  ]);
  loadModule(dom);

  // After load, render() runs. The list should have 2 children (both notifications).
  // If the today-only bug were present, only 'today-1' would render.
  const renderedIds = dom.listEl.children.map(c => c.dataset.id).filter(Boolean);
  assert.ok(renderedIds.includes('old-1'), 'old notification (7 days ago) should be visible');
  assert.ok(renderedIds.includes('today-1'), 'today notification should be visible');
  assert.strictEqual(renderedIds.length, 2, 'both notifications should render');
});

test('render() filters out dismissed notifications', () => {
  const dom = setupDom([
    { id: 'active-1', title: 'Active', message: 'Visible', createdAt: Date.now() },
    { id: 'dismissed-1', title: 'Dismissed', message: 'Hidden', createdAt: Date.now(), dismissedAt: Date.now() },
  ]);
  loadModule(dom);

  const renderedIds = dom.listEl.children.map(c => c.dataset.id).filter(Boolean);
  assert.ok(renderedIds.includes('active-1'), 'active notification should render');
  assert.ok(!renderedIds.includes('dismissed-1'), 'dismissed notification should NOT render');
  assert.strictEqual(renderedIds.length, 1, 'only non-dismissed notifications should render');
});

test('render() shows empty state when no notifications exist', () => {
  const dom = setupDom([]);
  loadModule(dom);

  // When empty, the empty element should have 'hidden' removed
  assert.ok(!dom.emptyEl.classList.contains('hidden'), 'empty state should be visible when no notifications');
  // actions-right should be hidden
  assert.ok(dom.actionsRight.classList.contains('hidden'), 'actions should be hidden when empty');
});

test('syncUnread keeps today-only range for badge counter', () => {
  const oldDate = Date.now() - 7 * 24 * 60 * 60 * 1000;
  const todayUnread = Date.now();
  const dom = setupDom([
    { id: 'old-unread', title: 'Old unread', message: 'x', createdAt: oldDate }, // unread, old
    { id: 'today-unread', title: 'Today unread', message: 'x', createdAt: todayUnread }, // unread, today
  ]);
  loadModule(dom);

  // The badge counter (ef_global_unread_count) should only count today's unread
  const badgeCount = dom.localStorage.getItem('ef_global_unread_count');
  assert.strictEqual(badgeCount, '1', 'unread badge should only count today notifications, not old ones');
});
