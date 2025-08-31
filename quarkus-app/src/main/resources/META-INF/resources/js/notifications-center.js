(function () {
  const LS_KEY = 'ef_global_notifs'; // array de notifs [{id, title, message, createdAt, readAt?, dismissedAt?, targetUrl?}]
  const listEl  = document.getElementById('notif-list');
  const emptyEl = document.getElementById('empty');
  const markAllBtn = document.getElementById('markAllRead');
  const deleteBtn  = document.getElementById('deleteSelected');
  const selectAllBtn = document.getElementById('selectAll');
  const confirmDlg = document.getElementById('confirmDeleteAll');

  // Estado de selección en memoria (se preserva entre renders)
  const selected = new Set();
  let currentFilter = 'all';

  // Utilidades de almacenamiento
  function getAll() {
    try { return JSON.parse(localStorage.getItem(LS_KEY) || '[]'); }
    catch { return []; }
  }
  function saveAll(arr) {
    // recorta a las últimas 1000 por seguridad
    localStorage.setItem(LS_KEY, JSON.stringify(arr.slice(-1000)));
    // opcional: actualizar badge global si existe
    if (window.EFNotificationsAdapter?.getUnreadCount) {
      // no bloqueante; el badge se recalcula en otro flujo
      document.dispatchEvent(new CustomEvent('ef:notifs:changed'));
    }
  }

  function fmt(ts) {
    try { return new Date(ts || Date.now()).toLocaleString(); } catch { return ''; }
  }

  // Render de la lista aplicando filtro y preservando selección
  function render() {
    const now = Date.now();
    const all = getAll();
    let items = all.filter(n => !n.dismissedAt);

    if (currentFilter === 'unread')  items = items.filter(n => !n.readAt);
    if (currentFilter === 'last24h') items = items.filter(n => (now - (n.createdAt || 0)) <= 24 * 3600 * 1000);

    items.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));

    listEl.innerHTML = '';
    if (items.length === 0) {
      emptyEl.classList.remove('hidden');
      return;
    }
    emptyEl.classList.add('hidden');

    for (const n of items) {
      const div = document.createElement('div');
      div.className = 'card notif';
      div.dataset.id = n.id;

      const checked = selected.has(n.id) ? 'checked' : '';
      const readLabel = n.readAt ? 'No leída' : 'Leída';
      const chip = chipFor(n);
      const url = n.targetUrl || (n.talkId ? `/talks/${encodeURIComponent(n.talkId)}` : '/notifications/center');

      div.innerHTML = `
        <div class="row items-start gap-3">
          <input type="checkbox" class="sel js-select" data-id="${n.id}" ${checked}>
          <div class="grow">
            <div class="title">${escapeHtml(n.title || 'Aviso')}</div>
            <div class="msg">${escapeHtml(n.message || '')}</div>
            <div class="meta text-xs">${fmt(n.createdAt)} ${chip}</div>
          </div>
          <div class="col shrink">
            <button class="btn-link js-read" data-id="${n.id}">${readLabel}</button>
            <a class="btn-link js-open" data-id="${n.id}" href="${escapeAttr(url)}" rel="nofollow">Revisar</a>
          </div>
        </div>`;
      listEl.appendChild(div);
    }

    updateSelectAllBtn();
  }

  function chipFor(n) {
    const cat = (n.category || '').toLowerCase();
    const label = cat === 'event' ? 'Evento' : cat === 'talk' ? 'Charla' : cat === 'break' ? 'Break' : 'Aviso';
    return `<span class="chip chip-${cat}">${label}</span>`;
  }

  // Escapes básicos
  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, m => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      "\"": "&quot;",
      "'": "&#039;"
    }[m]));
  }
  function escapeAttr(s) {
    return String(s).replace(/"/g, '&quot;');
  }

  function updateSelectAllBtn() {
    if (!selectAllBtn) return;
    const boxes = listEl.querySelectorAll('.js-select');
    const allChecked = boxes.length > 0 && Array.from(boxes).every(cb => cb.checked);
    selectAllBtn.textContent = allChecked ? 'Deseleccionar todos' : 'Seleccionar todos';
  }

  // Delegación robusta (usa closest)
  document.addEventListener('click', (e) => {
    // Filtros
    const filterBtn = e.target.closest('[data-filter]');
    if (filterBtn) {
      currentFilter = filterBtn.getAttribute('data-filter');
      render();
      return;
    }

    // Marcar leída / no leída (por item)
    const readBtn = e.target.closest('.js-read');
    if (readBtn) {
      const id = readBtn.getAttribute('data-id');
      const all = getAll();
      const n = all.find(x => x.id === id);
      if (n && !n.dismissedAt) {
        if (n.readAt) delete n.readAt;
        else n.readAt = Date.now();
        saveAll(all);
        render();
      }
      e.preventDefault();
      return;
    }

    // Abrir (enlace ya navega), no interceptar si hay href
    const openA = e.target.closest('.js-open');
    if (openA) return;

    // Eliminar seleccionadas
    if (e.target.closest('#deleteSelected')) {
      // Recalcular selección real desde el DOM para evitar desync
      const checked = document.querySelectorAll('.js-select:checked');
      if (checked.length === 0) return;
      selected.clear();
      for (const cb of checked) {
        const id = cb.getAttribute('data-id');
        if (id) selected.add(id);
      }
      const all = getAll();
      const now = Date.now();
      for (const n of all) {
        if (selected.has(String(n.id)) && !n.dismissedAt) n.dismissedAt = now;
      }
      saveAll(all);
      selected.clear();
      render();
      updateSelectAllBtn();
      e.preventDefault();
      return;
    }

    // Marcar TODAS como leídas (solo las no eliminadas)
    if (e.target.closest('#markAllRead')) {
      const all = getAll();
      const now = Date.now();
      for (const n of all) {
        if (!n.dismissedAt && !n.readAt) n.readAt = now;
      }
      saveAll(all);
      render();
      updateSelectAllBtn();
      e.preventDefault();
      return;
    }

    // Seleccionar / deseleccionar todos
    if (e.target.closest('#selectAll')) {
      const boxes = listEl.querySelectorAll('.js-select');
      const allChecked = boxes.length > 0 && Array.from(boxes).every(cb => cb.checked);
      if (allChecked) {
        boxes.forEach(cb => { cb.checked = false; selected.delete(cb.getAttribute('data-id')); });
      } else {
        boxes.forEach(cb => { cb.checked = true; selected.add(cb.getAttribute('data-id')); });
      }
      updateSelectAllBtn();
      e.preventDefault();
      return;
    }

    // Borrar todas con confirmación
    if (e.target.closest('#deleteAll')) {
      if (confirmDlg) confirmDlg.showModal();
      e.preventDefault();
      return;
    }

    if (e.target.closest('#confirmDeleteAllBtn')) {
      const all = getAll();
      const now = Date.now();
      for (const n of all) {
        if (!n.dismissedAt) n.dismissedAt = now;
      }
      saveAll(all);
      selected.clear();
      render();
      updateSelectAllBtn();
      if (confirmDlg) confirmDlg.close();
      e.preventDefault();
      return;
    }

    if (e.target.closest('#cancelDeleteAllBtn')) {
      if (confirmDlg) confirmDlg.close();
      e.preventDefault();
      return;
    }
  });

  // Cambios en checkbox (usar 'change' en vez de 'click')
  document.addEventListener('change', (e) => {
    const sel = e.target.closest('.js-select');
    if (!sel) return;
    const id = sel.getAttribute('data-id');
    if (!id) return;
    if (sel.checked) selected.add(id);
    else selected.delete(id);
    updateSelectAllBtn();
  });

  // Hook que invoca el WS global al recibir una notif
  window.__EF_GLOBAL_NOTIF_ACCEPT__ = function (dto) {
    const all = getAll();
    // dedupe por id
    if (!all.some(x => x.id === dto.id)) {
      all.push(dto);
      saveAll(all);
      render();
    }
  };

  // Inicial
  render();
})();
