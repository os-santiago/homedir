(function () {
  const root = document.querySelector('.notifications-center');
  if (!root) {
    return;
  }

  const i18n = {
    toggleMarkRead: root.dataset.i18nToggleMarkRead || 'Mark as read',
    toggleMarkUnread: root.dataset.i18nToggleMarkUnread || 'Mark as unread',
    linkViewTalk: root.dataset.i18nLinkViewTalk || 'View talk',
    linkOpen: root.dataset.i18nLinkOpen || 'Open',
    defaultTitle: root.dataset.i18nDefaultTitle || 'Notification',
    catEvent: root.dataset.i18nCatEvent || 'Event',
    catTalk: root.dataset.i18nCatTalk || 'Talk',
    catBreak: root.dataset.i18nCatBreak || 'Break',
    catAnnouncement: root.dataset.i18nCatAnnouncement || 'Announcement',
    selectAll: root.dataset.i18nSelectAll || 'Select all',
    deselectAll: root.dataset.i18nDeselectAll || 'Deselect all'
  };

  const LS_KEY = 'ef_global_notifs'; // array de notifs [{id, title, message, createdAt, readAt?, dismissedAt?, targetUrl?}]
  const UNREAD_KEY = 'ef_global_unread_count';
  const listEl  = document.getElementById('notif-list');
  const emptyEl = document.getElementById('empty');
  const markAllBtn = document.getElementById('markAllRead');
  const deleteBtn  = document.getElementById('deleteSelected');
  const selectAllBtn = document.getElementById('selectAll');
  const confirmDlg = document.getElementById('confirmDeleteAll');
  const actionsRight = document.querySelector('.actions-right');

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
    syncUnread(arr);
  }

  function syncUnread(arr) {
    const startOfDay = new Date();
    startOfDay.setHours(0, 0, 0, 0);
    const endOfDay = new Date(startOfDay);
    endOfDay.setDate(endOfDay.getDate() + 1);
    const unread = arr.filter(n => {
      const ts = n.createdAt || 0;
      return !n.dismissedAt && !n.readAt && ts >= startOfDay.getTime() && ts < endOfDay.getTime();
    }).length;
    localStorage.setItem(UNREAD_KEY, String(unread));
    document.dispatchEvent(new CustomEvent('ef:notifs:changed'));
  }

  function fmt(ts) {
    try { return new Date(ts || Date.now()).toLocaleString(); } catch { return ''; }
  }

  // Render de la lista aplicando filtro y preservando selección
  function render() {
    const all = getAll();
    syncUnread(all);
    let items = all.filter(n => !n.dismissedAt);

    const startOfDay = new Date();
    startOfDay.setHours(0, 0, 0, 0);
    const endOfDay = new Date(startOfDay);
    endOfDay.setDate(endOfDay.getDate() + 1);
    items = items.filter(n => {
      const ts = n.createdAt || 0;
      return ts >= startOfDay.getTime() && ts < endOfDay.getTime();
    });

    if (currentFilter === 'unread') items = items.filter(n => !n.readAt);

    items.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));

    listEl.innerHTML = '';
    if (items.length === 0) {
      emptyEl.classList.remove('hidden');
      actionsRight?.classList.add('hidden');
      updateSelectAllBtn();
      return;
    }
    emptyEl.classList.add('hidden');
    actionsRight?.classList.remove('hidden');

    for (const n of items) {
      const div = document.createElement('div');
      div.className = 'card notif';
      div.dataset.id = n.id;
      if (n.id) div.id = n.id;

      const checked = selected.has(n.id) ? 'checked' : '';
      const readLabel = n.readAt ? i18n.toggleMarkUnread : i18n.toggleMarkRead;
      const chip = chipFor(n);
      const url = n.targetUrl || (n.talkId ? `/talks/${encodeURIComponent(n.talkId)}` : '/notifications/center');
      const linkLabel = n.talkId ? i18n.linkViewTalk : i18n.linkOpen;

      div.innerHTML = `
        <div class="row items-start gap-3">
          <input type="checkbox" class="sel js-select" data-id="${n.id}" ${checked}>
          <div class="grow">
            <div class="title">${escapeHtml(n.title || i18n.defaultTitle)}</div>
            <div class="msg">${escapeHtml(n.message || '')}</div>
            <div class="meta text-xs">${fmt(n.createdAt)} ${chip}</div>
          </div>
          <div class="col shrink">
            <button class="btn-link js-read" data-id="${n.id}">${readLabel}</button>
            <a class="btn-link js-open" data-id="${n.id}" href="${escapeAttr(url)}" rel="nofollow">${linkLabel}</a>
          </div>
        </div>`;
      listEl.appendChild(div);
    }

    updateSelectAllBtn();
  }

  function chipFor(n) {
    const cat = (n.category || 'announcement').toLowerCase();
    const label = cat === 'event'
      ? i18n.catEvent
      : cat === 'talk'
        ? i18n.catTalk
        : cat === 'break'
          ? i18n.catBreak
          : i18n.catAnnouncement;
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
    selectAllBtn.textContent = allChecked ? i18n.deselectAll : i18n.selectAll;
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
  if (window.updateUnreadFromLocal) {
    window.updateUnreadFromLocal();
  }
  const hash = window.location.hash.slice(1);
  if (hash) {
    const el = document.getElementById(hash);
    if (el) el.scrollIntoView();
  }
})();
