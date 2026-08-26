(function() {
    'use strict';

    var LS = window.localStorage;
    var LS_KEY = 'homedir_tutorial_seen';

    function getSeen() {
        try {
            var raw = LS.getItem(LS_KEY);
            return raw ? JSON.parse(raw) : {};
        } catch (e) {
            return {};
        }
    }

    function markSeen(id) {
        var seen = getSeen();
        seen[id] = true;
        try {
            LS.setItem(LS_KEY, JSON.stringify(seen));
        } catch (e) { /* quota exceeded */ }
    }

    function clearSeen() {
        try {
            LS.removeItem(LS_KEY);
        } catch (e) { /* ignore */ }
    }

    function hideSeenTutorials() {
        var cards = document.querySelectorAll('[data-tutorial]');
        for (var i = 0; i < cards.length; i++) {
            var id = cards[i].getAttribute('data-tutorial');
            if (getSeen()[id]) {
                cards[i].setAttribute('hidden', '');
            }
        }
    }

    function setupDismissButtons() {
        var btns = document.querySelectorAll('[data-tutorial-dismiss]');
        for (var i = 0; i < btns.length; i++) {
            btns[i].addEventListener('click', function ()  {
                var id = this.getAttribute('data-tutorial-dismiss');
                markSeen(id);
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
            clearSeen();
            var cards = document.querySelectorAll('[data-tutorial]');
            for (var i = 0; i < cards.length; i++) {
                cards[i].removeAttribute('hidden');
            }
        });
    }

    function init() {
        hideSeenTutorials();
        setupDismissButtons();
        setupReplayButton();
    }

    document.addEventListener('DOMContentLoaded', init);
})();