(function () {
  if (window.__communityBoardEnhancerInit) {
    return;
  }
  window.__communityBoardEnhancerInit = true;

  const BOARD_PATH_PREFIX = "/comunidad/board";
  let inFlightController = null;

  function isLeftClick(event) {
    return event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey;
  }

  function isBoardPath(pathname) {
    return typeof pathname === "string" && pathname.startsWith(BOARD_PATH_PREFIX);
  }

  function setLoading(active) {
    if (!document.body) {
      return;
    }
    document.body.classList.toggle("community-board-loading", active);
  }

  function replaceMainContentFromDocument(nextDocument) {
    const nextMain = nextDocument.querySelector("#main-content");
    const currentMain = document.querySelector("#main-content");
    if (!nextMain || !currentMain) {
      return false;
    }
    currentMain.className = nextMain.className;
    currentMain.innerHTML = nextMain.innerHTML;
    if (nextDocument.body && typeof nextDocument.body.className === "string") {
      document.body.className = nextDocument.body.className;
    }
    if (nextDocument.title) {
      document.title = nextDocument.title;
    }
    return true;
  }

  async function fetchAndSwap(url, pushState) {
    if (inFlightController) {
      inFlightController.abort();
    }
    const controller = new AbortController();
    inFlightController = controller;
    setLoading(true);

    try {
      const response = await fetch(url, {
        method: "GET",
        headers: {
          Accept: "text/html",
          "X-Requested-With": "community-board-fetch"
        },
        credentials: "same-origin",
        cache: "no-store",
        signal: controller.signal
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const html = await response.text();
      if (controller.signal.aborted) {
        return;
      }
      const parser = new DOMParser();
      const nextDocument = parser.parseFromString(html, "text/html");
      const replaced = replaceMainContentFromDocument(nextDocument);
      if (!replaced) {
        window.location.assign(url);
        return;
      }
      if (pushState) {
        window.history.pushState({ boardNav: true }, "", url);
      }
      const title = document.querySelector(".page-header h1");
      if (title instanceof HTMLElement) {
        title.scrollIntoView({ block: "start", behavior: "auto" });
      }
    } catch (error) {
      if (!controller.signal.aborted) {
        window.location.assign(url);
      }
    } finally {
      if (inFlightController === controller) {
        inFlightController = null;
      }
      setLoading(false);
    }
  }

  function buildSearchUrl(form) {
    const action = form.getAttribute("action") || window.location.pathname;
    const url = new URL(action, window.location.origin);
    const queryInput = form.querySelector("input[name='q']");
    const limitInput = form.querySelector("input[name='limit']");
    const query = queryInput && "value" in queryInput ? String(queryInput.value || "").trim() : "";
    const limit = limitInput && "value" in limitInput ? String(limitInput.value || "").trim() : "";

    if (limit) {
      url.searchParams.set("limit", limit);
    }
    url.searchParams.set("offset", "0");
    if (query) {
      url.searchParams.set("q", query);
    } else {
      url.searchParams.delete("q");
    }
    return url.toString();
  }

  function shouldInterceptLink(anchor, event) {
    if (!(anchor instanceof HTMLAnchorElement)) {
      return false;
    }
    if (!isLeftClick(event)) {
      return false;
    }
    if (anchor.target && anchor.target !== "_self") {
      return false;
    }
    if (anchor.hasAttribute("download")) {
      return false;
    }
    const href = anchor.getAttribute("href");
    if (!href || href.startsWith("#")) {
      return false;
    }
    const url = new URL(href, window.location.origin);
    if (url.origin !== window.location.origin || !isBoardPath(url.pathname)) {
      return false;
    }
    const inPagination = !!anchor.closest(".board-pagination");
    const inBoardSummary = !!anchor.closest(".board-summary-grid");
    const inCommunitySubmenu = !!anchor.closest(".community-submenu");
    return inPagination || inBoardSummary || inCommunitySubmenu;
  }

  document.addEventListener(
    "submit",
    (event) => {
      const target = event.target;
      if (!(target instanceof HTMLElement)) {
        return;
      }
      const form = target.closest(".board-search-form");
      if (!(form instanceof HTMLFormElement)) {
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      const url = buildSearchUrl(form);
      if (isBoardPath(new URL(url).pathname)) {
        fetchAndSwap(url, true);
      } else {
        window.location.assign(url);
      }
    },
    true
  );

  document.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) {
      return;
    }
    const copyButton = target.closest(".board-copy-btn");
    if (copyButton instanceof HTMLButtonElement) {
      const rawLink = copyButton.getAttribute("data-share-link");
      if (!rawLink) {
        return;
      }
      const fullLink = new URL(rawLink, window.location.origin).toString();
      navigator.clipboard
        .writeText(fullLink)
        .then(() => {
          copyButton.classList.add("is-done");
          copyButton.textContent = copyButton.getAttribute("data-success-label") || "OK";
          window.setTimeout(() => {
            copyButton.classList.remove("is-done");
            copyButton.textContent = copyButton.getAttribute("data-default-label") || "Copy";
          }, 1200);
        })
        .catch(() => {
          copyButton.textContent = copyButton.getAttribute("data-error-label") || "Error";
          window.setTimeout(() => {
            copyButton.textContent = copyButton.getAttribute("data-default-label") || "Copy";
          }, 1200);
        });
      return;
    }

    const anchor = target.closest("a");
    if (!shouldInterceptLink(anchor, event)) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    const href = anchor.getAttribute("href");
    if (!href) {
      return;
    }
    const url = new URL(href, window.location.origin).toString();
    fetchAndSwap(url, true);
  });

  window.addEventListener("popstate", () => {
    if (isBoardPath(window.location.pathname)) {
      fetchAndSwap(window.location.href, false);
    }
  });
})();
