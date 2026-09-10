(function() {
    'use strict';

    var LS;
    try {
        LS = window.localStorage;
    } catch (e) {
        // Storage can be blocked by browser privacy settings or third-party context.
        LS = null;
    }
    var LS_KEY = 'homedir_tutorial_seen';

    function readSeenTutorials() {
        try {
            var raw = LS.getItem(LS_KEY);
            if (!raw) return {};
            var parsed = JSON.parse(raw);
            if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
                return Object.fromEntries(
                    Object.entries(parsed).filter(function (_ref) {
                        var v =_ref[1];
                        return v === true;
                    })
                );
            }
            return {};
        } catch (e) {
            console.warn('[tutorial] localStorage read failed:', e);
            return {};
        }
    }

    function markTutorialSeen(id) {
        var seen = readSeenTutorials();
        seen[id] = true;
        try {
            LS.setItem(LS_KEY, JSON.stringify(seen));
        } catch (e) { /* quota exceeded */ }
    }

    function clearSeenTutorials() {
        try {
            LS.removeItem(LS_KEY);
        } catch (e) { /* ignore */ }
    }

    function isTutorialSeen(id) {
        return readSeenTutorials()[id] === true;
    }

    function hideSeenTutorials() {
        var seen = readSeenTutorials();
        var cards = document.querySelectorAll('[data-tutorial]');
        for (var i = 0; i < cards.length; i++) {
            var id = cards[i].getAttribute('data-tutorial');
            if (seen[id]) {
                cards[i].setAttribute('hidden', '');
            }
        }
    }

    function setupDismissButtons() {
        var btns = document.querySelectorAll('[data-tutorial-dismiss]');
        for (var i = 0; i < btns.length; i++) {
            btns[i].addEventListener('click', function ()  {
                var id = this.getAttribute('data-tutorial-dismiss');
                markTutorialSeen(id);
                var cards = document.querySelectorAll('[data-tutorial="' + id + '"]');
                for (var j = 0; j < cards.length; j++) {
                    cards[j].setAttribute('hidden', '');
                }
            });
        }
    }

    function setupReplayButton() {
        var btn = document.querySelector('[data-replay-tutorials]');
        if (!btn) return;
        btn.addEventListener('click', function () {
            clearSeenTutorials();
            var cards = document.querySelectorAll('[data-tutorial]');
            for (var i = 0; i < cards.length; i++) {
                cards[i].removeAttribute('hidden');
            }
        });
    }

    function init() {
        hideSeenTutorials();
        // Cards render hidden by default (template) to avoid a flash of already-seen
        // content; reveal the ones this user has not dismissed yet.
        var cards = document.querySelectorAll('[data-tutorial]');
        var seen = readSeenTutorials();
        for (var i = 0; i < cards.length; i++) {
            var id = cards[i].getAttribute('data-tutorial');
            if (!seen[id]) {
                cards[i].removeAttribute('hidden');
            }
        }
        setupDismissButtons();
        setupReplayButton();
    }

    document.addEventListener('DOMContentLoaded', init);

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = {
            readSeenTutorials: readSeenTutorials,
            markTutorialSeen: markTutorialSeen,
            clearSeenTutorials: clearSeenTutorials,
            isTutorialSeen: isTutorialSeen,
            hideSeenTutorials: hideSeenTutorials,
            setupDismissButtons: setupDismissButtons,
            setupReplayButton: setupReplayButton,
            _LS_KEY: LS_KEY,
            _resetLS: function () { try { LS.removeItem(LS_KEY); } catch (_) {} }
        };
    }
})();