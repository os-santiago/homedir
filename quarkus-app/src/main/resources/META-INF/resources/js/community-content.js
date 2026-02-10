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
  const totalMetricEl = document.getElementById("community-content-total");
  const activeFiltersEl = document.getElementById("community-active-filters");
  const activeFilterChipsEl = document.getElementById("community-active-filter-chips");
  const clearFiltersBtn = document.getElementById("community-clear-filters");
  const hotSectionEl = document.getElementById("community-hot");
  const hotListEl = document.getElementById("community-hot-list");
  const interestSectionEl = document.getElementById("community-interest");
  const interestListEl = document.getElementById("community-interest-list");
  const radarSectionEl = document.getElementById("community-radar");
  const radarListEl = document.getElementById("community-radar-list");

  const tabButtons = Array.from(document.querySelectorAll(".community-tab"));
  const filterButtons = Array.from(document.querySelectorAll(".community-filter"));
  const topicButtons = Array.from(document.querySelectorAll(".community-topic-btn"));

  const state = {
    view: initialView,
    filter: initialFilter,
    topic: sessionStorage.getItem("community.topic") || "all",
    tag: sessionStorage.getItem("community.tag") || "",
    expandedSummaries: new Set(),
    items: [],
    offset: 0,
    total: 0,
    loading: false
  };

  const i18n = {
    emptyFiltered: root.dataset.i18nEmptyFiltered || "No matches for current filters. Try a different combination.",
    emptyGeneric: root.dataset.i18nEmptyGeneric || "No curated content is available right now.",
    itemsUnit: root.dataset.i18nItemsUnit || "items",
    scorePrefix: root.dataset.i18nScorePrefix || "Score",
    voteLoginRequired: root.dataset.i18nVoteLoginRequired || "Sign in to vote",
    voteRecommended: root.dataset.i18nVoteRecommended || "Recommended",
    voteMustSee: root.dataset.i18nVoteMustSee || "Must see",
    voteNotForMe: root.dataset.i18nVoteNotForMe || "Not for me",
    loadError: root.dataset.i18nLoadError || "Could not load community content.",
    voteError: root.dataset.i18nVoteError || "Could not register your vote. Please try again.",
    allTags: root.dataset.i18nAllTags || "All tags",
    filterSourceInternet: root.dataset.i18nFilterSourceInternet || "Source: Internet",
    filterSourceMembers: root.dataset.i18nFilterSourceMembers || "Source: Members",
    filterTopicPrefix: root.dataset.i18nFilterTopicPrefix || "Topic",
    filterTagPrefix: root.dataset.i18nFilterTagPrefix || "Tag",
    originInternet: root.dataset.i18nOriginInternet || "Internet",
    originMembers: root.dataset.i18nOriginMembers || "Members",
    readUnit: root.dataset.i18nReadUnit || "min",
    badgeNew: root.dataset.i18nBadgeNew || "New",
    topPrefix: root.dataset.i18nTopPrefix || "Top",
    summaryShow: root.dataset.i18nSummaryShow || "Show summary",
    summaryHide: root.dataset.i18nSummaryHide || "Show less"
  };
  const uiLocale = document.documentElement.lang || navigator.language || undefined;

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
    if (empty && state.items.length > 0 && (state.topic !== "all" || state.tag)) {
      emptyEl.textContent = i18n.emptyFiltered;
    } else {
      emptyEl.textContent = i18n.emptyGeneric;
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
    return parsed.toLocaleDateString(uiLocale, { year: "numeric", month: "short", day: "numeric" });
  }

  function scoreOf(item) {
    return Number(item && item.score ? item.score : 0);
  }

  function isRecentItem(rawDate) {
    if (!rawDate) return false;
    const parsed = new Date(rawDate);
    if (Number.isNaN(parsed.getTime())) return false;
    const days = (Date.now() - parsed.getTime()) / 86400000;
    return days >= 0 && days <= 7;
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

  function normalizeTag(rawTag) {
    return String(rawTag || "")
      .trim()
      .toLowerCase();
  }

  function itemHasTag(item, tag) {
    if (!tag) return true;
    const tags = Array.isArray(item.tags) ? item.tags : [];
    return tags.some((entry) => normalizeTag(entry) === tag);
  }

  function visibleItems() {
    return state.items.filter((item) => {
      if (state.topic !== "all" && inferTopic(item) !== state.topic) {
        return false;
      }
      if (state.tag && !itemHasTag(item, state.tag)) {
        return false;
      }
      return true;
    });
  }

  function topicCatalog() {
    return [
      { key: "ai", label: "AI", icon: "smart_toy" },
      { key: "opensource", label: "Open Source", icon: "code" },
      { key: "devops", label: "DevOps", icon: "hub" },
      { key: "platform", label: "Platform", icon: "layers" }
    ];
  }

  function renderInterestCards(items) {
    if (!interestSectionEl || !interestListEl) {
      return;
    }
    interestListEl.textContent = "";

    if (!Array.isArray(items) || items.length === 0) {
      interestSectionEl.classList.add("hidden");
      return;
    }

    const counts = { ai: 0, opensource: 0, devops: 0, platform: 0 };
    items.forEach((item) => {
      const topic = inferTopic(item);
      if (counts[topic] != null) {
        counts[topic] += 1;
      }
    });

    interestSectionEl.classList.remove("hidden");

    topicCatalog().forEach((entry) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "community-interest-card";
      if (state.topic === entry.key) {
        btn.classList.add("active");
      }
      btn.dataset.topic = entry.key;

      const title = document.createElement("h4");
      title.className = "community-interest-title";
      title.innerHTML = `<span class=\"material-symbols-outlined\">${entry.icon}</span>${entry.label}`;

      const meta = document.createElement("p");
      meta.className = "community-interest-meta";
      meta.textContent = `${counts[entry.key] || 0} ${i18n.itemsUnit}`;

      btn.appendChild(title);
      btn.appendChild(meta);
      interestListEl.appendChild(btn);
    });
  }

  function renderTagRadar(items) {
    if (!radarSectionEl || !radarListEl) {
      return;
    }
    radarListEl.textContent = "";
    if (!Array.isArray(items) || items.length === 0) {
      radarSectionEl.classList.add("hidden");
      return;
    }

    const filteredByTopic =
      state.topic === "all" ? items : items.filter((item) => inferTopic(item) === state.topic);
    const counter = new Map();
    filteredByTopic.forEach((item) => {
      const tags = Array.isArray(item.tags) ? item.tags : [];
      tags.forEach((tagValue) => {
        const key = normalizeTag(tagValue);
        if (!key) return;
        const entry = counter.get(key) || { label: String(tagValue).trim(), count: 0 };
        entry.count += 1;
        counter.set(key, entry);
      });
    });

    const topTags = Array.from(counter.entries())
      .sort((a, b) => {
        if (b[1].count !== a[1].count) return b[1].count - a[1].count;
        return a[0].localeCompare(b[0]);
      })
      .slice(0, 10);

    if (topTags.length === 0) {
      radarSectionEl.classList.add("hidden");
      return;
    }

    radarSectionEl.classList.remove("hidden");

    const allBtn = document.createElement("button");
    allBtn.type = "button";
    allBtn.className = "community-radar-chip";
    if (!state.tag) {
      allBtn.classList.add("active");
    }
    allBtn.dataset.tag = "";
    allBtn.textContent = i18n.allTags;
    radarListEl.appendChild(allBtn);

    topTags.forEach(([key, data]) => {
      const chip = document.createElement("button");
      chip.type = "button";
      chip.className = "community-radar-chip";
      if (state.tag === key) {
        chip.classList.add("active");
      }
      chip.dataset.tag = key;

      const label = document.createElement("span");
      label.textContent = `#${data.label}`;

      const count = document.createElement("span");
      count.className = "community-radar-count";
      count.textContent = String(data.count);

      chip.appendChild(label);
      chip.appendChild(count);
      radarListEl.appendChild(chip);
    });
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
      meta.textContent = `${escapeText(item.source)} · ${i18n.scorePrefix} ${scoreOf(item).toFixed(2)}`;

      link.appendChild(title);
      link.appendChild(meta);
      hotListEl.appendChild(link);
    });
  }

  function renderActiveFilters() {
    if (!activeFiltersEl || !activeFilterChipsEl) {
      return;
    }
    activeFilterChipsEl.textContent = "";

    const chips = [];
    if (state.filter === "internet") {
      chips.push(i18n.filterSourceInternet);
    } else if (state.filter === "members") {
      chips.push(i18n.filterSourceMembers);
    }
    if (state.topic && state.topic !== "all") {
      chips.push(`${i18n.filterTopicPrefix}: ${state.topic}`);
    }
    if (state.tag) {
      chips.push(`${i18n.filterTagPrefix}: #${state.tag}`);
    }

    if (chips.length === 0) {
      activeFiltersEl.classList.add("hidden");
      return;
    }

    activeFiltersEl.classList.remove("hidden");
    chips.forEach((label) => {
      const chip = document.createElement("span");
      chip.className = "community-active-filter-chip";
      chip.textContent = label;
      activeFilterChipsEl.appendChild(chip);
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
      btn.title = i18n.voteLoginRequired;
    }
    btn.dataset.itemId = item.id;
    btn.dataset.vote = voteKey;
    btn.textContent = `${label} (${count})`;
    return btn;
  }

  function renderItems() {
    listEl.textContent = "";
    renderInterestCards(state.items);
    renderTagRadar(state.items);
    renderActiveFilters();
    if (totalMetricEl) {
      totalMetricEl.textContent = String(Number(state.total || state.items.length || 0));
    }

    const items = visibleItems();
    renderHotItems(items);

    items.forEach((item, index) => {
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
      origin.textContent = item.origin === "members" ? i18n.originMembers : i18n.originInternet;

      const score = document.createElement("span");
      score.className = "community-score-pill";
      score.textContent = `${i18n.scorePrefix} ${scoreOf(item).toFixed(2)}`;

      const read = document.createElement("span");
      read.className = "community-read-pill";
      read.textContent = `${readMinutes(item)} ${i18n.readUnit}`;

      eyebrow.appendChild(origin);
      eyebrow.appendChild(score);
      eyebrow.appendChild(read);
      if (state.view === "featured" && index < 3) {
        const rank = document.createElement("span");
        rank.className = "community-rank-pill";
        rank.textContent = `${i18n.topPrefix} ${index + 1}`;
        eyebrow.appendChild(rank);
      }
      if (isRecentItem(item.created_at)) {
        const fresh = document.createElement("span");
        fresh.className = "community-fresh-pill";
        fresh.textContent = i18n.badgeNew;
        eyebrow.appendChild(fresh);
      }
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

      const summaryText = escapeText(item.summary);
      if (summaryText) {
        const summary = document.createElement("p");
        summary.className = "community-item-summary";
        summary.textContent = summaryText;
        const expandableSummary = summaryText.length > 140;
        const expanded = state.expandedSummaries.has(String(item.id));
        if (expandableSummary && !expanded) {
          summary.classList.add("clamped");
        }
        if (expanded) {
          summary.classList.add("expanded");
        }
        card.appendChild(summary);

        if (expandableSummary) {
          const toggle = document.createElement("button");
          toggle.type = "button";
          toggle.className = "community-summary-toggle";
          toggle.dataset.itemId = String(item.id);
          toggle.setAttribute("aria-expanded", expanded ? "true" : "false");
          toggle.textContent = expanded ? i18n.summaryHide : i18n.summaryShow;
          card.appendChild(toggle);
        }
      }

      if (Array.isArray(item.tags) && item.tags.length > 0) {
        const tagsWrap = document.createElement("div");
        tagsWrap.className = "community-tags";
        item.tags.forEach((tag) => {
          const normalizedTag = normalizeTag(tag);
          const tagEl = document.createElement("button");
          tagEl.type = "button";
          tagEl.className = "community-tag community-tag-btn";
          if (normalizedTag && state.tag === normalizedTag) {
            tagEl.classList.add("active");
          }
          tagEl.dataset.tag = normalizedTag;
          tagEl.textContent = escapeText(tag);
          tagsWrap.appendChild(tagEl);
        });
        card.appendChild(tagsWrap);
      }

      const votesWrap = document.createElement("div");
      votesWrap.className = "community-votes";
      const counts = item.vote_counts || {};
      votesWrap.appendChild(createVoteButton(item, "recommended", i18n.voteRecommended, Number(counts.recommended || 0)));
      votesWrap.appendChild(createVoteButton(item, "must_see", i18n.voteMustSee, Number(counts.must_see || 0)));
      votesWrap.appendChild(createVoteButton(item, "not_for_me", i18n.voteNotForMe, Number(counts.not_for_me || 0)));
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
      state.expandedSummaries = new Set();
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
      if (reset && state.items.length > 0 && visibleItems().length === 0 && (state.tag || state.topic !== "all")) {
        state.topic = "all";
        state.tag = "";
        sessionStorage.setItem("community.topic", "all");
        sessionStorage.removeItem("community.tag");
        updateTopicState();
      }
      renderItems();
    } catch (error) {
      showFeedback(i18n.loadError);
      updateEmptyState(0);
    } finally {
      state.loading = false;
      showSkeleton(false);
      updateEmptyState(visibleItems().length);
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
      showFeedback(i18n.voteError);
    }
  }

  loadMoreBtn.addEventListener("click", () => load(false));

  function setTopic(nextTopic) {
    if (!nextTopic || nextTopic === state.topic) {
      return;
    }
    state.topic = nextTopic;
    sessionStorage.setItem("community.topic", state.topic);
    updateTopicState();
    renderItems();
  }

  function setTag(nextTag) {
    const normalizedTag = normalizeTag(nextTag);
    if (normalizedTag === state.tag) {
      return;
    }
    state.tag = normalizedTag;
    if (state.tag) {
      sessionStorage.setItem("community.tag", state.tag);
    } else {
      sessionStorage.removeItem("community.tag");
    }
    renderItems();
  }

  function clearAllFilters() {
    const shouldReload = state.filter !== "all";
    const changed = shouldReload || state.topic !== "all" || Boolean(state.tag);
    if (!changed) {
      return;
    }
    state.filter = "all";
    state.topic = "all";
    state.tag = "";
    sessionStorage.setItem("community.topic", "all");
    sessionStorage.removeItem("community.tag");
    updateFilterState();
    updateTopicState();
    if (shouldReload) {
      load(true);
    } else {
      renderItems();
    }
  }

  function toggleSummary(itemId) {
    const key = String(itemId || "");
    if (!key) return;
    if (state.expandedSummaries.has(key)) {
      state.expandedSummaries.delete(key);
    } else {
      state.expandedSummaries.add(key);
    }
    renderItems();
  }

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
      setTopic(nextTopic);
    });
  });

  if (interestListEl) {
    interestListEl.addEventListener("click", (event) => {
      const target = event.target;
      if (!(target instanceof HTMLElement)) return;
      const card = target.closest(".community-interest-card");
      if (!card) return;
      const nextTopic = String(card.dataset.topic || "").toLowerCase();
      if (!nextTopic) return;
      setTopic(nextTopic);
    });
  }

  if (radarListEl) {
    radarListEl.addEventListener("click", (event) => {
      const target = event.target;
      if (!(target instanceof HTMLElement)) return;
      const chip = target.closest(".community-radar-chip");
      if (!chip) return;
      setTag(chip.dataset.tag || "");
    });
  }

  if (clearFiltersBtn) {
    clearFiltersBtn.addEventListener("click", () => {
      clearAllFilters();
    });
  }

  listEl.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const summaryToggle = target.closest(".community-summary-toggle");
    if (summaryToggle) {
      toggleSummary(summaryToggle.dataset.itemId || "");
      return;
    }
    const tagBtn = target.closest(".community-tag-btn");
    if (tagBtn) {
      setTag(tagBtn.dataset.tag || "");
      return;
    }
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
