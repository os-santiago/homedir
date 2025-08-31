(async function(){
  const listEl = document.getElementById('admin-list');
  const clearBtn = document.getElementById('clearAll');
  async function load(){
    const res = await fetch('/admin/api/notifications/latest?limit=50', { cache:'no-store' });
    if(!res.ok) return;
    const items = await res.json();
    listEl.innerHTML = '';
    items.forEach(n=>{
      const row = document.createElement('div');
      row.className = 'card row justify-between items-center';
      row.innerHTML = `
        <div class="grow">
          <div class="font-medium">${n.title}</div>
          <div class="text-sm text-muted-foreground">${n.message}</div>
          <div class="text-xs">${new Date(n.createdAt).toLocaleString()} — ${n.type}${n.eventId? ' — '+n.eventId:''}</div>
        </div>
        <button class="btn-danger" data-id="${n.id}">Eliminar</button>`;
      listEl.appendChild(row);
    });
  }
  document.getElementById('broadcast').addEventListener('submit', async (e)=>{
    e.preventDefault();
    const fd = new FormData(e.target);
    const body = Object.fromEntries(fd.entries());
    const res = await fetch('/admin/api/notifications/broadcast',{ method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(body) });
    if(res.ok){ e.target.reset(); load(); }
  });
  listEl.addEventListener('click', async (e)=>{
    const id = e.target?.dataset?.id; if(!id) return;
    if(!confirm('¿Eliminar notificación del backlog?')) return;
    const res = await fetch(`/admin/api/notifications/${id}`, { method:'DELETE' });
    if(res.status===204) load();
  });
  if(clearBtn){
    clearBtn.addEventListener('click', async ()=>{
      if(!confirm('¿Borrar todo el backlog de notificaciones?')) return;
      const res = await fetch('/admin/api/notifications',{ method:'DELETE' });
      if(res.status===204) load();
    });
  }
  load();
  const demoBtn = document.getElementById('broadcast-demo');
  if(demoBtn){
    demoBtn.addEventListener('click', async ()=>{
      const now = Date.now();
      const payloads=[
        {type:'ANNOUNCEMENT',category:'announcement',title:'Demo 1',message:'Notificación de prueba 1',expiresAt:now+5*60*1000},
        {type:'ANNOUNCEMENT',category:'announcement',title:'Demo 2',message:'Notificación de prueba 2',expiresAt:now+5*60*1000}
      ];
      for(const body of payloads){
        await fetch('/admin/api/notifications/broadcast',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
      }
      load();
    });
  }
})();
