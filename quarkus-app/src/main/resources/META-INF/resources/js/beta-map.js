(function () {
  const root = document.getElementById("beta-map-root");
  if (!root) {
    return;
  }

  const canvas = document.getElementById("beta-map-canvas");
  const selectedNode = document.getElementById("beta-selected-zone");
  const visitedNode = document.getElementById("beta-visited-zones");
  const openBtn = document.getElementById("beta-open-zone");
  const resetBtn = document.getElementById("beta-reset-progress");
  const zoneCards = Array.from(root.querySelectorAll(".beta-zone-card"));
  if (!canvas || !selectedNode || !visitedNode || !openBtn || zoneCards.length === 0) {
    return;
  }

  const ctx = canvas.getContext("2d", { alpha: false });
  if (!ctx) {
    return;
  }

  const storageKey = "homedir.beta.map.v1.visited";
  const trackUrl = root.dataset.trackUrl || "/api/beta/interaction";
  const i18nOpenFirst = root.dataset.i18nOpenFirst || "Select a zone first.";
  const i18nSelectedPrefix = root.dataset.i18nSelectedPrefix || "Selected zone: {0}";
  const i18nSelectedNone = root.dataset.i18nSelectedNone || "No zone selected yet.";
  const i18nVisitedPrefix = root.dataset.i18nVisitedPrefix || "Visited zones: {0}/4";
  const i18nOpenButton = root.dataset.i18nOpenButton || "Open selected zone";

  const zones = zoneCards.map((card) => ({
    id: card.dataset.zone || "",
    tileX: Number(card.dataset.tileX || "0"),
    tileY: Number(card.dataset.tileY || "0"),
    href: card.getAttribute("href") || "/",
    label: (card.querySelector("strong") || card).textContent.trim(),
    card
  }));

  const state = {
    mapSize: 9,
    tileW: 64,
    tileH: 32,
    avatarX: 4,
    avatarY: 4,
    selectedZoneId: null,
    visited: loadVisited()
  };

  function loadVisited() {
    try {
      const raw = window.localStorage.getItem(storageKey);
      if (!raw) {
        return new Set();
      }
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) {
        return new Set();
      }
      return new Set(parsed.filter((v) => typeof v === "string"));
    } catch (err) {
      return new Set();
    }
  }

  function saveVisited() {
    try {
      window.localStorage.setItem(storageKey, JSON.stringify(Array.from(state.visited)));
    } catch (err) {
      // no-op
    }
  }

  function isoToScreen(x, y) {
    const originX = canvas.width / 2;
    const originY = Math.round(canvas.height * 0.18);
    return {
      x: originX + (x - y) * (state.tileW / 2),
      y: originY + (x + y) * (state.tileH / 2)
    };
  }

  function drawDiamond(cx, cy, fill, stroke) {
    const hw = state.tileW / 2;
    const hh = state.tileH / 2;
    ctx.beginPath();
    ctx.moveTo(cx, cy - hh);
    ctx.lineTo(cx + hw, cy);
    ctx.lineTo(cx, cy + hh);
    ctx.lineTo(cx - hw, cy);
    ctx.closePath();
    ctx.fillStyle = fill;
    ctx.fill();
    if (stroke) {
      ctx.strokeStyle = stroke;
      ctx.lineWidth = 1;
      ctx.stroke();
    }
  }

  function drawMap() {
    ctx.fillStyle = "#091423";
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    for (let y = 0; y < state.mapSize; y += 1) {
      for (let x = 0; x < state.mapSize; x += 1) {
        const p = isoToScreen(x, y);
        const alt = (x + y) % 2 === 0;
        drawDiamond(
          p.x,
          p.y,
          alt ? "rgba(34, 62, 94, 0.72)" : "rgba(27, 49, 76, 0.72)",
          "rgba(80, 140, 200, 0.16)"
        );
      }
    }

    zones.forEach((zone) => {
      const p = isoToScreen(zone.tileX, zone.tileY);
      const isSelected = state.selectedZoneId === zone.id;
      const isVisited = state.visited.has(zone.id);
      drawDiamond(
        p.x,
        p.y,
        isSelected
          ? "rgba(255, 200, 84, 0.62)"
          : isVisited
            ? "rgba(82, 196, 129, 0.55)"
            : "rgba(117, 190, 255, 0.5)",
        isSelected ? "rgba(255, 220, 128, 0.95)" : "rgba(117, 190, 255, 0.58)"
      );

      ctx.fillStyle = "rgba(12, 21, 36, 0.96)";
      ctx.fillRect(p.x - 42, p.y - 52, 84, 18);
      ctx.fillStyle = "#ebf4ff";
      ctx.font = "12px 'Exo 2', sans-serif";
      ctx.textAlign = "center";
      ctx.fillText(zone.label, p.x, p.y - 38);
    });

    const avatar = isoToScreen(state.avatarX, state.avatarY);
    drawDiamond(avatar.x, avatar.y - 8, "rgba(255, 96, 101, 0.9)", "rgba(255, 192, 194, 0.95)");
    ctx.fillStyle = "rgba(255, 246, 250, 0.95)";
    ctx.beginPath();
    ctx.arc(avatar.x, avatar.y - 22, 5, 0, Math.PI * 2);
    ctx.fill();
  }

  function setSelectedZone(zone) {
    state.selectedZoneId = zone ? zone.id : null;
    updateUi();
    drawMap();
  }

  function visitZone(zone, trackEvent) {
    if (!zone) {
      return;
    }
    state.visited.add(zone.id);
    saveVisited();
    setSelectedZone(zone);
    if (trackEvent) {
      track("visit", zone.id);
    }
  }

  function updateUi() {
    zones.forEach((zone) => {
      const selected = zone.id === state.selectedZoneId;
      zone.card.classList.toggle("is-selected", selected);
      zone.card.classList.toggle("is-visited", state.visited.has(zone.id));
    });

    const selectedZone = zones.find((z) => z.id === state.selectedZoneId);
    selectedNode.textContent = selectedZone
      ? i18nSelectedPrefix.replace("{0}", selectedZone.label)
      : i18nSelectedNone;
    visitedNode.textContent = i18nVisitedPrefix.replace("{0}", String(state.visited.size));
    openBtn.textContent = selectedZone
      ? i18nOpenButton + ": " + selectedZone.label
      : i18nOpenButton;
  }

  function track(eventName, zoneId) {
    const body = JSON.stringify({ event: eventName, zone: zoneId });
    if (navigator.sendBeacon) {
      const blob = new Blob([body], { type: "application/json" });
      navigator.sendBeacon(trackUrl, blob);
      return;
    }
    fetch(trackUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body,
      keepalive: true
    }).catch(() => {});
  }

  function openSelectedZone() {
    const selectedZone = zones.find((z) => z.id === state.selectedZoneId);
    if (!selectedZone) {
      window.alert(i18nOpenFirst);
      return;
    }
    track("open", selectedZone.id);
    window.location.href = selectedZone.href;
  }

  function nearestZoneAt(px, py) {
    let nearest = null;
    let nearestDistance = Number.POSITIVE_INFINITY;
    zones.forEach((zone) => {
      const p = isoToScreen(zone.tileX, zone.tileY);
      const dx = p.x - px;
      const dy = p.y - py;
      const d = Math.sqrt(dx * dx + dy * dy);
      if (d < nearestDistance) {
        nearestDistance = d;
        nearest = zone;
      }
    });
    return nearestDistance <= 36 ? nearest : null;
  }

  function detectStandingZone() {
    return zones.find((z) => z.tileX === state.avatarX && z.tileY === state.avatarY) || null;
  }

  function moveAvatar(dx, dy) {
    const nextX = Math.max(0, Math.min(state.mapSize - 1, state.avatarX + dx));
    const nextY = Math.max(0, Math.min(state.mapSize - 1, state.avatarY + dy));
    if (nextX === state.avatarX && nextY === state.avatarY) {
      return;
    }
    state.avatarX = nextX;
    state.avatarY = nextY;
    const standingZone = detectStandingZone();
    if (standingZone) {
      visitZone(standingZone, true);
    } else {
      drawMap();
    }
  }

  function resizeCanvas() {
    const rect = canvas.parentElement.getBoundingClientRect();
    const width = Math.min(960, Math.max(320, Math.floor(rect.width - 2)));
    canvas.width = width;
    canvas.height = Math.round(width * 0.58);
    drawMap();
  }

  function bindEvents() {
    canvas.addEventListener("click", (event) => {
      const rect = canvas.getBoundingClientRect();
      const x = (event.clientX - rect.left) * (canvas.width / rect.width);
      const y = (event.clientY - rect.top) * (canvas.height / rect.height);
      const zone = nearestZoneAt(x, y);
      if (zone) {
        visitZone(zone, true);
      }
    });

    document.addEventListener("keydown", (event) => {
      if (
        event.target &&
        (event.target.tagName === "INPUT" ||
          event.target.tagName === "TEXTAREA" ||
          event.target.isContentEditable)
      ) {
        return;
      }
      switch (event.key) {
        case "ArrowUp":
        case "w":
        case "W":
          event.preventDefault();
          moveAvatar(-1, -1);
          break;
        case "ArrowDown":
        case "s":
        case "S":
          event.preventDefault();
          moveAvatar(1, 1);
          break;
        case "ArrowLeft":
        case "a":
        case "A":
          event.preventDefault();
          moveAvatar(-1, 1);
          break;
        case "ArrowRight":
        case "d":
        case "D":
          event.preventDefault();
          moveAvatar(1, -1);
          break;
        case "Enter":
          openSelectedZone();
          break;
        default:
          break;
      }
    });

    zoneCards.forEach((card) => {
      card.addEventListener("click", (event) => {
        event.preventDefault();
        const zone = zones.find((z) => z.id === card.dataset.zone);
        if (!zone) {
          return;
        }
        visitZone(zone, true);
        track("open", zone.id);
        window.location.href = zone.href;
      });
    });

    openBtn.addEventListener("click", openSelectedZone);
    resetBtn.addEventListener("click", () => {
      state.visited.clear();
      saveVisited();
      state.selectedZoneId = null;
      updateUi();
      drawMap();
    });

    let resizeTimeout = null;
    window.addEventListener("resize", () => {
      window.clearTimeout(resizeTimeout);
      resizeTimeout = window.setTimeout(resizeCanvas, 120);
    });
  }

  bindEvents();
  updateUi();
  resizeCanvas();
})();
