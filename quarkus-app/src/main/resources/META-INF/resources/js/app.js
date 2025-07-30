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
    const initialMsg = document.getElementById('git-initial-status');
    if (!container) return;
    try {
        const resp = await fetch('/private/api/git-status');
        if (!resp.ok) {
            container.textContent = 'Error al obtener estado';
            return;
        }
        const data = await resp.json();
        container.innerHTML = renderGitStatus(data);
        if (initialMsg && data.initialLoadAttempted !== undefined) {
            initialMsg.textContent = data.initialLoadSuccess
                ? 'Carga inicial desde Git realizada correctamente al iniciar la aplicación'
                : 'La carga inicial falló. Se requiere intervención.';
        }
        const btn = document.getElementById('git-error-btn');
        const details = document.getElementById('git-error-details');
        if (btn && details) {
            btn.addEventListener('click', () => {
                details.style.display = details.style.display === 'none' ? 'block' : 'none';
            });
        }
    } catch (e) {
        container.textContent = 'Error al obtener estado';
    }
}

function renderGitStatus(data) {
    const icon = data.success ? '✔️' : '❌';
    const color = data.success ? 'green' : 'red';
    let html = `<p style="color:${color}" role="status" aria-live="polite">${icon} `;
    if (data.success) {
        html += `Última carga exitosa desde Git: ${data.lastSuccess}`;
    } else {
        html += `Último intento fallido: ${data.lastAttempt}`;
        if (data.message) html += ` – Error: ${data.message}`;
    }
    html += '</p><ul>';
    if (data.repoUrl) html += `<li>Repo: ${data.repoUrl}</li>`;
    if (data.branch) html += `<li>Rama: ${data.branch}</li>`;
    if (data.filesRead !== undefined && data.eventsImported !== undefined) {
        html += `<li>${data.filesRead} archivos JSON analizados – ${data.eventsImported} eventos cargados</li>`;
    }
    html += '</ul>';
    if (!data.success && data.errorDetails) {
        html += `<button id="git-error-btn">Ver detalles del error</button>`;
        html += `<pre id="git-error-details" style="display:none">${escapeHtml(data.errorDetails)}</pre>`;
    }
    return html;
}

function escapeHtml(str) {
    const map = {"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"};
    return str.replace(/[&<>"']/g, c => map[c]);
}

async function reloadGit() {
    const btn = document.getElementById('git-reload-btn');
    const msg = document.getElementById('git-reload-msg');
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner"></span> Cargando...';
    }
    if (msg) {
        msg.textContent = '';
        msg.style.color = 'gray';
    }
    try {
        const resp = await fetch('/private/api/git-reload', {method: 'POST'});
        if (resp.ok) {
            const data = await resp.json();
            if (msg) {
                msg.textContent = data.success ? 'Carga exitosa' : 'Error: ' + data.message;
                msg.style.color = data.success ? 'green' : 'red';
            } else {
                alert(data.success ? 'Carga exitosa' : 'Error: ' + data.message);
            }
        } else {
            if (msg) {
                msg.textContent = 'Error al recargar';
                msg.style.color = 'red';
            } else {
                alert('Error al recargar');
            }
        }
    } catch (e) {
        if (msg) {
            msg.textContent = 'Error al recargar';
            msg.style.color = 'red';
        } else {
            alert('Error al recargar');
        }
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.textContent = 'Volver a cargar desde Git';
        }
        loadGitStatus();
    }
}
