function adjustLayout() {
    if (window.innerWidth < 600) {
        document.body.classList.add('mobile');
    } else {
        document.body.classList.remove('mobile');
    }
}

function setupMenu() {
    const toggle = document.getElementById('menuToggle');
    const links = document.querySelector('.nav-links');
    if (toggle && links) {
        toggle.addEventListener('click', () => {
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

window.addEventListener('DOMContentLoaded', () => {
    setupMenu();
    adjustLayout();
    bannerParallax();
    loadGitStatus();
    const reloadBtn = document.getElementById('git-reload-btn');
    if (reloadBtn) {
        reloadBtn.addEventListener('click', reloadGit);
    }
});
window.addEventListener('resize', adjustLayout);
window.addEventListener('scroll', bannerParallax);

async function loadGitStatus() {
    const container = document.getElementById('git-status-container');
    if (!container) return;
    try {
        const resp = await fetch('/private/api/git-status');
        if (!resp.ok) {
            container.textContent = 'Error al obtener estado';
            return;
        }
        const data = await resp.json();
        container.innerHTML = renderGitStatus(data);
    } catch (e) {
        container.textContent = 'Error al obtener estado';
    }
}

function renderGitStatus(data) {
    const icon = data.success ? '✅' : '❌';
    const color = data.success ? 'green' : 'red';
    let html = `<p style="color:${color}">${icon} `;
    html += data.success ? 'Configuración cargada correctamente desde Git.' : 'Error al cargar configuración desde Git.';
    html += '</p><ul>';
    if (data.message && !data.success) {
        html += `<li>${data.message}</li>`;
    }
    if (data.repoUrl) html += `<li>Repo: ${data.repoUrl}</li>`;
    if (data.branch) html += `<li>Branch: ${data.branch}</li>`;
    if (data.filesRead !== undefined) html += `<li>Archivos JSON: ${data.filesRead}</li>`;
    if (data.eventsImported !== undefined) html += `<li>Eventos cargados: ${data.eventsImported}</li>`;
    if (data.lastSuccess) html += `<li>Última carga exitosa: ${data.lastSuccess}</li>`;
    html += '</ul>';
    return html;
}

async function reloadGit() {
    const btn = document.getElementById('git-reload-btn');
    if (btn) btn.disabled = true;
    try {
        const resp = await fetch('/private/api/git-reload', {method: 'POST'});
        if (resp.ok) {
            const data = await resp.json();
            alert(data.success ? 'Cargado exitosamente' : 'Error: ' + data.message);
        } else {
            alert('Error al recargar');
        }
    } catch (e) {
        alert('Error al recargar');
    } finally {
        if (btn) btn.disabled = false;
        loadGitStatus();
    }
}
