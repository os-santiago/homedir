(function () {
  const root = document.getElementById("community-submissions-root");
  if (!root) {
    return;
  }

  const authenticated = String(root.dataset.authenticated || "false") === "true";
  if (!authenticated) {
    return;
  }

  const form = document.getElementById("community-submission-form");
  const submitBtn = document.getElementById("community-submit-btn");
  const listEl = document.getElementById("community-submissions-list");
  const emptyEl = document.getElementById("community-submissions-empty");
  const feedbackEl = document.getElementById("community-submission-feedback");

  if (!form || !submitBtn || !listEl || !emptyEl || !feedbackEl) {
    return;
  }

  function showFeedback(message) {
    feedbackEl.textContent = message;
    feedbackEl.classList.remove("hidden");
  }

  function hideFeedback() {
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

  async function loadMine() {
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
      await loadMine();
    } catch (error) {
      showFeedback(error instanceof Error ? error.message : "No se pudo enviar la propuesta.");
    } finally {
      submitBtn.disabled = false;
    }
  });

  loadMine();
})();
