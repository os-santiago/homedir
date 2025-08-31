(function(){
  const origFetch = window.fetch;
  const isCriticalApi = (url) => typeof url === 'string' && url.startsWith('/api/private/');
  window.fetch = async function(input, init = {}) {
    if (!init.credentials) { init.credentials = 'include'; }
    init.headers = new Headers(init.headers || {});
    if (!init.headers.has('Authorization')) {
      const token = sessionStorage.getItem('token') || localStorage.getItem('token');
      if (token) { init.headers.set('Authorization', `Bearer ${token}`); }
    }
    const res = await origFetch(input, init);
    const url = typeof input === 'string' ? input : (input && input.url) || '';
    if (res.status === 401) {
      if (isCriticalApi(url) && res.headers.get('X-Session-Expired') === 'true') {
        try { sessionStorage.clear(); localStorage.clear(); } catch (e) {}
        if (location.pathname !== '/') { location.assign('/?session=expired'); }
      } else {
        console.debug('non-critical 401', url);
      }
    }
    return res;
  };
})();

/**
 * Mapa de selectores usados por la capa de UI.
 * Cada entrada apunta a un atributo `data-*` presente en el HTML, lo que
 * desacopla la lógica de JavaScript de los ids o clases concretos.
 * Modifique estos valores si cambia la estructura del marcado para mantener
 * el contrato entre HTML y JavaScript.
 */
const SELECTORS = {
    /** Botón para desplegar el menú principal en móvil */
    menuToggle: '[data-menu-toggle]',
    /** Contenedor de enlaces de navegación principal */
    primaryNav: '[data-primary-navigation]',
    /** Contenedor del menú del usuario */
    userMenu: '[data-user-menu]',
    /** Botón que abre/cierra el menú del usuario */
    userMenuBtn: '[data-user-menu-btn]',
    /** Botón para vista secuencial de agenda */
    agendaSeqBtn: '[data-agenda-seq-btn]',
    /** Botón para vista en cuadrícula de agenda */
    agendaGridBtn: '[data-agenda-grid-btn]',
    /** Contenedores de la vista secuencial de agenda */
    agendaSeqViews: '[data-agenda-seq]',
    /** Contenedores de la vista en cuadrícula de agenda */
    agendaGridViews: '[data-agenda-grid]',
    /** Banner superior con efecto parallax */
    banner: '[data-banner]',
    /** Elemento indicador de carga */
    loading: '[data-loading]',
    /** Área de notificaciones */
    notification: '[data-notification]',
    /** Enlaces de navegación */
    navLinks: '[data-nav-link]',
    /** Indicador cuando no hay eventos */
    noEvents: '[data-no-events]',
    /** Formularios generales de la página */
    forms: 'form'
};

// Funciones de ayuda para seleccionar elementos utilizando los selectores centralizados.
const $ = (key) => document.querySelector(SELECTORS[key]);
const $$ = (key) => document.querySelectorAll(SELECTORS[key]);

function adjustLayout() {
    const toggle = $('menuToggle');
    const links = $('primaryNav');
    if (window.innerWidth < 600) {
        document.body.classList.add('mobile');
        if (links && !links.classList.contains('active')) {
            links.setAttribute('hidden', '');
            if (toggle) toggle.setAttribute('aria-expanded', 'false');
        }
    } else {
        document.body.classList.remove('mobile');
        if (links) {
            links.classList.remove('active');
            links.removeAttribute('hidden');
        }
        if (toggle) toggle.setAttribute('aria-expanded', 'false');
    }
}

function setupMenu() {
    const toggle = $('menuToggle');
    const links = $('primaryNav');
    if (toggle && links) {
        toggle.addEventListener('click', () => {
            const expanded = toggle.getAttribute('aria-expanded') === 'true';
            toggle.setAttribute('aria-expanded', String(!expanded));
            links.classList.toggle('active');
            if (expanded) {
                links.setAttribute('hidden', '');
            } else {
                links.removeAttribute('hidden');
            }
        });
    }
}

function setupUserMenu() {
    const menu = $('userMenu');
    const btn = $('userMenuBtn');
    if (menu && btn) {
        btn.addEventListener('click', () => {
            const expanded = btn.getAttribute('aria-expanded') === 'true';
            btn.setAttribute('aria-expanded', String(!expanded));
            menu.classList.toggle('open');
            const dropdown = menu.querySelector('.dropdown');
            if (dropdown) {
                if (expanded) {
                    dropdown.setAttribute('hidden', '');
                } else {
                    dropdown.removeAttribute('hidden');
                }
            }
        });
        document.addEventListener('click', (e) => {
            if (!menu.contains(e.target)) {
                btn.setAttribute('aria-expanded', 'false');
                menu.classList.remove('open');
                const dropdown = menu.querySelector('.dropdown');
                if (dropdown) dropdown.setAttribute('hidden', '');
            }
        });
    }
}

function setupAgendaToggle() {
    const seqBtn = $('agendaSeqBtn');
    const gridBtn = $('agendaGridBtn');
    const seqViews = $$('agendaSeqViews');
    const gridViews = $$('agendaGridViews');
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

function setupViewFullAgenda() {
    const btn = document.getElementById('view-full-agenda');
    if (btn) {
        btn.addEventListener('click', (e) => {
            const panel = document.querySelector('#agenda');
            if (panel) {
                panel.classList.remove('collapsed');
                panel.scrollIntoView({ behavior: 'smooth' });
            }
        });
    }
}

function bannerParallax() {
    const banner = $('banner');
    if (banner) {
        banner.style.backgroundPositionX = (window.scrollY * 0.3) + 'px';
    }
}

let loadingTimeout;
let loadingTarget = 'el contenido';

function showLoading(target = 'el contenido', enableTimeout = true) {
    loadingTarget = target;
    document.body.classList.remove('loaded');
    const loader = $('loading');
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
    const loader = $('loading');
    if (loader) loader.classList.add('hidden');
    document.body.classList.add('loaded');
    clearTimeout(loadingTimeout);
}

function showNotification(type, message) {
    const note = $('notification');
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
    $$('forms').forEach(form => {
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
    const links = $$('navLinks');
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
    if (params.get('session') === 'expired') {
        showNotification('error', 'Sesión expirada');
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
    setupViewFullAgenda();
    adjustLayout();
    bannerParallax();
    handleForms();
    highlightNav();
    handleNotificationsFromUrl();
    restoreScroll();
    hideLoading();
    if ($('noEvents')) {
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

if (typeof window !== 'undefined') {
    window.initListeners = initListeners;
    window.removeListeners = removeListeners;
    initListeners();
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = { initListeners, removeListeners };
}
