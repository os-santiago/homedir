(() => {
  const marker = document.querySelector('[data-reputation-vitals]');
  if (!marker) {
    return;
  }

  const route = marker.dataset.reputationVitals;
  if (route !== 'hub' && route !== 'how') {
    return;
  }

  // Keep ingestion bounded on public routes while still collecting useful trend signals.
  const sampleRate = 0.2;
  if (Math.random() > sampleRate) {
    return;
  }

  let lcpMs = 0;
  let inpMs = 0;
  let sent = false;

  const updateInp = (entry) => {
    if (!entry || typeof entry.duration !== 'number') {
      return;
    }
    if (!Number.isFinite(entry.duration) || entry.duration <= 0) {
      return;
    }
    inpMs = Math.max(inpMs, entry.duration);
  };

  try {
    const lcpObserver = new PerformanceObserver((list) => {
      list.getEntries().forEach((entry) => {
        if (entry && typeof entry.startTime === 'number' && Number.isFinite(entry.startTime)) {
          lcpMs = Math.max(lcpMs, entry.startTime);
        }
      });
    });
    lcpObserver.observe({ type: 'largest-contentful-paint', buffered: true });
  } catch (_ignored) {
    // Browser may not support this observer type.
  }

  try {
    const eventObserver = new PerformanceObserver((list) => {
      list.getEntries().forEach(updateInp);
    });
    eventObserver.observe({ type: 'event', buffered: true, durationThreshold: 40 });
  } catch (_ignored) {
    // Browser may not support Event Timing API.
  }

  const send = () => {
    if (sent) {
      return;
    }
    sent = true;

    const payload = {
      route,
      lcp_ms: lcpMs > 0 ? Math.round(lcpMs) : null,
      inp_ms: inpMs > 0 ? Math.round(inpMs) : null,
      viewport_width: window.innerWidth || null
    };

    if (payload.lcp_ms == null && payload.inp_ms == null) {
      return;
    }

    const body = JSON.stringify(payload);
    if (navigator.sendBeacon) {
      const blob = new Blob([body], { type: 'application/json' });
      if (navigator.sendBeacon('/api/community/reputation/web-vitals', blob)) {
        return;
      }
    }

    fetch('/api/community/reputation/web-vitals', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body,
      keepalive: true,
      credentials: 'same-origin'
    }).catch(() => {});
  };

  addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
      send();
    }
  });
  addEventListener('pagehide', send, { once: true });
})();
