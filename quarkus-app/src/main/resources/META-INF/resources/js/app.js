(function(){
  const origFetch = window.fetch;
  window.fetch = async function(input, init) {
    const res = await origFetch(input, init);
    if (res.status === 401 && res.headers.get('X-Session-Expired') === 'true') {
      try { sessionStorage.clear(); localStorage.clear(); } catch (e) {}
      if (location.pathname !== '/') { location.assign('/'); }
    }
    return res;
  };
})();

function adjustLayout() {
    if (window.innerWidth < 600) {
        document.body.classList.add('mobile');
    } else {
        document.body.classList.remove('mobile');
    }
}

function setupMenu() {
    const toggle = document.getElementById('menuToggle');
    const links = document.getElementById('primary-navigation');
    if (toggle && links) {
        toggle.addEventListener('click', () => {
            const expanded = toggle.getAttribute('aria-expanded') === 'true';
            toggle.setAttribute('aria-expanded', String(!expanded));
            links.classList.toggle('active');
        });
    }
}

function setupUserMenu() {
    const menu = document.querySelector('.user-menu');
    const btn = document.getElementById('userMenuBtn');
    if (menu && btn) {
        btn.addEventListener('click', () => {
            const expanded = btn.getAttribute('aria-expanded') === 'true';
            btn.setAttribute('aria-expanded', String(!expanded));
            menu.classList.toggle('open');
        });
        document.addEventListener('click', (e) => {
            if (!menu.contains(e.target)) {
                btn.setAttribute('aria-expanded', 'false');
                menu.classList.remove('open');
            }
        });
    }
}

function setupAgendaToggle() {
    const seqBtn = document.getElementById('agendaSeqBtn');
    const gridBtn = document.getElementById('agendaGridBtn');
    const seqViews = document.querySelectorAll('.agenda-seq');
    const gridViews = document.querySelectorAll('.agenda-grid');
    if (seqBtn && gridBtn && seqViews.length && gridViews.length) {
        seqBtn.addEventListener('click', () => {
            seqBtn.classList.add('active');
            gridBtn.classList.remove('active');
            seqViews.forEach(v => v.classList.remove('hidden'));
            gridViews.forEach(v => v.classList.add('hidden'));
        });
        gridBtn.addEventListener('click', () => {
            gridBtn.classList.add('active');
            seqBtn.classList.remove('active');
            gridViews.forEach(v => v.classList.remove('hidden'));
            seqViews.forEach(v => v.classList.add('hidden'));
        });
    }
}

function bannerParallax() {
    const banner = document.querySelector('.container-banner');
    if (banner) {
        banner.style.backgroundPositionX = (window.scrollY * 0.3) + 'px';
    }
}

let loadingTimeout;
let loadingTarget = 'el contenido';

function showLoading(target = 'el contenido', enableTimeout = true) {
    loadingTarget = target;
    document.body.classList.remove('loaded');
    const loader = document.getElementById('loading');
    if (loader) loader.classList.remove('hidden');
    clearTimeout(loadingTimeout);
    if (enableTimeout) {
        loadingTimeout = setTimeout(() => {
            hideLoading();
            showNotification('error', `No se pudo cargar ${loadingTarget}`);
        }, 5000);
    }
}

function hideLoading() {
    const loader = document.getElementById('loading');
    if (loader) loader.classList.add('hidden');
    document.body.classList.add('loaded');
    clearTimeout(loadingTimeout);
}

function showNotification(type, message) {
    const note = document.getElementById('notification');
    if (!note) return;
    note.innerHTML = message;
    note.className = 'notification ' + type;
    note.classList.add('show');
    setTimeout(() => {
        note.classList.remove('show');
        setTimeout(() => {
            note.className = 'notification hidden';
            note.innerHTML = '';
        }, 300);
    }, 3000);
}

function handleForms() {
    document.querySelectorAll('form').forEach(form => {
        form.addEventListener('submit', e => {
            if (form.classList.contains('needs-confirm') && !confirm('¿Estás seguro?')) {
                e.preventDefault();
                return;
            }
            sessionStorage.setItem('scrollPos', String(window.scrollY));
            sessionStorage.setItem('scrollPath', window.location.pathname);
            showLoading('los datos');
            const btn = e.submitter || form.querySelector('button[type="submit"]');
            if (btn) {
                setTimeout(() => btn.disabled = true, 0);
            }
        });
    });
}

function highlightNav() {
    const links = document.querySelectorAll('.nav-links a');
    links.forEach(link => {
        if (link.getAttribute('href') === window.location.pathname) {
            link.classList.add('active');
        }
    });
}

function handleNotificationsFromUrl() {
    const params = new URLSearchParams(window.location.search);
    if (params.has('success')) {
        showNotification('success', params.get('success'));
    }
    if (params.has('error')) {
        showNotification('error', params.get('error'));
    }
    if (params.has('msg')) {
        const msg = params.get('msg');
        const type = msg.startsWith('✅') ? 'success' : 'error';
        showNotification(type, msg);
    }
}

function restoreScroll() {
    const path = sessionStorage.getItem('scrollPath');
    const pos = sessionStorage.getItem('scrollPos');
    if (path === window.location.pathname && pos !== null) {
        window.scrollTo(0, parseInt(pos, 10));
    }
    sessionStorage.removeItem('scrollPath');
    sessionStorage.removeItem('scrollPos');
}

function onDomContentLoaded() {
    setupMenu();
    setupUserMenu();
    setupAgendaToggle();
    adjustLayout();
    bannerParallax();
    handleForms();
    highlightNav();
    handleNotificationsFromUrl();
    restoreScroll();
    hideLoading();
    if (document.querySelector('.no-events')) {
        hideLoading();
        showNotification('info', 'No hay eventos disponibles');
    }
}

let domContentLoadedHandler;
let resizeHandler;
let scrollHandler;
let beforeUnloadHandler;
let unloadHandler;

function initListeners() {
    domContentLoadedHandler = onDomContentLoaded;
    window.addEventListener('DOMContentLoaded', domContentLoadedHandler);

    resizeHandler = adjustLayout;
    window.addEventListener('resize', resizeHandler);

    scrollHandler = bannerParallax;
    window.addEventListener('scroll', scrollHandler);

    beforeUnloadHandler = () => showLoading('la página', false);
    window.addEventListener('beforeunload', beforeUnloadHandler);

    unloadHandler = () => removeListeners();
    window.addEventListener('unload', unloadHandler);
}

function removeListeners() {
    if (domContentLoadedHandler) {
        window.removeEventListener('DOMContentLoaded', domContentLoadedHandler);
        domContentLoadedHandler = null;
    }
    if (resizeHandler) {
        window.removeEventListener('resize', resizeHandler);
        resizeHandler = null;
    }
    if (scrollHandler) {
        window.removeEventListener('scroll', scrollHandler);
        scrollHandler = null;
    }
    if (beforeUnloadHandler) {
        window.removeEventListener('beforeunload', beforeUnloadHandler);
        beforeUnloadHandler = null;
    }
    if (unloadHandler) {
        window.removeEventListener('unload', unloadHandler);
        unloadHandler = null;
    }
}

initListeners();

export { initListeners, removeListeners };
