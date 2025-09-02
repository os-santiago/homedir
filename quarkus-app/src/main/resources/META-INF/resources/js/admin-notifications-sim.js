(function () {
  const form = document.getElementById('simForm');
  const previewBtn = document.getElementById('previewBtn');
  const emitBtn = document.getElementById('emitBtn');
  const seqBtn = document.getElementById('sequenceBtn');
  const resultsTable = document.getElementById('results');
  const optinToggle = document.getElementById('optinToggle');
  optinToggle.checked = localStorage.getItem('ef_notif_test_optin') === 'true';
  optinToggle.addEventListener('change', (e) => {
    localStorage.setItem('ef_notif_test_optin', e.target.checked ? 'true' : 'false');
  });

  function buildReq() {
    const states = Array.from(form.querySelectorAll('input[name="state"]:checked')).map(
      (c) => c.value
    );
    const pivotVal = document.getElementById('pivot').value;
    return {
      eventId: document.getElementById('eventId').value || null,
      pivot: pivotVal ? new Date(pivotVal).toISOString() : null,
      includeEvent: document.getElementById('incEvent').checked,
      includeTalks: document.getElementById('incTalks').checked,
      includeBreaks: document.getElementById('incBreaks').checked,
      states: states,
    };
  }

  function render(plan) {
    const tbody = resultsTable.querySelector('tbody');
    tbody.innerHTML = '';
    plan.forEach((n) => {
      const tr = document.createElement('tr');
      tr.innerHTML = `<td>${n.type || ''}</td><td>${n.category || ''}</td><td>${n.title || ''}</td><td>${n.message || ''}</td>`;
      tbody.appendChild(tr);
    });
    resultsTable.classList.toggle('hidden', plan.length === 0);
  }

  previewBtn.addEventListener('click', async () => {
    const req = buildReq();
    const res = await fetch('/admin/api/notifications/sim/dry-run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req),
    });
    if (res.ok) {
      render(await res.json());
    }
  });

  emitBtn.addEventListener('click', async () => {
    const req = buildReq();
    req.mode = 'test-broadcast';
    const res = await fetch('/admin/api/notifications/sim/execute', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req),
    });
    if (res.ok) {
      const out = await res.json();
      alert('Enqueued ' + out.enqueued + ' notificaciones de prueba');
    }
  });

  seqBtn.addEventListener('click', async () => {
    const req = buildReq();
    req.mode = 'test-broadcast';
    req.sequence = true;
    const res = await fetch('/admin/api/notifications/sim/execute', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req),
    });
    if (res.ok) {
      alert('Reproducción secuencial iniciada');
    }
  });
})();
