document.addEventListener('DOMContentLoaded', () => {
  const eventId = document.getElementById('eventId');
  const pivot = document.getElementById('pivot');
  const states = document.getElementById('states');
  const resultsTable = document.getElementById('results');
  const resultsBody = resultsTable.querySelector('tbody');
  const optin = document.getElementById('optin');
  // Shared escaping lives in utils.js (window.HomeDirUtils), loaded first in
  // the layout <head> before any deferred page script. No local fallback.

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
      tr.innerHTML = `<td class="border px-2 py-1">${window.HomeDirUtils.escapeHtml(row.recipient)}</td><td class="border px-2 py-1">${window.HomeDirUtils.escapeHtml(row.message)}</td>`;
      resultsBody.appendChild(tr);
    });
    resultsTable.classList.remove('hidden');
  }

  document.getElementById('preview').addEventListener('click', () => call('/admin/api/notifications/sim/dry-run'));
  document.getElementById('execute').addEventListener('click', () => call('/admin/api/notifications/sim/execute'));
  document.getElementById('replay').addEventListener('click', () => call('/admin/api/notifications/sim/execute?mode=sequential'));
});
