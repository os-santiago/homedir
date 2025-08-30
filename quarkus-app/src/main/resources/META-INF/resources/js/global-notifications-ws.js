(function () {
  const storeKey = 'ef_global_lastCursor';
  const inboxKey = 'ef_global_inbox';
  const unreadKey = 'ef_global_unread_count';
  function connect(delay) {
    const retry = delay || 1000;
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const ws = new WebSocket(proto + '://' + window.location.host + '/ws/global-notifications');
    ws.onopen = () => {
      const cursor = Number(localStorage.getItem(storeKey)) || 0;
      ws.send(JSON.stringify({ t: 'hello', cursor: cursor, cap: ['toast', 'center'] }));
    };
    ws.onmessage = (ev) => {
      const msg = JSON.parse(ev.data);
      if (msg.t === 'notif') {
        const last = Number(localStorage.getItem(storeKey)) || 0;
        if (msg.createdAt && msg.createdAt > last) {
          localStorage.setItem(storeKey, String(msg.createdAt));
        }
        try {
          const arr = JSON.parse(localStorage.getItem(inboxKey) || '[]');
          arr.unshift(msg);
          localStorage.setItem(inboxKey, JSON.stringify(arr.slice(0, 100)));
          const unread = parseInt(localStorage.getItem(unreadKey) || '0', 10) + 1;
          localStorage.setItem(unreadKey, String(unread));
        } catch (e) {}
        if (window.EventFlowNotifications && window.EventFlowNotifications.accept) {
          window.EventFlowNotifications.accept(msg);
        }
        if (window.updateUnreadFromLocal) {
          window.updateUnreadFromLocal();
        }
      }
    };
    ws.onclose = () => {
      const next = Math.min(retry * 2, 30000);
      setTimeout(() => connect(next), retry + Math.random() * 1000);
    };
    ws.onerror = () => ws.close();
  }
  if ('WebSocket' in window) {
    connect(1000);
  } else {
    console.info('WebSocket not supported');
  }
})();
