(function(){
  const reduceMotion=window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  function uid(){return (self.crypto&&crypto.randomUUID?crypto.randomUUID():Date.now().toString(36)+Math.random().toString(36).slice(2));}
  class ToastQueueManager{
    constructor(container){
      this.container=container;
      this.closeAllBtn=document.getElementById('ef-toast-close-all');
      this.maxVisible=parseInt(container.dataset.maxVisible||'3',10);
      this.autoDismissMs=parseInt(container.dataset.autoDismissMs||'6000',10);
      this.rateLimitWindow=30000;
      this.rateLimitMax=6;
      this.visible=[];
      this.queue=[];
      this.timestamps=[];
      this.closeAllBtn.addEventListener('click',()=>this.closeAll());
      this.reduceMotion=reduceMotion;
    }
    enqueue(vm){
      const now=Date.now();
      this.timestamps=this.timestamps.filter(t=>now-t<this.rateLimitWindow);
      if(this.timestamps.length>=this.rateLimitMax){this.metric('rate_limited');return;}
      if(this.visible.some(t=>t.id===vm.id)||this.queue.some(t=>t.id===vm.id)){this.metric('suppressed');return;}
      this.timestamps.push(now);
      if(this.visible.length<this.maxVisible){this.show(vm);}else{this.queue.push(vm);}
      this.updateCloseAll();
    }
    show(vm){
      vm.node=this.render(vm);
      this.visible.push(vm);
      this.metric('shown');
      requestAnimationFrame(()=>this.container.appendChild(vm.node));
      vm.timer=this.startTimer(vm);
    }
    startTimer(vm){
      let remaining=this.autoDismissMs;
      let start=Date.now();
      const tick=()=>this.close(vm.id);
      let timer=setTimeout(tick,remaining);
      const pause=()=>{clearTimeout(timer);remaining-=Date.now()-start;};
      const resume=()=>{start=Date.now();timer=setTimeout(tick,remaining);};
      vm.node.addEventListener('mouseenter',pause);
      vm.node.addEventListener('mouseleave',resume);
      vm.node.addEventListener('focusin',pause);
      vm.node.addEventListener('focusout',resume);
      return timer;
    }
    render(vm){
      const toast=document.createElement('div');
      toast.className='ef-toast';
      toast.setAttribute('data-id',vm.id);
      toast.setAttribute('tabindex','0');
      toast.setAttribute('role','status');
      toast.setAttribute('aria-live','polite');
      toast.addEventListener('keydown',e=>{if(e.key==='Escape'){this.close(vm.id);}});
      const title=document.createElement('div');
      title.className='ef-toast__title';
      title.textContent=vm.title;
      toast.appendChild(title);
      const msg=document.createElement('div');
      msg.className='ef-toast__message';
      msg.textContent=vm.message;
      toast.appendChild(msg);
      const actions=document.createElement('div');
      actions.className='ef-toast__actions';
      if(vm.centerUrl){
        const detail=document.createElement('a');
        detail.href=vm.centerUrl;
        detail.textContent='Ver detalle';
        actions.appendChild(detail);
      }
      if(vm.url){
        const link=document.createElement('a');
        link.href=vm.url;
        link.textContent='Ver charla';
        actions.appendChild(link);
      }
      const close=document.createElement('button');
      close.type='button';
      close.textContent='×';
      close.addEventListener('click',e=>{e.preventDefault();e.stopPropagation();this.close(vm.id);});
      actions.appendChild(close);
      toast.appendChild(actions);
      return toast;
    }
    close(id){
      const idx=this.visible.findIndex(t=>t.id===id);
      if(idx===-1)return;
      const vm=this.visible.splice(idx,1)[0];
      clearTimeout(vm.timer);
      if(this.reduceMotion){vm.node.remove();}
      else {
        vm.node.classList.add('hide');
        requestAnimationFrame(()=>vm.node.remove());
      }
      this.metric('dismissed');
      if(this.queue.length>0){this.show(this.queue.shift());}
      this.updateCloseAll();
    }
    closeAll(){
      this.visible.slice().forEach(t=>this.close(t.id));
      this.queue=[];
      this.metric('closed_all');
      this.updateCloseAll();
    }
    updateCloseAll(){
      const total=this.visible.length+this.queue.length;
      const hide = total===0;
      this.closeAllBtn.hidden = hide;
      this.closeAllBtn.classList.toggle('hidden', hide);
    }
    metric(name){
      if(window.__metrics&&window.__metrics.count){window.__metrics.count('ui.notifications.'+name);} }
  }
  const container=document.getElementById('ef-toast-container');
  const manager=container?new ToastQueueManager(container):null;
  function updateCloseAllVisibility(){if(manager)manager.updateCloseAll();}
  window.updateCloseAllVisibility=updateCloseAllVisibility;
  updateCloseAllVisibility();
  const badge=document.getElementById('notif-badge');
  const UNREAD_KEY='ef_global_unread_count';
  let unread=0;

  function renderBadge(){
    if(!badge)return;
    if(unread>0){
      badge.textContent=String(unread);
      badge.classList.remove('hidden');
    }else{
      badge.textContent='0';
      badge.classList.add('hidden');
    }
  }

  function updateUnreadFromLocal(){
    if(!badge)return;
    try{
      const raw=localStorage.getItem(UNREAD_KEY);
      unread=raw?parseInt(raw,10):0;
    }catch(_){unread=0;}
    renderBadge();
  }
  window.updateUnreadFromLocal=updateUnreadFromLocal;
  if(badge){
    window.addEventListener('DOMContentLoaded',updateUnreadFromLocal);
    document.addEventListener('ef:notifs:changed',updateUnreadFromLocal);
  }

  window.EventFlowNotifications={
    accept(dto){
      if(window.updateUnreadFromLocal)window.updateUnreadFromLocal();
      if(!manager)return;
      const id=dto.id||uid();
      const vm={
        id:id,
        title:dto.title||dto.type,
        message:dto.message||'',
        url:(dto.talkId && dto.category!=='break')?('/talks/'+dto.talkId):null,
        centerUrl:dto.id?('/notifications/center#'+dto.id):'/notifications/center'
      };
      manager.enqueue(vm);
    },
    closeAll(){manager&&manager.closeAll();}
  };
  window.__notifyDev__=(partial={})=>{
    const demo=Object.assign({type:'UPCOMING',title:'Demo',message:'Example',talkId:'demo'},partial);
    window.EventFlowNotifications.accept(demo);
  };
})();
