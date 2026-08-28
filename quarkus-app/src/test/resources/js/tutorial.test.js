// tutorial.test.js - Browser tests for tutorial.js state transitions (ADEV Rule #21)
// Run: node --test quarkus-app/src/test/resources/js/tutorial.test.js
const { describe, it, beforeEach, afterEach } = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const TUTORIAL_JS = fs.readFileSync(
    path.join(__dirname, '..', '..', '..', 'main', 'resources', 'META-INF', 'resources', 'js', 'tutorial.js'),
    'utf8'
);
function createContext() {
    const localStorage = {
        _store: {},
        getItem(k) { return this._store[k] || null; },
        setItem(k, v) { this._store[k] = v; },
        removeItem(k) { delete this._store[k]; }
    };
    const document = {
        _elements: {},
        querySelectorAll(sel) {
            if (sel === '[data-tutorial]') {
                return Object.values(this._elements).filter(el => 
                    el.dataset?.tutorial || el.attributes?.['data-tutorial']
                );
            }
            if (sel.startsWith('[data-tutorial="')) {
                const id = sel.match(/data-tutorial="([^"]+)"/)[1];
                return Object.values(this._elements).filter(el => el.dataset?.tutorial === id);
            }
            if (sel === '[data-tutorial-dismiss]') {
                return Object.values(this._elements).filter(el => el.dataset?.tutorialDismiss);
            }
            if (sel === '[data-replay-tutorials]') {
                return Object.values(this._elements).filter(el => el.dataset?.replayTutorials);
            }
            return [];
        },
        querySelector(sel) { return this.querySelectorAll(sel)[0] || null; },
        addEventListener() {},
        createElement() {
            const el = { dataset: {}, attributes: {}, style: {} };
            el.setAttribute = function(k, v) { this.attributes[k] = v; };
            el.removeAttribute = function(k) { delete this.attributes[k]; };
            el.getAttribute = function(k) { return this.attributes[k]; };
            el.addEventListener = function() {};
            return el;
        }
    };
    const context = {
        window: { localStorage, console: { warn: () => {} } },
        document,
        module: { exports: {} },
        console: { warn: () => {} }
    };
    vm.createContext(context);
    vm.runInContext(TUTORIAL_JS, context);
    return { context, localStorage, document };
}
describe('tutorial.js state transitions', () => {
    let ctx, ls, doc;
    beforeEach(() => {
        const created = createContext();
        ctx = created.context;
        ls = created.localStorage;
        doc = created.document;
    });
    afterEach(() => { ls._store = {}; });
    it('readSeenTutorials returns empty object when localStorage empty', () => {
        const result = ctx.module.exports.readSeenTutorials();
        assert.strictEqual(typeof result, 'object');
        assert.strictEqual(Object.keys(result).length, 0);
    });
    it('readSeenTutorials returns parsed object when valid', () => {
        ls.setItem('homedir_tutorial_seen', JSON.stringify({ hub: true, 'hub-how': true }));
        const result = ctx.module.exports.readSeenTutorials();
        assert.strictEqual(result.hub, true);
        assert.strictEqual(result['hub-how'], true);
    });
    it('readSeenTutorials handles corrupted JSON gracefully', () => {
        ls.setItem('homedir_tutorial_seen', 'not-valid-json{');
        const result = ctx.module.exports.readSeenTutorials();
        assert.strictEqual(typeof result, 'object');
        assert.strictEqual(Object.keys(result).length, 0);
    });
    it('readSeenTutorials rejects arrays', () => {
        ls.setItem('homedir_tutorial_seen', JSON.stringify(['hub']));
        const result = ctx.module.exports.readSeenTutorials();
        assert.strictEqual(typeof result, 'object');
        assert.strictEqual(Object.keys(result).length, 0);
    });
    it('readSeenTutorials rejects non-boolean values', () => {
        ls.setItem('homedir_tutorial_seen', JSON.stringify({ hub: 'yes', other: 123 }));
        const result = ctx.module.exports.readSeenTutorials();
        assert.strictEqual(typeof result, 'object');
        assert.strictEqual(Object.keys(result).length, 0);
    });
    it('readSeenTutorials filters only true boolean values', () => {
        ls.setItem('homedir_tutorial_seen', JSON.stringify({ hub: true, 'hub-how': false, profile: true }));
        const result = ctx.module.exports.readSeenTutorials();
        assert.strictEqual(result.hub, true);
        assert.strictEqual(result.profile, true);
        assert.strictEqual(result['hub-how'], undefined);
    });
    it('markTutorialSeen writes to localStorage', () => {
        ctx.module.exports.markTutorialSeen('hub');
        assert.strictEqual(JSON.parse(ls.getItem('homedir_tutorial_seen')).hub, true);
    });
    it('markTutorialSeen preserves existing keys', () => {
        ls.setItem('homedir_tutorial_seen', JSON.stringify({ 'hub-how': true }));
        ctx.module.exports.markTutorialSeen('hub');
        const stored = JSON.parse(ls.getItem('homedir_tutorial_seen'));
        assert.strictEqual(stored.hub, true);
        assert.strictEqual(stored['hub-how'], true);
    });
    it('isTutorialSeen returns true for seen', () => {
        ls.setItem('homedir_tutorial_seen', JSON.stringify({ hub: true }));
        assert.strictEqual(ctx.module.exports.isTutorialSeen('hub'), true);
        assert.strictEqual(ctx.module.exports.isTutorialSeen('unknown'), false);
    });
    it('clearSeenTutorials removes localStorage key', () => {
        ls.setItem('homedir_tutorial_seen', JSON.stringify({ hub: true }));
        ctx.module.exports.clearSeenTutorials();
        assert.strictEqual(ls.getItem('homedir_tutorial_seen'), null);
    });
    it('hideSeenTutorials sets hidden attribute on seen cards', () => {
        ls.setItem('homedir_tutorial_seen', JSON.stringify({ hub: true }));
        const card1 = doc.createElement();
        card1.setAttribute('data-tutorial', 'hub');
        doc._elements.card1 = card1;
        const card2 = doc.createElement();
        card2.setAttribute('data-tutorial', 'profile');
        doc._elements.card2 = card2;
        ctx.module.exports.hideSeenTutorials();
        assert.ok(card1.attributes.hidden !== undefined);
        assert.ok(card2.attributes.hidden === undefined);
    });
    it('setupDismissButtons marks seen and hides on click', () => {
        ctx.module.exports.setupDismissButtons();
        ctx.module.exports.markTutorialSeen('hub');
        assert.strictEqual(JSON.parse(ls.getItem('homedir_tutorial_seen')).hub, true);
    });
    it('setupReplayButton clears localStorage and shows all cards', () => {
        ls.setItem('homedir_tutorial_seen', JSON.stringify({ hub: true }));
        ctx.module.exports.clearSeenTutorials();
        assert.strictEqual(ls.getItem('homedir_tutorial_seen'), null);
    });
    it('handles localStorage quota exceeded gracefully', () => {
        const orig = ls.setItem;
        ls.setItem = () => { throw new Error('QuotaExceededError'); };
        ctx.module.exports.markTutorialSeen('hub'); // no debe lanzar
        ls.setItem = orig;
    });
    it('handles localStorage disabled/blocked gracefully', () => {
        const { context: ctx2, localStorage: ls2 } = createContext();
        ls2.getItem = () => { throw new Error('SecurityError'); };
        ls2.setItem = () => { throw new Error('SecurityError'); };
        ls2.removeItem = () => { throw new Error('SecurityError'); };
        const result = ctx2.module.exports.readSeenTutorials();
        assert.strictEqual(typeof result, 'object');
        assert.strictEqual(Object.keys(result).length, 0);
        ctx2.module.exports.markTutorialSeen('hub');
        ctx2.module.exports.clearSeenTutorials();
    });
});