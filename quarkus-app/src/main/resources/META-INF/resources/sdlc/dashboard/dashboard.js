// Compatibility loader for previously cached dashboard HTML.
(() => {
  const script = document.createElement('script');
  script.src = '/sdlc/dashboard/dashboard-v2.js';
  script.defer = true;
  document.head.appendChild(script);
})();
>>>>>>> origin/main
})();
