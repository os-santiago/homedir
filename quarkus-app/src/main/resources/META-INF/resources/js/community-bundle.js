(function () {
  const root =
    document.getElementById("community-lightning-root") ||
    document.getElementById("home-lightning-root");
  if (!root) {
    return;
  }

  const authenticated = String(root.dataset.authenticated || "false") === "true";
  const i18n = {
    loginRequired: root.dataset.i18nLoginRequired || "Sign in to participate.",
    postLimit:
      root.dataset.i18nPostLimit ||
      "1 post per hour per account. Replies are limited to 1 per minute.",
    postSubmit: root.dataset.i18nPostSubmit || "Post",
    postSubmitting: root.dataset.i18nPostSubmitting || "Posting...",
    comment: root.dataset.i18nComment || "Reply",
    like: root.dataset.i18nLike || "Like",
    report: root.dataset.i18nReport || "Report",
    loadMore: root.dataset.i18nLoadMore || "Load more",
    empty: root.dataset.i18nEmpty || "No sharp statements yet.",
    queued: root.dataset.i18nQueued || "Queued. It will be published in turn.",
    published: root.dataset.i18nPublished || "Published.",
    serverLimit:
      root.dataset.i18nServerLimit ||
      "Maximo de publicaciones por minuto del servidor superado, intenta mas tarde",
    reportSuccess: root.dataset.i18nReportSuccess || "Report sent to moderation.",
    reportDuplicate: root.dataset.i18nReportDuplicate || "You already reported this item.",
    bestAnswer: root.dataset.i18nBestAnswer || "Best answer",
    pingBest: root.dataset.i18nPingBest || "Ping best answer",
    edit: root.dataset.i18nEdit || "Edit",
    modified: root.dataset.i18nModified || "Modified",
    datePosted: root.dataset.i18nDatePosted || "Posted",
    dateUpdated: root.dataset.i18nDateUpdated || "Updated",
    dateLastComment: root.dataset.i18nDateLastComment || "Last reply",
    promptEditThread:
      root.dataset.i18nPromptEditThread || "Edit your statement (max 100 chars)",
    promptEditComment:
      root.dataset.i18nPromptEditComment || "Edit your reply (max 200 chars)",
    updateSuccess: root.dataset.i18nUpdateSuccess || "Updated.",
    promptComment: root.dataset.i18nPromptComment || "Write your reply (max 200 chars)",
    promptReason: root.dataset.i18nPromptReason || "Reason for report",
    commentTooLong:
      root.dataset.i18nCommentTooLong ||
      "Replies must be 200 characters or less."
  };

  const form = document.getElementById("home-lightning-form");
  const statementInput = document.getElementById("home-lightning-statement");
  const countEl = document.getElementById("home-lightning-count");
  const submitBtn = document.getElementById("home-lightning-submit");
  const feedback = document.getElementById("home-lightning-feedback");
  const listEl = document.getElementById("home-lightning-list");
  const emptyEl = document.getElementById("home-lightning-empty");
  const loadMoreBtn = document.getElementById("home-lightning-load-more");

  const state = {
    limit: 6,
    offset: 0,
    total: 0,
    loading: false,
    highlightThreadId: new URLSearchParams(window.location.search).get("lta") || "",
    highlightHandled: false,
    highlightLoads: 0
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

  function showFeedback(message, isError) {
    if (!feedback) {
      return;
    }
    feedback.textContent = message || "";
    feedback.classList.toggle("hidden", !message);
    feedback.classList.toggle("home-lightning-feedback-error", Boolean(isError));
  }

  function formatDate(raw) {
    if (!raw) {
      return "";
    }
    const value = new Date(raw);
    if (Number.isNaN(value.getTime())) {
      return "";
    }
    return value.toLocaleString(undefined, {
      month: "short",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit"
    });
  }

  function encodeData(value) {
    return encodeURIComponent(String(value == null ? "" : value));
  }

  function decodeData(value) {
    try {
      return decodeURIComponent(String(value == null ? "" : value));
    } catch (error) {
      return String(value == null ? "" : value);
    }
  }

  function parseEpoch(raw) {
    if (!raw) {
      return 0;
    }
    const value = new Date(raw).getTime();
    return Number.isFinite(value) ? value : 0;
  }

  function isModified(editedAt) {
    return parseEpoch(editedAt) > 0;
  }

  function threadMeta(thread) {
    const parts = [];
    const posted = formatDate(thread.published_at || thread.created_at);
    if (posted) {
      parts.push(`${i18n.datePosted}: ${posted}`);
    }
    const modified = isModified(thread.edited_at);
    const updated = formatDate(thread.edited_at);
    if (modified && updated) {
      parts.push(`${i18n.dateUpdated}: ${updated}`);
    }
    const lastComment = formatDate(thread.last_comment_at);
    if (lastComment) {
      parts.push(`${i18n.dateLastComment}: ${lastComment}`);
    }
    return {
      text: parts.join(" · "),
      modified
    };
  }

  function updateCount() {
    if (!statementInput || !countEl) {
      return;
    }
    const current = String(statementInput.value || "").length;
    countEl.textContent = `${current}/100`;
  }

  function commentMarkup(comment, isBest) {
    const cls = isBest ? "home-lightning-comment best" : "home-lightning-comment";
    const modified = isModified(comment.edited_at);
    const posted = formatDate(comment.created_at);
    const updated = formatDate(comment.edited_at);
    const dateLine = modified && updated
      ? `${i18n.datePosted}: ${posted} · ${i18n.dateUpdated}: ${updated}`
      : `${i18n.datePosted}: ${posted}`;
    return `
      <div class="${cls}" id="hl-comment-${escapeText(comment.id)}">
        <div class="home-lightning-comment-head">
          <strong>${escapeText(comment.user_name || "member")}</strong>
          <span>${escapeText(dateLine)}</span>
        </div>
        ${modified ? `<span class="home-lightning-modified">${i18n.modified}</span>` : ""}
        <p>${escapeText(comment.body)}</p>
        <div class="home-lightning-comment-actions">
          <button type="button" class="home-lightning-mini-btn" data-action="like-comment" data-id="${escapeText(comment.id)}">${i18n.like} · ${Number(comment.likes || 0)}</button>
          ${comment.is_owner ? `<button type="button" class="home-lightning-mini-btn" data-action="edit-comment" data-id="${escapeText(comment.id)}" data-text="${encodeData(comment.body)}">${i18n.edit}</button>` : ""}
          <button type="button" class="home-lightning-mini-btn" data-action="report-comment" data-id="${escapeText(comment.id)}">${i18n.report}</button>
        </div>
      </div>
    `;
  }

  function threadMarkup(thread) {
    const comments = Array.isArray(thread.top_comments) ? thread.top_comments : [];
    const bestId = thread.best_comment_id || "";
    const commentsHtml = comments.map((item) => commentMarkup(item, item.id === bestId)).join("");
    const body = String(thread.body || "").trim();
    const title = String(thread.title || "").trim();
    const showBody = body && body !== title;
    const meta = threadMeta(thread);
    const modifiedBadge = meta.modified
      ? `<span class="home-lightning-modified">${i18n.modified}</span>`
      : "";
    const bestBlock = thread.best_comment
      ? `<div class="home-lightning-best">
          <span>${i18n.bestAnswer}: ${escapeText(thread.best_comment.user_name || "member")}</span>
          <button type="button" class="home-lightning-mini-btn" data-action="ping-best" data-id="${escapeText(thread.best_comment.id)}">${i18n.pingBest}</button>
        </div>`
      : "";
    return `
      <article class="home-lightning-item" data-thread-id="${escapeText(thread.id)}">
        <div class="home-lightning-item-head">
          <strong>${escapeText(thread.user_name || "member")}</strong>
          <small>${escapeText(meta.text)}</small>
        </div>
        ${modifiedBadge}
        <h3>${escapeText(title || body)}</h3>
        ${showBody ? `<p>${escapeText(body)}</p>` : ""}
        ${bestBlock}
        <div class="home-lightning-thread-actions">
          <button type="button" class="home-lightning-mini-btn" data-action="like-thread" data-id="${escapeText(thread.id)}">${i18n.like} · ${Number(thread.likes || 0)}</button>
          <button type="button" class="home-lightning-mini-btn" data-action="comment-thread" data-id="${escapeText(thread.id)}">${i18n.comment}</button>
          ${thread.is_owner ? `<button type="button" class="home-lightning-mini-btn" data-action="edit-thread" data-id="${escapeText(thread.id)}" data-text="${encodeData(title || body)}">${i18n.edit}</button>` : ""}
          <button type="button" class="home-lightning-mini-btn" data-action="report-thread" data-id="${escapeText(thread.id)}">${i18n.report}</button>
        </div>
        <div class="home-lightning-comments">${commentsHtml}</div>
      </article>
    `;
  }

  async function fetchThreads(reset) {
    if (state.loading) {
      return;
    }
    state.loading = true;
    if (reset) {
      state.offset = 0;
      state.total = 0;
    }
    try {
      const response = await fetch(
        `/api/community/lightning?limit=${state.limit}&offset=${state.offset}&comments_limit=3`,
        { credentials: "same-origin" }
      );
      if (!response.ok) {
        throw new Error("load_failed");
      }
      const data = await response.json();
      const items = Array.isArray(data.items) ? data.items : [];
      if (reset) {
        listEl.innerHTML = "";
      }
      listEl.insertAdjacentHTML("beforeend", items.map((item) => threadMarkup(item)).join(""));
      state.total = Number(data.total || 0);
      state.offset += items.length;
      emptyEl.classList.toggle("hidden", state.offset > 0);
      const hasMore = state.offset < state.total;
      if (loadMoreBtn) {
        loadMoreBtn.classList.toggle("hidden", !hasMore);
        loadMoreBtn.textContent = i18n.loadMore;
      }
      window.setTimeout(tryHighlightThread, 0);
    } catch (error) {
      showFeedback(i18n.serverLimit, true);
    } finally {
      state.loading = false;
    }
  }

  function tryHighlightThread() {
    if (!state.highlightThreadId || state.highlightHandled) {
      return;
    }
    const node = listEl.querySelector(`[data-thread-id="${state.highlightThreadId}"]`);
    if (node) {
      node.classList.add("home-lightning-item-focus");
      node.scrollIntoView({ behavior: "smooth", block: "center" });
      state.highlightHandled = true;
      window.setTimeout(() => {
        node.classList.remove("home-lightning-item-focus");
      }, 2200);
      return;
    }
    if (state.offset < state.total && state.highlightLoads < 4 && !state.loading) {
      state.highlightLoads += 1;
      fetchThreads(false);
    }
  }

  async function postThread(event) {
    event.preventDefault();
    if (!authenticated) {
      showFeedback(i18n.loginRequired, true);
      return;
    }
    const statement = String(statementInput?.value || "").trim();
    if (!statement) {
      return;
    }
    submitBtn.disabled = true;
    submitBtn.textContent = i18n.postSubmitting;
    showFeedback("", false);
    try {
      const response = await fetch("/api/community/lightning/threads", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "same-origin",
        body: JSON.stringify({ statement })
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        const message = payload.message || payload.error || i18n.serverLimit;
        throw new Error(message);
      }
      if (statementInput) {
        statementInput.value = "";
      }
      updateCount();
      showFeedback(payload.queued ? i18n.queued : i18n.published, false);
      await fetchThreads(true);
    } catch (error) {
      showFeedback(error instanceof Error ? error.message : i18n.serverLimit, true);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = i18n.postSubmit;
    }
  }

  async function mutate(url, body, successMessage, duplicateMessage, method) {
    const response = await fetch(url, {
      method: method || "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "same-origin",
      body: JSON.stringify(body || {})
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      const message = payload.message || payload.error || i18n.serverLimit;
      throw new Error(message);
    }
    if (duplicateMessage && payload.duplicate) {
      showFeedback(duplicateMessage, false);
    } else if (successMessage) {
      showFeedback(successMessage, false);
    }
  }

  root.addEventListener("click", async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) {
      return;
    }
    const action = target.dataset.action;
    const id = target.dataset.id;
    if (!action || !id) {
      return;
    }
    if (!authenticated) {
      showFeedback(i18n.loginRequired, true);
      return;
    }
    try {
      if (action === "comment-thread") {
        const text = window.prompt(i18n.promptComment);
        const trimmed = String(text || "").trim();
        if (!trimmed) {
          return;
        }
        if (trimmed.length > 200) {
          showFeedback(i18n.commentTooLong, true);
          return;
        }
        await mutate(
          `/api/community/lightning/threads/${encodeURIComponent(id)}/comments`,
          { body: trimmed },
          "",
          ""
        );
        await fetchThreads(true);
        return;
      }
      if (action === "edit-thread") {
        const currentText = decodeData(target.dataset.text || "");
        const text = window.prompt(i18n.promptEditThread, currentText);
        const trimmed = String(text || "").trim();
        if (!trimmed) {
          return;
        }
        if (trimmed.length > 100) {
          showFeedback(i18n.postLimit, true);
          return;
        }
        await mutate(
          `/api/community/lightning/threads/${encodeURIComponent(id)}`,
          { statement: trimmed },
          i18n.updateSuccess,
          "",
          "PUT"
        );
        await fetchThreads(true);
        return;
      }
      if (action === "edit-comment") {
        const currentText = decodeData(target.dataset.text || "");
        const text = window.prompt(i18n.promptEditComment, currentText);
        const trimmed = String(text || "").trim();
        if (!trimmed) {
          return;
        }
        if (trimmed.length > 200) {
          showFeedback(i18n.commentTooLong, true);
          return;
        }
        await mutate(
          `/api/community/lightning/comments/${encodeURIComponent(id)}`,
          { body: trimmed },
          i18n.updateSuccess,
          "",
          "PUT"
        );
        await fetchThreads(true);
        return;
      }
      if (action === "report-thread") {
        const reason = window.prompt(i18n.promptReason);
        if (!reason || !reason.trim()) {
          return;
        }
        await mutate(
          `/api/community/lightning/threads/${encodeURIComponent(id)}/report`,
          { reason },
          i18n.reportSuccess,
          i18n.reportDuplicate
        );
        await fetchThreads(true);
        return;
      }
      if (action === "report-comment") {
        const reason = window.prompt(i18n.promptReason);
        if (!reason || !reason.trim()) {
          return;
        }
        await mutate(
          `/api/community/lightning/comments/${encodeURIComponent(id)}/report`,
          { reason },
          i18n.reportSuccess,
          i18n.reportDuplicate
        );
        await fetchThreads(true);
        return;
      }
      if (action === "like-thread") {
        await fetch(`/api/community/lightning/threads/${encodeURIComponent(id)}/like`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          credentials: "same-origin",
          body: JSON.stringify({ liked: true })
        });
        await fetchThreads(true);
        return;
      }
      if (action === "like-comment") {
        await fetch(`/api/community/lightning/comments/${encodeURIComponent(id)}/like`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          credentials: "same-origin",
          body: JSON.stringify({ liked: true })
        });
        await fetchThreads(true);
        return;
      }
      if (action === "ping-best") {
        const best = document.getElementById(`hl-comment-${id}`);
        if (best) {
          best.scrollIntoView({ behavior: "smooth", block: "center" });
        }
      }
    } catch (error) {
      showFeedback(error instanceof Error ? error.message : i18n.serverLimit, true);
    }
  });

  if (statementInput) {
    statementInput.addEventListener("input", updateCount);
  }

  if (form) {
    form.addEventListener("submit", postThread);
  }

  if (loadMoreBtn) {
    loadMoreBtn.addEventListener("click", () => {
      fetchThreads(false);
    });
    loadMoreBtn.textContent = i18n.loadMore;
  }

  if (emptyEl) {
    emptyEl.textContent = i18n.empty;
  }
  updateCount();
  showFeedback(i18n.postLimit, false);
  fetchThreads(true);
})();
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
  const initialMedia = (() => {
    const raw = String(root.dataset.initialMedia || "all").toLowerCase();
    return raw === "video_story" || raw === "podcast" || raw === "article_blog" ? raw : "all";
  })();
  const ultraLiteMode = String(root.dataset.ultraLite || "false") === "true";

  const listEl = document.getElementById("community-list");
  const skeletonEl = document.getElementById("community-skeleton");
  const emptyEl = document.getElementById("community-empty");
  const loadMoreBtn = document.getElementById("community-load-more");
  const feedbackEl = document.getElementById("community-feedback");
  const activationEl = document.getElementById("community-activation");
  const activationStatusEl = document.getElementById("community-activation-status");
  const activationProgressEl = document.getElementById("community-activation-progress");
  const personalEmptyEl = document.getElementById("community-personal-empty");
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
  const mediaButtons = Array.from(document.querySelectorAll(".community-media"));
  const topicButtons = Array.from(document.querySelectorAll(".community-topic-btn"));
  const cacheTtlMs = Math.max(5000, Number.parseInt(root.dataset.cacheTtlMs || "60000", 10) || 60000);
  const responseCache = new Map();

  const state = {
    view: initialView,
    filter: initialFilter,
    media: initialMedia,
    topic: "all",
    tag: sessionStorage.getItem("community.tag") || "",
    expandedSummaries: new Set(),
    openPreviews: new Set(),
    items: [],
    offset: 0,
    total: 0,
    userVoteCount: 0,
    loading: false,
    requestToken: 0
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
    filterMediaPrefix: root.dataset.i18nFilterMediaPrefix || "Format",
    filterTopicPrefix: root.dataset.i18nFilterTopicPrefix || "Topic",
    filterTagPrefix: root.dataset.i18nFilterTagPrefix || "Tag",
    originInternet: root.dataset.i18nOriginInternet || "Internet",
    originMembers: root.dataset.i18nOriginMembers || "Members",
    topicAi: root.dataset.i18nTopicAi || "AI",
    topicPlatformEngineering: root.dataset.i18nTopicPlatformEngineering || "Platform Engineering",
    topicCloudNative: root.dataset.i18nTopicCloudNative || "Cloud Native",
    topicSecurity: root.dataset.i18nTopicSecurity || "Security",
    mediaVideoStory: root.dataset.i18nMediaVideoStory || "Video Story",
    mediaPodcast: root.dataset.i18nMediaPodcast || "Podcast",
    mediaArticleBlog: root.dataset.i18nMediaArticleBlog || "Article/Blog",
    readUnit: root.dataset.i18nReadUnit || "min",
    badgeNew: root.dataset.i18nBadgeNew || "New",
    topPrefix: root.dataset.i18nTopPrefix || "Top",
    summaryShow: root.dataset.i18nSummaryShow || "Show summary",
    summaryHide: root.dataset.i18nSummaryHide || "Show less",
    previewPlay: root.dataset.i18nPreviewPlay || "Play preview",
    previewHide: root.dataset.i18nPreviewHide || "Hide preview",
    openSource: root.dataset.i18nOpenSource || "Open source",
    previewUnavailable: root.dataset.i18nPreviewUnavailable || "No inline preview available for this source.",
    ctaVoteTitle: root.dataset.i18nCtaVoteTitle || "Vote 3 picks to personalize your feed",
    ctaVoteStart: root.dataset.i18nCtaVoteStart || "Start by voting on three picks.",
    ctaVoteProgress: root.dataset.i18nCtaVoteProgress || "{0} of 3 votes completed.",
    ctaVoteReady: root.dataset.i18nCtaVoteReady || "Personalization signal is active.",
    emptyNoVotes: root.dataset.i18nEmptyNoVotes || "You have not voted yet. Vote 3 picks to personalize your feed."
  };
  const uiLocale = document.documentElement.lang || navigator.language || undefined;

  function normalizeTopicKey(value) {
    const raw = String(value || "")
      .trim()
      .toLowerCase()
      .replace(/[-\s]/g, "_");
    if (!raw || raw === "all") {
      return "all";
    }
    if (raw === "general") {
      return "general";
    }
    if (["ai", "ml", "genai", "llm", "llmops", "data_ai"].includes(raw)) {
      return "ai";
    }
    if (
      [
        "platform_engineering",
        "platform",
        "developer_platform",
        "idp",
        "devex",
        "opensource",
        "open_source",
        "developer_experience",
        "inner_source"
      ].includes(raw)
    ) {
      return "platform_engineering";
    }
    if (["cloud_native", "devops", "sre", "kubernetes", "k8s"].includes(raw)) {
      return "cloud_native";
    }
    if (["security", "secops", "supply_chain", "security_supply_chain"].includes(raw)) {
      return "security";
    }
    return "all";
  }

  function escapeText(value) {
    return value == null ? "" : String(value);
  }

  function formatTemplate(template, value) {
    return String(template || "").replace("{0}", String(value));
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
    if (empty && authenticated && state.userVoteCount === 0) {
      emptyEl.textContent = i18n.emptyNoVotes;
    } else if (empty && state.items.length > 0 && (state.topic !== "all" || state.tag)) {
      emptyEl.textContent = i18n.emptyFiltered;
    } else {
      emptyEl.textContent = i18n.emptyGeneric;
    }
    emptyEl.classList.toggle("hidden", !empty);
  }

  function updateActivationState() {
    if (!authenticated || !activationEl) {
      return;
    }
    const votes = Math.max(0, Number(state.userVoteCount || 0));
    const capped = Math.min(3, votes);
    const isReady = votes >= 3;
    if (activationStatusEl) {
      if (isReady) {
        activationStatusEl.textContent = i18n.ctaVoteReady;
      } else if (votes <= 0) {
        activationStatusEl.textContent = i18n.ctaVoteStart;
      } else {
        activationStatusEl.textContent = formatTemplate(i18n.ctaVoteProgress, votes);
      }
    }
    if (activationProgressEl) {
      activationProgressEl.textContent = `${capped}/3`;
    }
    if (personalEmptyEl) {
      personalEmptyEl.classList.toggle("hidden", isReady || votes > 0);
    }
  }

  function updateLoadMoreState() {
    const hasMore = state.items.length < state.total;
    loadMoreBtn.classList.toggle("hidden", !hasMore || state.loading);
    loadMoreBtn.disabled = state.loading;
  }

  function cacheKey(view, filter, media, offset) {
    return `${view}|${filter}|${media}|${pageSize}|${offset}`;
  }

  function readCache(key) {
    const entry = responseCache.get(key);
    if (!entry) {
      return null;
    }
    if (Date.now() - entry.ts > cacheTtlMs) {
      responseCache.delete(key);
      return null;
    }
    return entry.payload;
  }

  function writeCache(key, payload) {
    responseCache.set(key, { ts: Date.now(), payload });
  }

  function invalidateContentCache() {
    responseCache.clear();
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

  function updateMediaState() {
    mediaButtons.forEach((btn) => {
      btn.classList.toggle("community-media-active", btn.dataset.media === state.media);
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

    if (
      /(security|secops|supply chain|zero trust|cve|vulnerability|sbom|slsa|threat|owasp|appsec|ransomware)/.test(
        text
      )
    ) {
      return "security";
    }
    if (/(\bai\b|\bml\b|llm|llmops|agent|copilot|genai|inteligencia artificial|machine learning)/.test(text)) {
      return "ai";
    }
    if (
      /(platform engineering|platform team|platform teams|developer platform|internal developer platform|idp|devex|inner source|innersource)/.test(
        text
      )
    ) {
      return "platform_engineering";
    }
    if (/(cloud native|devops|kubernetes|k8s|ci\/cd|sre|observability|terraform|helm|argo|istio|prometheus)/.test(text)) {
      return "cloud_native";
    }
    if (/(open source|opensource|oss|github|gitlab|apache foundation|linux foundation|developer workflow|engineering blog)/.test(text)) {
      return "platform_engineering";
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
      { key: "ai", label: i18n.topicAi, icon: "smart_toy" },
      { key: "platform_engineering", label: i18n.topicPlatformEngineering, icon: "layers" },
      { key: "cloud_native", label: i18n.topicCloudNative, icon: "hub" },
      { key: "security", label: i18n.topicSecurity, icon: "shield" }
    ];
  }

  function labelForTopic(topic) {
    const normalized = normalizeTopicKey(topic);
    if (normalized === "ai") {
      return i18n.topicAi;
    }
    if (normalized === "platform_engineering") {
      return i18n.topicPlatformEngineering;
    }
    if (normalized === "cloud_native") {
      return i18n.topicCloudNative;
    }
    if (normalized === "security") {
      return i18n.topicSecurity;
    }
    return "General";
  }

  function renderInterestCards(items) {
    if (!interestSectionEl || !interestListEl) {
      return;
    }
    if (ultraLiteMode) {
      interestSectionEl.classList.add("hidden");
      interestListEl.textContent = "";
      return;
    }
    interestListEl.textContent = "";

    if (!Array.isArray(items) || items.length === 0) {
      interestSectionEl.classList.add("hidden");
      return;
    }

    const counts = { ai: 0, platform_engineering: 0, cloud_native: 0, security: 0 };
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
    if (ultraLiteMode) {
      radarSectionEl.classList.add("hidden");
      radarListEl.textContent = "";
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
    if (ultraLiteMode) {
      hotSectionEl.classList.add("hidden");
      hotListEl.textContent = "";
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
    if (state.media && state.media !== "all") {
      chips.push(`${i18n.filterMediaPrefix}: ${labelForMedia(state.media)}`);
    }
    if (state.topic && state.topic !== "all") {
      chips.push(`${i18n.filterTopicPrefix}: ${labelForTopic(state.topic)}`);
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

  function normalizedMediaType(value) {
    const raw = String(value || "").toLowerCase().replace(/[-\s]/g, "_");
    if (raw === "video_story" || raw === "podcast" || raw === "article_blog") {
      return raw;
    }
    return "article_blog";
  }

  function labelForMedia(mediaType) {
    const normalized = normalizedMediaType(mediaType);
    if (normalized === "video_story") {
      return i18n.mediaVideoStory;
    }
    if (normalized === "podcast") {
      return i18n.mediaPodcast;
    }
    return i18n.mediaArticleBlog;
  }

  function toSafeUrl(value) {
    if (!value) {
      return null;
    }
    try {
      const parsed = new URL(String(value));
      if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
        return null;
      }
      return parsed.toString();
    } catch (_error) {
      return null;
    }
  }

  function mediaIcon(mediaType) {
    const normalized = normalizedMediaType(mediaType);
    if (normalized === "video_story") {
      return "play_circle";
    }
    if (normalized === "podcast") {
      return "headphones";
    }
    return "article";
  }

  function youtubeId(url) {
    const safe = toSafeUrl(url);
    if (!safe) return null;
    try {
      const parsed = new URL(safe);
      if (parsed.hostname.includes("youtu.be")) {
        const id = parsed.pathname.replace("/", "").trim();
        return id || null;
      }
      if (parsed.hostname.includes("youtube.com")) {
        const v = parsed.searchParams.get("v");
        if (v) return v;
        const parts = parsed.pathname.split("/").filter(Boolean);
        const embedIndex = parts.findIndex((entry) => entry === "embed" || entry === "shorts");
        if (embedIndex >= 0 && parts[embedIndex + 1]) {
          return parts[embedIndex + 1];
        }
      }
    } catch (_error) {
      return null;
    }
    return null;
  }

  function vimeoId(url) {
    const safe = toSafeUrl(url);
    if (!safe) return null;
    try {
      const parsed = new URL(safe);
      if (!parsed.hostname.includes("vimeo.com")) {
        return null;
      }
      const parts = parsed.pathname.split("/").filter(Boolean);
      const last = parts[parts.length - 1] || "";
      return /^\d+$/.test(last) ? last : null;
    } catch (_error) {
      return null;
    }
  }

  function spotifyParts(url) {
    const safe = toSafeUrl(url);
    if (!safe) return null;
    try {
      const parsed = new URL(safe);
      if (!parsed.hostname.includes("spotify.com")) {
        return null;
      }
      const parts = parsed.pathname.split("/").filter(Boolean);
      if (parts.length < 2) {
        return null;
      }
      const type = parts[0];
      const id = parts[1];
      if (!["episode", "show", "track"].includes(type) || !id) {
        return null;
      }
      return { type, id };
    } catch (_error) {
      return null;
    }
  }

  function isDirectAudio(url) {
    const safe = toSafeUrl(url);
    if (!safe) return false;
    return /\.(mp3|m4a|ogg|wav)(\?.*)?$/i.test(safe);
  }

  function previewDescriptor(item) {
    const mediaType = normalizedMediaType(item && item.media_type);
    const sourceUrl = toSafeUrl(item && item.url);
    if (!sourceUrl) {
      return null;
    }
    if (mediaType === "video_story") {
      const yt = youtubeId(sourceUrl);
      if (yt) {
        return {
          kind: "iframe",
          src: `https://www.youtube-nocookie.com/embed/${encodeURIComponent(yt)}?rel=0`,
          title: escapeText(item.title || "YouTube video"),
          provider: "youtube"
        };
      }
      const vimeo = vimeoId(sourceUrl);
      if (vimeo) {
        return {
          kind: "iframe",
          src: `https://player.vimeo.com/video/${encodeURIComponent(vimeo)}`,
          title: escapeText(item.title || "Vimeo video"),
          provider: "vimeo"
        };
      }
    }
    if (mediaType === "podcast") {
      const spotify = spotifyParts(sourceUrl);
      if (spotify) {
        return {
          kind: "iframe",
          src: `https://open.spotify.com/embed/${spotify.type}/${spotify.id}`,
          title: escapeText(item.title || "Spotify podcast"),
          provider: "spotify"
        };
      }
      if (isDirectAudio(sourceUrl)) {
        return {
          kind: "audio",
          src: sourceUrl,
          title: escapeText(item.title || "Audio preview")
        };
      }
    }
    return null;
  }

  function thumbnailUrlForItem(item) {
    const explicit = toSafeUrl(item && item.thumbnail_url);
    if (explicit) {
      return explicit;
    }
    const sourceUrl = toSafeUrl(item && item.url);
    const yt = sourceUrl ? youtubeId(sourceUrl) : null;
    if (yt) {
      return `https://i.ytimg.com/vi/${encodeURIComponent(yt)}/hqdefault.jpg`;
    }
    return null;
  }

  function createMediaWidget(item) {
    const widget = document.createElement("section");
    widget.className = "community-media-widget";

    const mediaType = normalizedMediaType(item && item.media_type);
    const thumbUrl = thumbnailUrlForItem(item);
    const descriptor = previewDescriptor(item);
    const canPreview = Boolean(descriptor);
    const isOpen = state.openPreviews.has(String(item.id || ""));

    const frame = document.createElement("div");
    frame.className = "community-media-frame";
    if (canPreview) {
      frame.classList.add("community-media-frame-toggleable");
      frame.dataset.itemId = String(item.id || "");
      frame.setAttribute("role", "button");
      frame.setAttribute("tabindex", "0");
      frame.setAttribute("aria-label", isOpen ? i18n.previewHide : i18n.previewPlay);
      frame.title = isOpen ? i18n.previewHide : i18n.previewPlay;
    }

    const loadThumbnail = Boolean(thumbUrl) && !ultraLiteMode;
    if (loadThumbnail) {
      const image = document.createElement("img");
      image.className = "community-media-image";
      image.loading = "lazy";
      image.decoding = "async";
      image.referrerPolicy = "no-referrer";
      image.src = thumbUrl;
      image.alt = `${labelForMedia(mediaType)} preview`;
      frame.appendChild(image);
    } else {
      const placeholder = document.createElement("div");
      placeholder.className = "community-media-placeholder";
      placeholder.innerHTML = `<span class="material-symbols-outlined">${mediaIcon(mediaType)}</span><span>${labelForMedia(mediaType)}</span>`;
      frame.appendChild(placeholder);
    }
    widget.appendChild(frame);

    const actions = document.createElement("div");
    actions.className = "community-media-actions";
    if (canPreview) {
      const playBtn = document.createElement("button");
      playBtn.type = "button";
      playBtn.className = "community-preview-toggle";
      playBtn.dataset.itemId = String(item.id || "");
      playBtn.textContent = isOpen ? i18n.previewHide : i18n.previewPlay;
      actions.appendChild(playBtn);
    } else if (mediaType === "video_story" || mediaType === "podcast") {
      const unavailable = document.createElement("span");
      unavailable.className = "community-preview-unavailable";
      unavailable.textContent = i18n.previewUnavailable;
      actions.appendChild(unavailable);
    }

    const openSource = document.createElement("a");
    openSource.className = "community-open-source-link";
    openSource.href = toSafeUrl(item && item.url) || "#";
    openSource.target = "_blank";
    openSource.rel = "noopener noreferrer";
    openSource.textContent = i18n.openSource;
    actions.appendChild(openSource);
    widget.appendChild(actions);

    if (isOpen && descriptor) {
      const preview = document.createElement("div");
      preview.className = "community-preview-panel";
      if (descriptor.kind === "iframe") {
        const iframe = document.createElement("iframe");
        iframe.loading = "lazy";
        iframe.referrerPolicy =
          descriptor.provider === "youtube"
            ? "strict-origin-when-cross-origin"
            : "no-referrer-when-downgrade";
        iframe.allow = "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share";
        iframe.allowFullscreen = true;
        iframe.src = descriptor.src;
        iframe.title = descriptor.title || "Embedded preview";
        preview.appendChild(iframe);
      } else if (descriptor.kind === "audio") {
        const audio = document.createElement("audio");
        audio.controls = true;
        audio.preload = "none";
        audio.src = descriptor.src;
        preview.appendChild(audio);
      }
      widget.appendChild(preview);
    }

    return widget;
  }

  function findCardByItemId(itemId) {
    const cards = listEl.querySelectorAll(".community-item-card");
    for (const card of cards) {
      if (card.dataset.itemId === String(itemId)) {
        return card;
      }
    }
    return null;
  }

  function updateVoteButtonsForItem(itemId) {
    const item = state.items.find((entry) => String(entry.id) === String(itemId));
    if (!item) {
      return;
    }
    const card = findCardByItemId(itemId);
    if (!card) {
      return;
    }
    const counts = item.vote_counts || {};
    const labels = {
      recommended: i18n.voteRecommended,
      must_see: i18n.voteMustSee,
      not_for_me: i18n.voteNotForMe
    };
    card.querySelectorAll(".community-vote-btn").forEach((btn) => {
      const key = btn.dataset.vote;
      if (!key || !labels[key]) {
        return;
      }
      btn.classList.toggle("active", item.my_vote === key);
      if (!authenticated) {
        btn.disabled = true;
        btn.title = i18n.voteLoginRequired;
      } else {
        btn.disabled = false;
        btn.removeAttribute("title");
      }
      btn.textContent = `${labels[key]} (${Number(counts[key] || 0)})`;
    });
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
      topicHint.innerHTML = `<span class="community-topic-dot ${topic}"></span>${escapeText(labelForTopic(topic))}`;
      const mediaHint = document.createElement("span");
      mediaHint.className = "community-media-hint";
      mediaHint.textContent = labelForMedia(item.media_type);
      topicHint.appendChild(mediaHint);
      card.appendChild(topicHint);

      card.appendChild(createMediaWidget(item));

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

  function applyResponse(data, reset) {
    const received = Array.isArray(data && data.items) ? data.items : [];
    state.userVoteCount = Number((data && data.user_vote_count) || 0);
    if (reset) {
      state.items = received;
      state.offset = received.length;
      state.openPreviews.clear();
    } else {
      state.items = state.items.concat(received);
      state.offset = state.items.length;
    }
    state.total = Number((data && data.total) || state.items.length);
    if (reset && state.items.length > 0 && visibleItems().length === 0 && (state.tag || state.topic !== "all")) {
      state.topic = "all";
      state.tag = "";
      sessionStorage.setItem("community.topic", "all");
      sessionStorage.removeItem("community.tag");
      updateTopicState();
    }
    renderItems();
    updateActivationState();
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
    if (state.loading && !reset) return;
    const requestToken = ++state.requestToken;
    state.loading = true;
    hideFeedback();
    const requestOffset = reset ? 0 : state.offset;
    const key = cacheKey(state.view, state.filter, state.media, requestOffset);
    showSkeleton(state.items.length === 0 && requestOffset === 0);
    updateLoadMoreState();

    if (reset && requestOffset === 0) {
      const cached = readCache(key);
      if (cached) {
        applyResponse(cached, true);
        state.loading = false;
        showSkeleton(false);
        updateEmptyState(visibleItems().length);
        updateLoadMoreState();
        return;
      }
    }

    try {
      const response = await fetch(
        `/api/community/content?view=${encodeURIComponent(state.view)}&filter=${encodeURIComponent(state.filter)}&media=${encodeURIComponent(state.media)}&limit=${encodeURIComponent(pageSize)}&offset=${encodeURIComponent(requestOffset)}`,
        { headers: { Accept: "application/json" } }
      );
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const data = await response.json();
      if (requestToken !== state.requestToken) {
        return;
      }
      writeCache(key, data);
      applyResponse(data, reset);
    } catch (error) {
      if (requestToken !== state.requestToken) {
        return;
      }
      showFeedback(i18n.loadError);
      updateEmptyState(0);
    } finally {
      if (requestToken === state.requestToken) {
        state.loading = false;
      }
      showSkeleton(false);
      updateEmptyState(visibleItems().length);
      updateLoadMoreState();
    }
  }

  async function sendVote(itemId, vote) {
    if (!authenticated) {
      const redirectTarget = `${window.location.pathname || "/"}${window.location.search || ""}${window.location.hash || ""}`;
      if (typeof window.buildLoginCallbackUrl === "function") {
        window.location.href = window.buildLoginCallbackUrl(redirectTarget);
      } else {
        window.location.href = `/private/login-callback?redirect=${encodeURIComponent(redirectTarget)}`;
      }
      return;
    }
    const item = state.items.find((entry) => String(entry.id) === String(itemId));
    if (!item) return;

    const snapshot = {
      my_vote: item.my_vote || null,
      userVoteCount: Number(state.userVoteCount || 0),
      vote_counts: Object.assign(
        { recommended: 0, must_see: 0, not_for_me: 0 },
        item.vote_counts || {}
      )
    };
    applyVoteLocally(item, vote);
    updateVoteButtonsForItem(itemId);

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
        const idx = state.items.findIndex((entry) => String(entry.id) === String(itemId));
        if (idx >= 0) {
          state.items[idx] = data.item;
        }
      }
      if (!snapshot.my_vote) {
        state.userVoteCount = Number(state.userVoteCount || 0) + 1;
      }
      invalidateContentCache();
      updateVoteButtonsForItem(itemId);
      updateActivationState();
    } catch (error) {
      const idx = state.items.findIndex((entry) => String(entry.id) === String(itemId));
      if (idx >= 0) {
        state.items[idx].my_vote = snapshot.my_vote;
        state.items[idx].vote_counts = snapshot.vote_counts;
      }
      state.userVoteCount = snapshot.userVoteCount;
      updateVoteButtonsForItem(itemId);
      updateActivationState();
      showFeedback(i18n.voteError);
    }
  }

  loadMoreBtn.addEventListener("click", () => load(false));

  function setTopic(nextTopic) {
    const normalizedTopic = normalizeTopicKey(nextTopic);
    if (!normalizedTopic || normalizedTopic === state.topic) {
      return;
    }
    state.topic = normalizedTopic;
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
    const shouldReload = state.filter !== "all" || state.media !== "all";
    const changed = shouldReload || state.topic !== "all" || Boolean(state.tag);
    if (!changed) {
      return;
    }
    state.filter = "all";
    state.media = "all";
    state.topic = "all";
    state.tag = "";
    sessionStorage.setItem("community.topic", "all");
    sessionStorage.removeItem("community.tag");
    updateFilterState();
    updateMediaState();
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

  function togglePreview(itemId) {
    const key = String(itemId || "");
    if (!key) return;
    if (state.openPreviews.has(key)) {
      state.openPreviews.delete(key);
    } else {
      state.openPreviews.clear();
      state.openPreviews.add(key);
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

  mediaButtons.forEach((btn) => {
    btn.addEventListener("click", () => {
      const nextMedia = String(btn.dataset.media || "all").toLowerCase();
      if (nextMedia === state.media) {
        return;
      }
      if (nextMedia !== "all" && nextMedia !== "video_story" && nextMedia !== "podcast" && nextMedia !== "article_blog") {
        return;
      }
      state.media = nextMedia;
      updateMediaState();
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
    const mediaFrame = target.closest(".community-media-frame[data-item-id]");
    if (mediaFrame) {
      togglePreview(mediaFrame.dataset.itemId || "");
      return;
    }
    const previewToggle = target.closest(".community-preview-toggle");
    if (previewToggle) {
      togglePreview(previewToggle.dataset.itemId || "");
      return;
    }
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

  listEl.addEventListener("keydown", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    if (event.key !== "Enter" && event.key !== " ") return;
    const mediaFrame = target.closest(".community-media-frame[data-item-id]");
    if (!mediaFrame) return;
    event.preventDefault();
    togglePreview(mediaFrame.dataset.itemId || "");
  });

  updateViewState();
  updateFilterState();
  updateMediaState();
  state.topic = normalizeTopicKey(sessionStorage.getItem("community.topic") || "all");
  updateTopicState();
  updateActivationState();
  load(true);
})();
(function () {
  const root = document.getElementById("community-submissions-root");
  if (!root) {
    return;
  }

  const authenticated = String(root.dataset.authenticated || "false") === "true";
  const isAdmin = String(root.dataset.admin || "false") === "true";
  const activeSubmenu = String(root.dataset.activeSubmenu || "");
  if (!authenticated) {
    return;
  }

  const form = document.getElementById("community-submission-form");
  const submitBtn = document.getElementById("community-submit-btn");
  const listEl = document.getElementById("community-submissions-list");
  const emptyEl = document.getElementById("community-submissions-empty");
  const feedbackEl = document.getElementById("community-submission-feedback");
  const moderationListEl = document.getElementById("community-moderation-list");
  const moderationEmptyEl = document.getElementById("community-moderation-empty");
  const moderationPanel = document.getElementById("community-moderation-panel");

  if (!feedbackEl) {
    return;
  }
  const i18n = {
    statusPending: root.dataset.i18nStatusPending || "Pending",
    statusApproved: root.dataset.i18nStatusApproved || "Approved",
    statusRejected: root.dataset.i18nStatusRejected || "Rejected",
    sourceFallback: root.dataset.i18nSourceFallback || "Community member",
    openSource: root.dataset.i18nOpenSource || "Open source",
    notePlaceholder: root.dataset.i18nNotePlaceholder || "Optional moderation note",
    approve: root.dataset.i18nApprove || "Approve",
    reject: root.dataset.i18nReject || "Reject",
    feedbackApproved: root.dataset.i18nFeedbackApproved || "Proposal approved and published.",
    feedbackRejected: root.dataset.i18nFeedbackRejected || "Proposal rejected.",
    errorModerate: root.dataset.i18nErrorModerate || "Could not moderate this proposal.",
    errorLoadMine: root.dataset.i18nErrorLoadMine || "Could not load your submissions.",
    errorLoadModeration: root.dataset.i18nErrorLoadModeration || "Could not load moderation queue.",
    errorAdminOnly: root.dataset.i18nErrorAdminOnly || "Only admins can moderate proposals.",
    errorNotFound: root.dataset.i18nErrorNotFound || "The proposal no longer exists.",
    errorConflict: root.dataset.i18nErrorConflict || "This URL already exists in the curated feed.",
    errorUnavailable: root.dataset.i18nErrorUnavailable || "Could not publish content right now. Please try again in a few minutes.",
    errorGenericModeration: root.dataset.i18nErrorGenericModeration || "Could not process moderation.",
    errorDailyLimit: root.dataset.i18nErrorDailyLimit || "You reached the daily proposal limit.",
    errorDuplicateUrl: root.dataset.i18nErrorDuplicateUrl || "A proposal already exists for that URL.",
    errorInvalidDataPrefix: root.dataset.i18nErrorInvalidDataPrefix || "Invalid data",
    errorSubmit: root.dataset.i18nErrorSubmit || "Could not send your proposal.",
    feedbackSubmitted: root.dataset.i18nFeedbackSubmitted || "Proposal sent. It is now pending review."
  };
  const uiLocale = document.documentElement.lang || navigator.language || undefined;

  function showFeedback(message) {
    if (!feedbackEl) {
      return;
    }
    feedbackEl.textContent = message;
    feedbackEl.classList.remove("hidden");
  }

  function hideFeedback() {
    if (!feedbackEl) {
      return;
    }
    feedbackEl.textContent = "";
    feedbackEl.classList.add("hidden");
  }

  function escapeText(value) {
    return value == null ? "" : String(value);
  }

  function statusLabel(status) {
    switch (status) {
      case "approved":
        return i18n.statusApproved;
      case "rejected":
        return i18n.statusRejected;
      default:
        return i18n.statusPending;
    }
  }

  function formatDate(raw) {
    if (!raw) return "";
    const parsed = new Date(raw);
    if (Number.isNaN(parsed.getTime())) return "";
    return parsed.toLocaleDateString(uiLocale, { year: "numeric", month: "short", day: "numeric" });
  }

  function renderList(items) {
    if (!listEl || !emptyEl) {
      return;
    }
    listEl.textContent = "";
    if (!Array.isArray(items) || items.length === 0) {
      emptyEl.classList.remove("hidden");
      return;
    }
    emptyEl.classList.add("hidden");
    items.forEach((item) => {
      const card = document.createElement("article");
      card.className = "community-submission-item";

      const title = document.createElement("h4");
      title.className = "community-submission-title";
      title.textContent = escapeText(item.title);
      card.appendChild(title);

      const meta = document.createElement("p");
      meta.className = "community-submission-meta";
      meta.textContent = `${escapeText(item.source || i18n.sourceFallback)} · ${formatDate(item.created_at)}`;
      card.appendChild(meta);

      const status = document.createElement("span");
      const normalizedStatus = escapeText(item.status || "pending").toLowerCase();
      status.className = `community-submission-status ${normalizedStatus}`;
      status.textContent = statusLabel(normalizedStatus);
      card.appendChild(status);

      listEl.appendChild(card);
    });
  }

  function renderModerationList(items) {
    if (!isAdmin || !moderationListEl || !moderationEmptyEl) {
      return;
    }
    const pendingItems = Array.isArray(items)
      ? items.filter((item) => {
          const id = String(item && item.id ? item.id : "");
          const status = String(item && item.status ? item.status : "pending").toLowerCase();
          return Boolean(id) && status === "pending";
        })
      : [];
    moderationListEl.textContent = "";
    if (pendingItems.length === 0) {
      moderationEmptyEl.classList.remove("hidden");
      return;
    }
    moderationEmptyEl.classList.add("hidden");
    pendingItems.forEach((item) => {
      const card = document.createElement("article");
      card.className = "community-submission-item";

      const title = document.createElement("h4");
      title.className = "community-submission-title";
      title.textContent = escapeText(item.title);
      card.appendChild(title);

      const meta = document.createElement("p");
      meta.className = "community-submission-meta";
      meta.textContent = `${escapeText(item.source || i18n.sourceFallback)} · ${formatDate(item.created_at)}`;
      card.appendChild(meta);

      const summary = document.createElement("p");
      summary.className = "community-submission-meta";
      summary.textContent = escapeText(item.summary);
      card.appendChild(summary);

      const link = document.createElement("a");
      link.href = item.url;
      link.target = "_blank";
      link.rel = "noopener noreferrer";
      link.textContent = i18n.openSource;
      card.appendChild(link);

      const note = document.createElement("textarea");
      note.className = "community-moderation-note";
      note.placeholder = i18n.notePlaceholder;
      card.appendChild(note);

      const actions = document.createElement("div");
      actions.className = "community-moderation-actions";
      const approveBtn = document.createElement("button");
      approveBtn.type = "button";
      approveBtn.className = "btn-primary";
      approveBtn.textContent = i18n.approve;
      approveBtn.dataset.action = "approve";
      approveBtn.dataset.id = item.id;

      const rejectBtn = document.createElement("button");
      rejectBtn.type = "button";
      rejectBtn.className = "btn-primary";
      rejectBtn.textContent = i18n.reject;
      rejectBtn.dataset.action = "reject";
      rejectBtn.dataset.id = item.id;

      actions.appendChild(approveBtn);
      actions.appendChild(rejectBtn);
      card.appendChild(actions);

      actions.addEventListener("click", async (event) => {
        const target = event.target;
        if (!(target instanceof HTMLButtonElement)) {
          return;
        }
        const action = target.dataset.action;
        const submissionId = target.dataset.id;
        if (!action || !submissionId) {
          return;
        }
        approveBtn.disabled = true;
        rejectBtn.disabled = true;
        try {
          hideFeedback();
          await moderateSubmission(submissionId, action, note.value.trim());
          card.remove();
          if (moderationListEl.children.length === 0) {
            moderationEmptyEl.classList.remove("hidden");
          }
          await Promise.all([loadMine(), loadPending()]);
          showFeedback(
            action === "approve"
              ? i18n.feedbackApproved
              : i18n.feedbackRejected
          );
        } catch (error) {
          showFeedback(error instanceof Error ? error.message : i18n.errorModerate);
        } finally {
          approveBtn.disabled = false;
          rejectBtn.disabled = false;
        }
      });

      moderationListEl.appendChild(card);
    });
  }

  async function loadMine() {
    if (!listEl || !emptyEl) {
      return;
    }
    try {
      const response = await fetch("/api/community/submissions/mine?limit=20&offset=0", {
        headers: { Accept: "application/json" }
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const data = await response.json();
      renderList(Array.isArray(data.items) ? data.items : []);
    } catch (error) {
      showFeedback(i18n.errorLoadMine);
      renderList([]);
    }
  }

  async function loadPending() {
    if (!isAdmin || !moderationListEl || !moderationEmptyEl) {
      return;
    }
    try {
      const response = await fetch("/api/community/submissions/pending?limit=50&offset=0", {
        headers: { Accept: "application/json" }
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const data = await response.json();
      renderModerationList(Array.isArray(data.items) ? data.items : []);
    } catch (error) {
      showFeedback(i18n.errorLoadModeration);
      renderModerationList([]);
    }
  }

  async function moderateSubmission(id, action, note) {
    const response = await fetch(`/api/community/submissions/${encodeURIComponent(id)}/${action}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json"
      },
      body: JSON.stringify({ note: note || null })
    });
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      const errorCode = String(data.error || "");
      const detail = String(data.detail || "");
      if (response.status === 403) {
        throw new Error(i18n.errorAdminOnly);
      }
      if (response.status === 404) {
        throw new Error(i18n.errorNotFound);
      }
      if (response.status === 409) {
        throw new Error(i18n.errorConflict);
      }
      if (response.status === 503 || errorCode === "approve_storage_unavailable") {
        const suffix = detail ? ` (${detail})` : "";
        throw new Error(`${i18n.errorUnavailable}${suffix}`);
      }
      throw new Error(errorCode || i18n.errorGenericModeration);
    }
  }

  function parseTags(raw) {
    if (!raw) {
      return [];
    }
    const parts = String(raw)
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean);
    return Array.from(new Set(parts)).slice(0, 8);
  }

  if (form && submitBtn) {
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      hideFeedback();
      submitBtn.disabled = true;

      const formData = new FormData(form);
      const payload = {
        title: String(formData.get("title") || "").trim(),
        url: String(formData.get("url") || "").trim(),
        summary: String(formData.get("summary") || "").trim(),
        source: String(formData.get("source") || "").trim() || null,
        tags: parseTags(formData.get("tags"))
      };

      try {
        const response = await fetch("/api/community/submissions", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Accept: "application/json"
          },
          body: JSON.stringify(payload)
        });
        if (!response.ok) {
          const data = await response.json().catch(() => ({}));
          const errorCode = String(data.error || "");
          if (response.status === 429) {
            throw new Error(i18n.errorDailyLimit);
          }
          if (response.status === 409) {
            throw new Error(i18n.errorDuplicateUrl);
          }
          if (response.status === 400 && errorCode) {
            throw new Error(`${i18n.errorInvalidDataPrefix}: ${errorCode}`);
          }
          throw new Error(i18n.errorSubmit);
        }
        form.reset();
        showFeedback(i18n.feedbackSubmitted);
        await Promise.all([loadMine(), loadPending()]);
      } catch (error) {
        showFeedback(error instanceof Error ? error.message : i18n.errorSubmit);
      } finally {
        submitBtn.disabled = false;
      }
    });
  }

  Promise.all([loadMine(), loadPending()]).then(() => {
    if (isAdmin && activeSubmenu === "moderation" && moderationPanel) {
      moderationPanel.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  });
})();
