(function(){
  const inboxKey='ef_global_inbox';
  const unreadKey='ef_global_unread_count';
  const list=document.getElementById('global-notifications-list');
  const items=JSON.parse(localStorage.getItem(inboxKey)||'[]');
  function save(){localStorage.setItem(inboxKey,JSON.stringify(items));}
  function dec(){const c=Math.max(0,parseInt(localStorage.getItem(unreadKey)||'0',10)-1);localStorage.setItem(unreadKey,String(c));if(window.updateUnreadFromLocal)window.updateUnreadFromLocal();}
  function render(){
    list.innerHTML='';
    items.forEach((n,i)=>{
      const li=document.createElement('li');
      li.className='notification-item';
      li.textContent=n.title+': '+n.message+' ';
      const btn=document.createElement('button');
      btn.textContent='✔';
      btn.addEventListener('click',()=>{items.splice(i,1);save();dec();render();});
      li.appendChild(btn);
      list.appendChild(li);
    });
  }
  render();
})();
