function adjustLayout() {
    if (window.innerWidth < 600) {
        document.body.classList.add('mobile');
    } else {
        document.body.classList.remove('mobile');
    }
}

function setupMenu() {
    const toggle = document.getElementById('menuToggle');
    const nav = document.querySelector('nav');
    if (toggle && nav) {
        toggle.addEventListener('click', () => {
            nav.classList.toggle('active');
        });
    }
}

window.addEventListener('DOMContentLoaded', () => {
    setupMenu();
    adjustLayout();
});
window.addEventListener('resize', adjustLayout);
