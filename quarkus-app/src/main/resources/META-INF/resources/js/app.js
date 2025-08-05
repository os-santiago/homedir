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

function bannerParallax() {
    const banner = document.querySelector('.container-banner');
    if (banner) {
        banner.style.backgroundPositionX = (window.scrollY * 0.3) + 'px';
    }
}

function showLoading() {
    document.body.classList.remove('loaded');
    const loader = document.getElementById('loading');
    if (loader) loader.classList.remove('hidden');
}

function hideLoading() {
    const loader = document.getElementById('loading');
    if (loader) loader.classList.add('hidden');
}

function showNotification(type, message) {
    const note = document.getElementById('notification');
    if (!note) return;
    note.textContent = message;
    note.className = 'notification ' + type;
    note.classList.add('show');
    setTimeout(() => {
        note.classList.remove('show');
        setTimeout(() => {
            note.className = 'notification hidden';
            note.textContent = '';
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
            showLoading();
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
    adjustLayout();
    bannerParallax();
    handleForms();
    highlightNav();
    handleNotificationsFromUrl();
    document.body.classList.add('loaded');
});
window.addEventListener('resize', adjustLayout);
window.addEventListener('scroll', bannerParallax);
window.addEventListener('beforeunload', showLoading);
