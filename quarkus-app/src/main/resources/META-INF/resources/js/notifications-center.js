(function(){
  const LS_KEY = 'ef_global_notifs';
  const UNREAD_KEY = 'ef_global_unread_count';
  const listEl = document.getElementById('notif-list');
  const emptyEl = document.getElementById('empty');

  function getAll(){ try { return JSON.parse(localStorage.getItem(LS_KEY) || '[]'); } catch { return []; } }
  function saveAll(arr){ localStorage.setItem(LS_KEY, JSON.stringify(arr.slice(-1000))); syncUnread(arr); }
  function syncUnread(arr){ const unread = arr.filter(n=>!n.readAt && !n.dismissedAt).length; localStorage.setItem(UNREAD_KEY, String(unread)); if(window.updateUnreadFromLocal) window.updateUnreadFromLocal(); }
  function render(filter='all'){
    const all = getAll();
    syncUnread(all);
    const now = Date.now();
    let items = all.filter(n => !n.dismissedAt);
    if(filter==='unread') items = items.filter(n => !n.readAt);
    if(filter==='last24h') items = items.filter(n => (now - (n.createdAt||0)) <= 24*3600*1000);
    listEl.innerHTML = '';
    if(items.length===0){ emptyEl.classList.remove('hidden'); return; }
    emptyEl.classList.add('hidden');
    items.sort((a,b)=>(b.createdAt||0)-(a.createdAt||0)).forEach(n=>{
      const div = document.createElement('div');
      div.className = 'card notif';
      const label = n.category==='event' ? 'Evento' : n.category==='break' ? 'Break' : n.category==='talk' ? 'Charla' : null;
      div.innerHTML = `
        <label class="row">
          <input type="checkbox" class="sel" data-id="${n.id}">
          <div class="col grow">
            <div class="title">${(n.title||'Aviso')}</div>
            <div class="msg">${(n.message||'')}</div>
            <div class="meta">${label?`<span class="chip">${label}</span>`:''} ${new Date(n.createdAt||Date.now()).toLocaleString()}</div>
          </div>
          <div class="col">
            <button class="btn-link" data-act="read" data-id="${n.id}">${n.readAt?'Leída':'Marcar leída'}</button>
            <button class="btn-link" data-act="open" data-id="${n.id}">Revisar</button>
          </div>
        </label>`;
      listEl.appendChild(div);
    });
  }

  document.addEventListener('click', (e)=>{
    const act = e.target?.dataset?.act;
    if(act==='read'){
      const id = e.target.dataset.id;
      const all = getAll();
      const n = all.find(x=>x.id===id); if(n){ n.readAt = Date.now(); saveAll(all); render(currentFilter); }
    }
    if(e.target.id==='markAllRead'){
      const all = getAll().map(n=> (n.dismissedAt? n : (n.readAt? n : {...n, readAt: Date.now()})));
      saveAll(all); render(currentFilter);
    }
    if(e.target.id==='deleteSelected'){
      const ids = [...document.querySelectorAll('.sel:checked')].map(x=>x.dataset.id);
      if(ids.length===0) return;
      const all = getAll().map(n => ids.includes(n.id) ? {...n, dismissedAt: Date.now()} : n);
      saveAll(all); render(currentFilter);
    }
    if(act==='open'){
      // optional: navigate to event if eventId present
      // location.href = `/events/${e.target.dataset.eventId || ''}`;
    }
    if(e.target.matches('[data-filter]')) { currentFilter = e.target.dataset.filter; render(currentFilter); }
  });

  let currentFilter = 'all';
  render();

  // Hook from WS global to insert new notifications into localStorage
  window.__EF_GLOBAL_NOTIF_ACCEPT__ = function(dto){
    const all = getAll();
    if(!all.some(x=>x.id===dto.id)){
      all.push(dto);
      saveAll(all);
    }
    render(currentFilter);
  };
})();
