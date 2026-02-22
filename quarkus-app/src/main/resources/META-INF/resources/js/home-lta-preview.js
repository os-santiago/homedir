(function () {
  const root = document.getElementById("home-lta-preview-root");
  if (!root) {
    return;
  }

  const listEl = document.getElementById("home-lta-preview-list");
  const targetUrl = root.dataset.targetUrl || "/comunidad#community-lta";
  const i18n = {
    empty: root.dataset.i18nEmpty || "No Lightning Threads yet.",
    loadError: root.dataset.i18nLoadError || "Could not load LTA preview."
  };

  function escapeText(value) {
    if (value == null) {
      return "";
    }
    return String(value).replace(/[&<>"']/g, (match) => {
      switch (match) {
        case "&":
          return "&amp;";
        case "<":
          return "&lt;";
        case ">":
          return "&gt;";
        case "\"":
          return "&quot;";
        default:
          return "&#39;";
      }
    });
  }

  function formatDate(raw) {
    if (!raw) {
      return "";
    }
    const value = new Date(raw);
    if (Number.isNaN(value.getTime())) {
      return "";
    }
    return value.toLocaleDateString(undefined, {
      month: "short",
      day: "2-digit"
    });
  }

  function renderEmpty(message) {
    listEl.innerHTML = `<p class="home-lta-preview-empty">${escapeText(message)}</p>`;
  }

  async function loadTop() {
    try {
      const response = await fetch("/api/community/lightning?limit=3&offset=0", {
        credentials: "same-origin"
      });
      if (!response.ok) {
        throw new Error("load_failed");
      }
      const payload = await response.json();
      const items = Array.isArray(payload.items) ? payload.items : [];
      if (!items.length) {
        renderEmpty(i18n.empty);
        return;
      }
      listEl.innerHTML = items
        .map((item) => {
          const title = escapeText(item.title || item.body || "LTA");
          const user = escapeText(item.user_name || "member");
          const href = `/comunidad?lta=${encodeURIComponent(item.id)}#community-lta`;
          const date = formatDate(item.published_at || item.created_at);
          return `
            <a class="home-lta-preview-item" href="${href}">
              <strong>${title}</strong>
              <span>${user}${date ? ` - ${escapeText(date)}` : ""}</span>
            </a>
          `;
        })
        .join("");
    } catch (error) {
      renderEmpty(i18n.loadError);
    }
  }

  root.addEventListener("click", (event) => {
    if (!(event.target instanceof HTMLElement)) {
      return;
    }
    if (event.target.closest("a")) {
      return;
    }
    window.location.href = targetUrl;
  });

  loadTop();
})();
