(function () {
  const root = document.getElementById("community-content-root");
  if (!root) {
    return;
  }

  const authenticated = String(root.dataset.authenticated || "false") === "true";
  const pageSize = Number.parseInt(root.dataset.pageSize || "10", 10) || 10;
  const initialView = String(root.dataset.initialView || "featured") === "new" ? "new" : "featured";
  const initialFilter = (() => {
    const raw = String(root.dataset.initialFilter || "all").toLowerCase();
    return raw === "internet" || raw === "members" ? raw : "all";
  })();

  const listEl = document.getElementById("community-list");
  const skeletonEl = document.getElementById("community-skeleton");
  const emptyEl = document.getElementById("community-empty");
  const loadMoreBtn = document.getElementById("community-load-more");
  const feedbackEl = document.getElementById("community-feedback");
  const hotSectionEl = document.getElementById("community-hot");
  const hotListEl = document.getElementById("community-hot-list");

  const tabButtons = Array.from(document.querySelectorAll(".community-tab"));
  const filterButtons = Array.from(document.querySelectorAll(".community-filter"));
  const topicButtons = Array.from(document.querySelectorAll(".community-topic-btn"));

  const state = {
    view: initialView,
    filter: initialFilter,
    topic: "all",
    items: [],
    offset: 0,
    total: 0,
    loading: false
  };

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

  function updateEmptyState(visibleCount) {
    const empty = !state.loading && visibleCount === 0;
    if (empty && state.items.length > 0 && state.topic !== "all") {
      emptyEl.textContent = "Sin coincidencias para este tema. Prueba otro filtro.";
    } else {
      emptyEl.textContent = "No hay contenido curado disponible por ahora.";
    }
    emptyEl.classList.toggle("hidden", !empty);
  }

  function updateLoadMoreState() {
    const hasMore = state.items.length < state.total;
    loadMoreBtn.classList.toggle("hidden", !hasMore || state.loading);
    loadMoreBtn.disabled = state.loading;
  }

  function updateViewState() {
    tabButtons.forEach((btn) => {
      btn.classList.toggle("active", btn.dataset.view === state.view);
      btn.setAttribute("aria-selected", btn.dataset.view === state.view ? "true" : "false");
    });
  }

  function updateFilterState() {
    filterButtons.forEach((btn) => {
      btn.classList.toggle("community-filter-active", btn.dataset.filter === state.filter);
    });
  }

  function updateTopicState() {
    topicButtons.forEach((btn) => {
      btn.classList.toggle("community-topic-active", btn.dataset.topic === state.topic);
    });
  }

  function formatDate(raw) {
    if (!raw) return "";
    const parsed = new Date(raw);
    if (Number.isNaN(parsed.getTime())) return "";
    return parsed.toLocaleDateString("es-CL", { year: "numeric", month: "short", day: "numeric" });
  }

  function scoreOf(item) {
    return Number(item && item.score ? item.score : 0);
  }

  function inferTopic(item) {
    const fields = [
      ...(Array.isArray(item.tags) ? item.tags : []),
      item.title,
      item.summary,
      item.source
    ];
    const text = fields
      .filter(Boolean)
      .join(" ")
      .toLowerCase();

    if (/(\bai\b|\bml\b|llm|agent|copilot|inteligencia artificial|machine learning)/.test(text)) {
      return "ai";
    }
    if (/(open source|opensource|oss|github|gitlab|apache foundation|linux foundation)/.test(text)) {
      return "opensource";
    }
    if (/(devops|kubernetes|k8s|ci\/cd|sre|observability|terraform|helm|argo)/.test(text)) {
      return "devops";
    }
    if (/(platform engineering|platform|developer platform|idp|internal developer)/.test(text)) {
      return "platform";
    }
    return "general";
  }

  function readMinutes(item) {
    const text = `${escapeText(item.title)} ${escapeText(item.summary)}`.trim();
    if (!text) {
      return 1;
    }
    const words = text.split(/\s+/).length;
    return Math.max(1, Math.round(words / 180));
  }

  function visibleItems() {
    if (state.topic === "all") {
      return state.items;
    }
    return state.items.filter((item) => inferTopic(item) === state.topic);
  }

  function renderHotItems(items) {
    if (!hotSectionEl || !hotListEl) {
      return;
    }
    hotListEl.textContent = "";

    const hotItems = items
      .slice()
      .sort((a, b) => scoreOf(b) - scoreOf(a))
      .slice(0, 3);

    if (hotItems.length === 0) {
      hotSectionEl.classList.add("hidden");
      return;
    }

    hotSectionEl.classList.remove("hidden");

    hotItems.forEach((item) => {
      const link = document.createElement("a");
      link.className = "community-hot-item";
      link.href = item.url;
      link.target = "_blank";
      link.rel = "noopener noreferrer";

      const title = document.createElement("h4");
      title.className = "community-hot-item-title";
      title.textContent = escapeText(item.title);

      const meta = document.createElement("p");
      meta.className = "community-hot-meta";
      meta.textContent = `${escapeText(item.source)} · Score ${scoreOf(item).toFixed(2)}`;

      link.appendChild(title);
      link.appendChild(meta);
      hotListEl.appendChild(link);
    });
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

    const items = visibleItems();
    renderHotItems(items);

    items.forEach((item) => {
      const card = document.createElement("article");
      card.className = "community-item-card";
      card.dataset.itemId = item.id;

      const header = document.createElement("div");
      header.className = "community-item-header";

      const main = document.createElement("div");
      main.className = "community-item-main";

      const eyebrow = document.createElement("div");
      eyebrow.className = "community-item-eyebrow";

      const origin = document.createElement("span");
      origin.className = `community-origin-pill ${item.origin || "internet"}`;
      origin.textContent = item.origin === "members" ? "Members" : "Internet";

      const score = document.createElement("span");
      score.className = "community-score-pill";
      score.textContent = `Score ${scoreOf(item).toFixed(2)}`;

      const read = document.createElement("span");
      read.className = "community-read-pill";
      read.textContent = `${readMinutes(item)} min`;

      eyebrow.appendChild(origin);
      eyebrow.appendChild(score);
      eyebrow.appendChild(read);
      main.appendChild(eyebrow);

      const title = document.createElement("h3");
      title.className = "community-item-title";
      const link = document.createElement("a");
      link.href = item.url;
      link.target = "_blank";
      link.rel = "noopener noreferrer";
      link.textContent = escapeText(item.title);
      title.appendChild(link);
      main.appendChild(title);

      header.appendChild(main);
      card.appendChild(header);

      const meta = document.createElement("p");
      meta.className = "community-item-meta";
      const created = formatDate(item.created_at);
      meta.textContent = `${escapeText(item.source)}${created ? ` · ${created}` : ""}`;
      card.appendChild(meta);

      const topic = inferTopic(item);
      const topicHint = document.createElement("span");
      topicHint.className = "community-topic-hint";
      topicHint.innerHTML = `<span class="community-topic-dot ${topic}"></span>${topic.toUpperCase()}`;
      card.appendChild(topicHint);

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
      votesWrap.appendChild(createVoteButton(item, "not_for_me", "No es para mi", Number(counts.not_for_me || 0)));
      card.appendChild(votesWrap);

      listEl.appendChild(card);
    });

    updateEmptyState(items.length);
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
        `/api/community/content?view=${encodeURIComponent(state.view)}&filter=${encodeURIComponent(state.filter)}&limit=${encodeURIComponent(pageSize)}&offset=${encodeURIComponent(state.offset)}`,
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
      updateEmptyState(0);
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
      showFeedback("No se pudo registrar tu voto. Intentalo nuevamente.");
    }
  }

  loadMoreBtn.addEventListener("click", () => load(false));

  tabButtons.forEach((btn) => {
    btn.addEventListener("click", () => {
      const nextView = btn.dataset.view;
      if (!nextView || nextView === state.view) {
        return;
      }
      state.view = nextView;
      updateViewState();
      load(true);
    });
  });

  filterButtons.forEach((btn) => {
    btn.addEventListener("click", () => {
      const nextFilter = String(btn.dataset.filter || "").toLowerCase();
      if (!nextFilter || nextFilter === state.filter) {
        return;
      }
      if (nextFilter !== "all" && nextFilter !== "internet" && nextFilter !== "members") {
        return;
      }
      state.filter = nextFilter;
      updateFilterState();
      load(true);
    });
  });

  topicButtons.forEach((btn) => {
    btn.addEventListener("click", () => {
      const nextTopic = String(btn.dataset.topic || "all").toLowerCase();
      if (!nextTopic || nextTopic === state.topic) {
        return;
      }
      state.topic = nextTopic;
      updateTopicState();
      renderItems();
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

  updateViewState();
  updateFilterState();
  updateTopicState();
  load(true);
})();
