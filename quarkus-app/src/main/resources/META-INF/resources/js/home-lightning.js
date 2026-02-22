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

  function updateCount() {
    if (!statementInput || !countEl) {
      return;
    }
    const current = String(statementInput.value || "").length;
    countEl.textContent = `${current}/100`;
  }

  function commentMarkup(comment, isBest) {
    const cls = isBest ? "home-lightning-comment best" : "home-lightning-comment";
    return `
      <div class="${cls}" id="hl-comment-${escapeText(comment.id)}">
        <div class="home-lightning-comment-head">
          <strong>${escapeText(comment.user_name || "member")}</strong>
          <span>${formatDate(comment.created_at)}</span>
        </div>
        <p>${escapeText(comment.body)}</p>
        <div class="home-lightning-comment-actions">
          <button type="button" class="home-lightning-mini-btn" data-action="like-comment" data-id="${escapeText(comment.id)}">${i18n.like} · ${Number(comment.likes || 0)}</button>
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
          <small>${formatDate(thread.published_at || thread.created_at)}</small>
        </div>
        <h3>${escapeText(title || body)}</h3>
        ${showBody ? `<p>${escapeText(body)}</p>` : ""}
        ${bestBlock}
        <div class="home-lightning-thread-actions">
          <button type="button" class="home-lightning-mini-btn" data-action="like-thread" data-id="${escapeText(thread.id)}">${i18n.like} · ${Number(thread.likes || 0)}</button>
          <button type="button" class="home-lightning-mini-btn" data-action="comment-thread" data-id="${escapeText(thread.id)}">${i18n.comment}</button>
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

  async function mutate(url, body, successMessage, duplicateMessage) {
    const response = await fetch(url, {
      method: "POST",
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
