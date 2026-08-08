(async function () {
  // Admin notifications module — serves both the broadcast page and the
  // simulator page. Each init function guards on its own root element and
  // no-ops when absent, so a single script tag on either page is safe.
  // notifications-center.js (user-facing) is intentionally separate: different
  // audience, different page, different data source (localStorage vs REST).
  function esc(s) {
    return window.HomeDirUtils.escapeHtml(s);
  }

  // --- Broadcast page (root: #admin-list) ---
  async function initBroadcast() {
    const listEl = document.getElementById('admin-list');
    const clearBtn = document.getElementById('clearAll');
    if (!listEl) {
      return;
    }
    async function load() {
      const res = await fetch('/admin/api/notifications/latest?limit=50', { cache: 'no-store' });
      if (!res.ok) return;
      const items = await res.json();
      listEl.textContent = '';
      items.forEach(n => {
        const row = document.createElement('div');
        row.className = 'card row justify-between items-center';
        const audienceInfo = n.audience ? ` — Audiencia: ${esc(n.audience)}` : ' — Global';
        row.innerHTML = `
        <div class="grow">
          <div class="font-medium">${esc(n.title)}</div>
          <div class="text-sm text-muted-foreground">${esc(n.message)}</div>
          <div class="text-xs">${new Date(n.createdAt).toLocaleString()} — ${esc(n.type)}${n.eventId ? ' — ' + esc(n.eventId) : ''}${audienceInfo}</div>
        </div>
        <button class="btn-danger" data-id="${esc(n.id)}">Eliminar</button>`;
        listEl.appendChild(row);
      });
    }
    async function updateAudienceEstimate() {
      const eventId = document.getElementById('eventId').value;
      const cfp = document.getElementById('audienceCfp').checked;
      const cfv = document.getElementById('audienceCfv').checked;
      const staff = document.getElementById('audienceStaff').checked;
      const estimateEl = document.getElementById('audienceEstimate');

      if (!cfp && !cfv && !staff) {
        estimateEl.textContent = 'Envío global a todos los usuarios conectados';
        return;
      }

      if (!eventId) {
        estimateEl.textContent = 'Selecciona un evento para ver la estimación';
        return;
      }

      const audience = [
        cfp ? 'cfp' : null,
        cfv ? 'cfv' : null,
        staff ? 'staff' : null
      ].filter(x => x).join(',');

      try {
        const res = await fetch(`/admin/api/notifications/audience-estimate?audience=${encodeURIComponent(audience)}&eventId=${encodeURIComponent(eventId)}`, { cache: 'no-store' });
        if (!res.ok) {
          estimateEl.textContent = 'Error al estimar audiencia';
          return;
        }
        const data = await res.json();
        const parts = [];
        if (cfp && data.cfpCount > 0) parts.push(`${data.cfpCount} speakers`);
        if (cfv && data.cfvCount > 0) parts.push(`${data.cfvCount} voluntarios`);
        if (staff && data.staffCount > 0) parts.push(`${data.staffCount} staff`);
        estimateEl.textContent = `Alcance estimado: ${data.total} usuarios únicos (${parts.join(', ')})`;
      } catch (e) {
        estimateEl.textContent = 'Error al estimar audiencia';
      }
    }

    document.getElementById('eventId').addEventListener('input', updateAudienceEstimate);
    document.getElementById('audienceCfp').addEventListener('change', updateAudienceEstimate);
    document.getElementById('audienceCfv').addEventListener('change', updateAudienceEstimate);
    document.getElementById('audienceStaff').addEventListener('change', updateAudienceEstimate);

    document.getElementById('broadcast').addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      const body = Object.fromEntries(fd.entries());
      const minutes = parseInt(body.expiresMinutes, 10);
      if (!isNaN(minutes)) { body.expiresAt = Date.now() + minutes * 60 * 1000; }
      delete body.expiresMinutes;

      // Build audience string from checkboxes
      const cfp = document.getElementById('audienceCfp').checked;
      const cfv = document.getElementById('audienceCfv').checked;
      const staff = document.getElementById('audienceStaff').checked;
      if (cfp || cfv || staff) {
        const audience = [
          cfp ? 'cfp' : null,
          cfv ? 'cfv' : null,
          staff ? 'staff' : null
        ].filter(x => x).join(',');
        body.audience = audience;
      }
      delete body.audienceCfp;
      delete body.audienceCfv;
      delete body.audienceStaff;

      const res = await fetch('/admin/api/notifications/broadcast', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
      if (res.ok) { e.target.reset(); updateAudienceEstimate(); load(); }
    });
    listEl.addEventListener('click', async (e) => {
      const id = e.target?.dataset?.id; if (!id) return;
      if (!confirm('¿Eliminar notificación del backlog?')) return;
      const res = await fetch(`/admin/api/notifications/${id}`, { method: 'DELETE' });
      if (res.status === 204) load();
    });
    if (clearBtn) {
      clearBtn.addEventListener('click', async () => {
        if (!confirm('¿Borrar todo el backlog de notificaciones?')) return;
        const res = await fetch('/admin/api/notifications', { method: 'DELETE' });
        if (res.status === 204) load();
      });
    }
    load();
    const demoBtn = document.getElementById('broadcast-demo');
    if (demoBtn) {
      demoBtn.addEventListener('click', async () => {
        const now = Date.now();
        const payloads = [
          { type: 'ANNOUNCEMENT', category: 'announcement', title: 'Demo 1', message: 'Notificación de prueba 1', expiresAt: now + 5 * 60 * 1000 },
          { type: 'ANNOUNCEMENT', category: 'announcement', title: 'Demo 2', message: 'Notificación de prueba 2', expiresAt: now + 5 * 60 * 1000 }
        ];
        for (const body of payloads) {
          await fetch('/admin/api/notifications/broadcast', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
        }
        load();
      });
    }
  }

  // --- Simulator page (root: #results + #optin) ---
  function initSimulator() {
    const eventId = document.getElementById('eventId');
    const pivot = document.getElementById('pivot');
    const states = document.getElementById('states');
    const resultsTable = document.getElementById('results');
    const optin = document.getElementById('optin');
    // Early-return when the simulator controls are not on the page (consistent
    // with other page scripts). This script is loaded with `defer`, so the DOM
    // is fully parsed when it runs — no DOMContentLoaded wrapper needed.
    if (!eventId || !pivot || !states || !resultsTable || !optin) {
      return;
    }
    const resultsBody = resultsTable.querySelector('tbody');

    // initialise opt-in state
    optin.checked = localStorage.getItem('ef_notif_test_optin') === '1';
    optin.addEventListener('change', () => {
      if (optin.checked) {
        localStorage.setItem('ef_notif_test_optin', '1');
      } else {
        localStorage.removeItem('ef_notif_test_optin');
      }
    });

    function collectParams() {
      return {
        eventId: eventId.value || undefined,
        pivot: pivot.value,
        states: Array.from(states.selectedOptions).map(o => o.value),
      };
    }

    async function call(url) {
      const resp = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(collectParams()),
      });
      const data = await resp.json();
      resultsBody.textContent = '';
      data.forEach(row => {
        const tr = document.createElement('tr');
        tr.innerHTML = `<td class="border px-2 py-1">${esc(row.recipient)}</td><td class="border px-2 py-1">${esc(row.message)}</td>`;
        resultsBody.appendChild(tr);
      });
      resultsTable.classList.remove('hidden');
    }

    document.getElementById('preview').addEventListener('click', () => call('/admin/api/notifications/sim/dry-run'));
    document.getElementById('execute').addEventListener('click', () => call('/admin/api/notifications/sim/execute'));
    document.getElementById('replay').addEventListener('click', () => call('/admin/api/notifications/sim/execute?mode=sequential'));
  }

  initBroadcast();
  initSimulator();
})();
