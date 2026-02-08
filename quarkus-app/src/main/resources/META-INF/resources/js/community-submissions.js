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
        return "Aprobada";
      case "rejected":
        return "Rechazada";
      default:
        return "Pendiente";
    }
  }

  function formatDate(raw) {
    if (!raw) return "";
    const parsed = new Date(raw);
    if (Number.isNaN(parsed.getTime())) return "";
    return parsed.toLocaleDateString("es-CL", { year: "numeric", month: "short", day: "numeric" });
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
      meta.textContent = `${escapeText(item.source || "Community member")} · ${formatDate(item.created_at)}`;
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
      meta.textContent = `${escapeText(item.source || "Community member")} · ${formatDate(item.created_at)}`;
      card.appendChild(meta);

      const summary = document.createElement("p");
      summary.className = "community-submission-meta";
      summary.textContent = escapeText(item.summary);
      card.appendChild(summary);

      const link = document.createElement("a");
      link.href = item.url;
      link.target = "_blank";
      link.rel = "noopener noreferrer";
      link.textContent = "Abrir fuente";
      card.appendChild(link);

      const note = document.createElement("textarea");
      note.className = "community-moderation-note";
      note.placeholder = "Nota opcional de moderación";
      card.appendChild(note);

      const actions = document.createElement("div");
      actions.className = "community-moderation-actions";
      const approveBtn = document.createElement("button");
      approveBtn.type = "button";
      approveBtn.className = "btn-primary";
      approveBtn.textContent = "Approve";
      approveBtn.dataset.action = "approve";
      approveBtn.dataset.id = item.id;

      const rejectBtn = document.createElement("button");
      rejectBtn.type = "button";
      rejectBtn.className = "btn-primary";
      rejectBtn.textContent = "Reject";
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
              ? "Propuesta aprobada y publicada."
              : "Propuesta rechazada.");
        } catch (error) {
          showFeedback(error instanceof Error ? error.message : "No se pudo moderar la propuesta.");
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
      showFeedback("No se pudo cargar tus propuestas.");
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
      showFeedback("No se pudo cargar la cola de moderación.");
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
      if (response.status === 403) {
        throw new Error("Solo admins pueden moderar propuestas.");
      }
      if (response.status === 404) {
        throw new Error("La propuesta ya no existe.");
      }
      if (response.status === 409) {
        throw new Error("La URL ya existe en el feed curado.");
      }
      if (response.status === 503 || errorCode === "approve_storage_unavailable") {
        throw new Error("No fue posible publicar el contenido en este momento. Reintenta en unos minutos.");
      }
      throw new Error(errorCode || "No se pudo procesar la moderación.");
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
            throw new Error("Llegaste al límite diario de propuestas.");
          }
          if (response.status === 409) {
            throw new Error("Ya existe una propuesta para esa URL.");
          }
          if (response.status === 400 && errorCode) {
            throw new Error(`Datos inválidos: ${errorCode}`);
          }
          throw new Error("No se pudo enviar la propuesta.");
        }
        form.reset();
        showFeedback("Propuesta enviada. Quedó pendiente de revisión.");
        await Promise.all([loadMine(), loadPending()]);
      } catch (error) {
        showFeedback(error instanceof Error ? error.message : "No se pudo enviar la propuesta.");
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
