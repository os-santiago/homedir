(function () {
  const root = document.getElementById("community-content-root");
  if (!root) {
    return;
  }

  const authenticated = String(root.dataset.authenticated || "false") === "true";
  const pageSize = Number.parseInt(root.dataset.pageSize || "10", 10) || 10;
  const initialViewFromData = String(root.dataset.initialView || "").trim().toLowerCase();
  const initialViewFromUrl = new URLSearchParams(window.location.search).get("view");

  const listEl = document.getElementById("community-list");
  const skeletonEl = document.getElementById("community-skeleton");
  const emptyEl = document.getElementById("community-empty");
  const loadMoreBtn = document.getElementById("community-load-more");
  const feedbackEl = document.getElementById("community-feedback");
  const tabButtons = Array.from(document.querySelectorAll(".community-tab"));

  const state = {
    view: "featured",
    items: [],
    offset: 0,
    total: 0,
    loading: false
  };

  function normalizeView(value) {
    const normalized = String(value || "").trim().toLowerCase();
    if (normalized === "new" || normalized === "featured") {
      return normalized;
    }
    return "featured";
  }

  state.view = normalizeView(initialViewFromUrl || initialViewFromData);

  function escapeText(value) {
    return value == null ? "" : String(value);
  }

  function showFeedback(message) {
    feedbackEl.textContent = message;
    feedbackEl.classList.remove("hidden");
  }

  function hideFeedback() {
    feedbackEl.textContent = "";
    feedbackEl.classList.add("hidden");
  }

  function showSkeleton(show) {
    skeletonEl.classList.toggle("hidden", !show);
  }

  function updateEmptyState() {
    const empty = !state.loading && state.items.length === 0;
    emptyEl.classList.toggle("hidden", !empty);
  }

  function updateLoadMoreState() {
    const hasMore = state.items.length < state.total;
    loadMoreBtn.classList.toggle("hidden", !hasMore || state.loading);
    loadMoreBtn.disabled = state.loading;
  }

  function formatDate(raw) {
    if (!raw) return "";
    const parsed = new Date(raw);
    if (Number.isNaN(parsed.getTime())) return "";
    return parsed.toLocaleDateString("es-CL", { year: "numeric", month: "short", day: "numeric" });
  }

  function createVoteButton(item, voteKey, label, count) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "community-vote-btn";
    if (item.my_vote === voteKey) {
      btn.classList.add("active");
    }
    if (!authenticated) {
      btn.disabled = true;
      btn.title = "Inicia sesión para votar";
    }
    btn.dataset.itemId = item.id;
    btn.dataset.vote = voteKey;
    btn.textContent = `${label} (${count})`;
    return btn;
  }

  function renderItems() {
    listEl.textContent = "";
    state.items.forEach((item) => {
      const card = document.createElement("article");
      card.className = "community-item-card";
      card.dataset.itemId = item.id;

      const header = document.createElement("div");
      header.className = "community-item-header";

      const titleWrap = document.createElement("div");
      const title = document.createElement("h3");
      title.className = "community-item-title";
      const link = document.createElement("a");
      link.href = item.url;
      link.target = "_blank";
      link.rel = "noopener noreferrer";
      link.textContent = escapeText(item.title);
      title.appendChild(link);
      titleWrap.appendChild(title);

      const score = document.createElement("small");
      score.textContent = `Score ${Number(item.score || 0).toFixed(2)}`;
      titleWrap.appendChild(score);
      header.appendChild(titleWrap);
      card.appendChild(header);

      const meta = document.createElement("p");
      meta.className = "community-item-meta";
      const created = formatDate(item.created_at);
      meta.textContent = `${escapeText(item.source)}${created ? ` · ${created}` : ""}`;
      card.appendChild(meta);

      const summary = document.createElement("p");
      summary.className = "community-item-summary";
      summary.textContent = escapeText(item.summary);
      card.appendChild(summary);

      if (Array.isArray(item.tags) && item.tags.length > 0) {
        const tagsWrap = document.createElement("div");
        tagsWrap.className = "community-tags";
        item.tags.forEach((tag) => {
          const tagEl = document.createElement("span");
          tagEl.className = "community-tag";
          tagEl.textContent = escapeText(tag);
          tagsWrap.appendChild(tagEl);
        });
        card.appendChild(tagsWrap);
      }

      const votesWrap = document.createElement("div");
      votesWrap.className = "community-votes";
      const counts = item.vote_counts || {};
      votesWrap.appendChild(createVoteButton(item, "recommended", "Recomendado", Number(counts.recommended || 0)));
      votesWrap.appendChild(createVoteButton(item, "must_see", "Imperdible", Number(counts.must_see || 0)));
      votesWrap.appendChild(createVoteButton(item, "not_for_me", "No es para mí", Number(counts.not_for_me || 0)));
      card.appendChild(votesWrap);

      listEl.appendChild(card);
    });

    updateEmptyState();
    updateLoadMoreState();
  }

  function applyVoteLocally(item, vote) {
    const counts = item.vote_counts || { recommended: 0, must_see: 0, not_for_me: 0 };
    const previous = item.my_vote;
    if (previous && counts[previous] != null) {
      counts[previous] = Math.max(0, Number(counts[previous]) - 1);
    }
    if (counts[vote] != null) {
      counts[vote] = Number(counts[vote]) + 1;
    }
    item.my_vote = vote;
    item.vote_counts = counts;
  }

  async function load(reset) {
    if (state.loading) return;
    state.loading = true;
    hideFeedback();
    showSkeleton(state.items.length === 0);
    updateLoadMoreState();
    if (reset) {
      state.offset = 0;
      state.total = 0;
      state.items = [];
      renderItems();
    }

    try {
      const response = await fetch(
        `/api/community/content?view=${encodeURIComponent(state.view)}&limit=${encodeURIComponent(pageSize)}&offset=${encodeURIComponent(state.offset)}`,
        { headers: { Accept: "application/json" } }
      );
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const data = await response.json();
      const received = Array.isArray(data.items) ? data.items : [];
      if (reset) {
        state.items = received;
      } else {
        state.items = state.items.concat(received);
      }
      state.total = Number(data.total || state.items.length);
      state.offset = state.items.length;
      renderItems();
    } catch (error) {
      showFeedback("No se pudo cargar el contenido de comunidad.");
      updateEmptyState();
    } finally {
      state.loading = false;
      showSkeleton(false);
      updateLoadMoreState();
    }
  }

  async function sendVote(itemId, vote) {
    if (!authenticated) {
      window.location.href = "/private/profile";
      return;
    }
    const item = state.items.find((entry) => entry.id === itemId);
    if (!item) return;

    const snapshot = JSON.parse(JSON.stringify(item));
    applyVoteLocally(item, vote);
    renderItems();

    try {
      const response = await fetch(`/api/community/content/${encodeURIComponent(itemId)}/vote`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json"
        },
        body: JSON.stringify({ vote })
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const data = await response.json();
      if (data && data.item) {
        const idx = state.items.findIndex((entry) => entry.id === itemId);
        if (idx >= 0) {
          state.items[idx] = data.item;
        }
      }
      renderItems();
    } catch (error) {
      const idx = state.items.findIndex((entry) => entry.id === itemId);
      if (idx >= 0) {
        state.items[idx] = snapshot;
      }
      renderItems();
      showFeedback("No se pudo registrar tu voto. Inténtalo nuevamente.");
    }
  }

  loadMoreBtn.addEventListener("click", () => load(false));

  tabButtons.forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.view === state.view);
    btn.addEventListener("click", () => {
      const nextView = btn.dataset.view;
      if (!nextView || nextView === state.view) {
        return;
      }
      state.view = nextView;
      const url = new URL(window.location.href);
      url.searchParams.set("view", state.view);
      window.history.replaceState({}, "", url.toString());
      tabButtons.forEach((candidate) => {
        candidate.classList.toggle("active", candidate.dataset.view === state.view);
      });
      load(true);
    });
  });

  listEl.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const voteBtn = target.closest(".community-vote-btn");
    if (!voteBtn) return;
    const itemId = voteBtn.dataset.itemId;
    const vote = voteBtn.dataset.vote;
    if (!itemId || !vote) return;
    sendVote(itemId, vote);
  });

  load(true);
})();
