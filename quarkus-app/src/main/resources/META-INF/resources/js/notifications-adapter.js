// window.__EF_NOTIFICATIONS_MODE__ = 'global' | 'user'; default: 'global'
(function () {
  const MODE = window.__EF_NOTIFICATIONS_MODE__ || 'global';
  const LS = window.localStorage;
  const LS_UNREAD_KEY = 'ef_global_unread_count'; // derivado del estado local
  function getUnreadCountGlobal() {
    const raw = LS.getItem(LS_UNREAD_KEY);
    return raw ? parseInt(raw, 10) : 0;
  }
  async function getUnreadCountUser() {
    const res = await fetch('/api/notifications?filter=unread&limit=1', { cache: 'no-store' });
    if (!res.ok) throw new Error('unread fetch failed: ' + res.status);
    const data = await res.json();
    return data.unreadCount ?? 0;
  }
  window.EFNotificationsAdapter = {
    async getUnreadCount() {
      return MODE === 'user' ? getUnreadCountUser() : getUnreadCountGlobal();
    }
  };
})();
