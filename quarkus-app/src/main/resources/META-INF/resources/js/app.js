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

function bannerParallax() {
    const banner = document.querySelector('.container-banner');
    if (banner) {
        banner.style.backgroundPositionX = (window.scrollY * 0.3) + 'px';
    }
}

let loadingTimeout;
let loadingTarget = 'el contenido';

function showLoading(target = 'el contenido') {
    loadingTarget = target;
    document.body.classList.remove('loaded');
    const loader = document.getElementById('loading');
    if (loader) loader.classList.remove('hidden');
    clearTimeout(loadingTimeout);
    loadingTimeout = setTimeout(() => {
        hideLoading();
        showNotification('error', `No se pudo cargar ${loadingTarget}`);
    }, 5000);
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
            showLoading('los datos');
            const btn = form.querySelector('button[type="submit"]');
            if (btn) btn.disabled = true;
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
}

window.addEventListener('DOMContentLoaded', () => {
    setupMenu();
    setupUserMenu();
    adjustLayout();
    bannerParallax();
    handleForms();
    highlightNav();
    handleNotificationsFromUrl();
    hideLoading();
    if (document.querySelector('.no-events')) {
        hideLoading();
        showNotification('info', 'No hay eventos disponibles');
    }
});
window.addEventListener('resize', adjustLayout);
window.addEventListener('scroll', bannerParallax);
window.addEventListener('beforeunload', () => showLoading('la página'));
