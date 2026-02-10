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
