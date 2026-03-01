(function () {
  const root = document.getElementById("beta-map-root");
  if (!root) {
    return;
  }

  const canvas = document.getElementById("beta-map-canvas");
  const selectedNode = document.getElementById("beta-selected-zone");
  const visitedNode = document.getElementById("beta-visited-zones");
  const visitedCountNode = document.getElementById("beta-visited-count");
  const openBtn = document.getElementById("beta-open-zone");
  const resetBtn = document.getElementById("beta-reset-progress");
  const previewCard = document.getElementById("beta-zone-preview");
  const previewName = document.getElementById("beta-preview-name");
  const previewDesc = document.getElementById("beta-preview-desc");
  const previewTarget = document.getElementById("beta-preview-target");
  const zoneCards = Array.from(root.querySelectorAll(".beta-zone-card"));
  if (
    !canvas ||
    !selectedNode ||
    !visitedNode ||
    !visitedCountNode ||
    !openBtn ||
    !previewCard ||
    !previewName ||
    !previewDesc ||
    !previewTarget ||
    zoneCards.length === 0
  ) {
    return;
  }

  const ctx = canvas.getContext("2d", { alpha: false });
  if (!ctx) {
    return;
  }

  const storageKey = "homedir.beta.map.v2.visited";
  const trackUrl = root.dataset.trackUrl || "/api/beta/interaction";
  const i18nOpenFirst = root.dataset.i18nOpenFirst || "Select a zone first.";
  const i18nSelectedPrefix = root.dataset.i18nSelectedPrefix || "Selected zone: {0}";
  const i18nSelectedNone = root.dataset.i18nSelectedNone || "No zone selected yet.";
  const i18nVisitedPrefix = root.dataset.i18nVisitedPrefix || "Visited zones: {0}/4";
  const i18nVisitedCounter = root.dataset.i18nVisitedCounter || "Visited";
  const i18nOpenButton = root.dataset.i18nOpenButton || "Open selected zone";
  const defaultPreviewDesc = previewDesc.textContent || "";

  const state = {
    mapSize: 9,
    tileW: 64,
    tileH: 32,
    avatarX: 4,
    avatarY: 4,
    selectedZoneId: null,
    visited: loadVisited()
  };

  const zoneMeta = {
    community: { color: "rgba(89, 170, 255, 0.74)", roof: "#7fd5ff", accent: "#25507e" },
    events: { color: "rgba(255, 154, 92, 0.74)", roof: "#ffd086", accent: "#8b4c25" },
    project: { color: "rgba(110, 215, 174, 0.74)", roof: "#bcffdf", accent: "#2f6e56" },
    profile: { color: "rgba(196, 137, 255, 0.72)", roof: "#e7c7ff", accent: "#5d3f84" }
  };

  const zones = zoneCards.map((card) => ({
    id: card.dataset.zone || "",
    tileX: Number(card.dataset.tileX || "0"),
    tileY: Number(card.dataset.tileY || "0"),
    href: card.getAttribute("href") || "/",
    label: (card.querySelector("strong") || card).textContent.trim(),
    desc: card.dataset.zoneDesc || "",
    card,
    meta: zoneMeta[card.dataset.zone || "community"] || zoneMeta.community
  }));

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

  function replaceToken(template, value) {
    if (!template) {
      return String(value);
    }
    const normalized = String(value);
    if (template.includes("{0}") || template.includes("{count}")) {
      return template.replaceAll("{0}", normalized).replaceAll("{count}", normalized);
    }
    return template + " " + normalized;
  }

  function isoToScreen(x, y) {
    const originX = canvas.width / 2;
    const originY = Math.round(canvas.height * 0.16);
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

  function drawRoad(fromX, fromY, toX, toY) {
    const steps = 18;
    for (let step = 0; step <= steps; step += 1) {
      const t = step / steps;
      const x = fromX + (toX - fromX) * t;
      const y = fromY + (toY - fromY) * t;
      const p = isoToScreen(x, y);
      drawDiamond(p.x, p.y, "rgba(120, 135, 156, 0.52)", "rgba(163, 187, 216, 0.18)");
    }
  }

  function drawHouse(x, y, meta, selected) {
    const p = isoToScreen(x, y);
    drawDiamond(
      p.x,
      p.y - 2,
      selected ? "rgba(255, 226, 132, 0.75)" : meta.color,
      "rgba(255, 255, 255, 0.26)"
    );
    drawDiamond(
      p.x,
      p.y - 15,
      selected ? "rgba(255, 241, 185, 0.92)" : meta.roof,
      "rgba(255, 255, 255, 0.4)"
    );
    ctx.fillStyle = "rgba(12, 22, 35, 0.74)";
    ctx.fillRect(p.x - 4, p.y - 8, 8, 8);
  }

  function drawTree(x, y) {
    const p = isoToScreen(x, y);
    ctx.fillStyle = "rgba(90, 66, 40, 0.95)";
    ctx.fillRect(p.x - 2, p.y - 6, 4, 8);
    ctx.beginPath();
    ctx.fillStyle = "rgba(90, 185, 125, 0.88)";
    ctx.arc(p.x, p.y - 11, 8, 0, Math.PI * 2);
    ctx.fill();
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
          alt ? "rgba(39, 75, 52, 0.72)" : "rgba(33, 65, 46, 0.72)",
          "rgba(100, 148, 119, 0.18)"
        );
      }
    }

    drawRoad(4, 4, 2, 2);
    drawRoad(4, 4, 6, 2);
    drawRoad(4, 4, 2, 6);
    drawRoad(4, 4, 6, 6);
    drawDiamond(isoToScreen(4, 4).x, isoToScreen(4, 4).y, "rgba(149, 167, 184, 0.62)", "rgba(233, 245, 255, 0.3)");
    drawTree(3, 4);
    drawTree(5, 4);
    drawTree(4, 3);
    drawTree(4, 5);

    zones.forEach((zone) => {
      const isSelected = state.selectedZoneId === zone.id;
      const isVisited = state.visited.has(zone.id);
      drawHouse(zone.tileX, zone.tileY, zone.meta, isSelected);

      const marker = isoToScreen(zone.tileX, zone.tileY);
      drawDiamond(
        marker.x,
        marker.y + 17,
        isVisited ? "rgba(117, 229, 158, 0.7)" : "rgba(103, 175, 255, 0.66)",
        isSelected ? "rgba(255, 230, 141, 0.95)" : "rgba(143, 201, 255, 0.72)"
      );

      ctx.fillStyle = "rgba(9, 18, 28, 0.86)";
      ctx.fillRect(marker.x - 50, marker.y - 53, 100, 18);
      ctx.fillStyle = "#ebf4ff";
      ctx.font = "12px 'Exo 2', sans-serif";
      ctx.textAlign = "center";
      ctx.fillText(zone.label, marker.x, marker.y - 39);
    });

    const avatar = isoToScreen(state.avatarX, state.avatarY);
    drawDiamond(avatar.x, avatar.y - 8, "rgba(255, 96, 101, 0.9)", "rgba(255, 192, 194, 0.95)");
    ctx.fillStyle = "rgba(255, 246, 250, 0.95)";
    ctx.beginPath();
    ctx.arc(avatar.x, avatar.y - 22, 5, 0, Math.PI * 2);
    ctx.fill();
  }

  function renderPreview(selectedZone) {
    if (!selectedZone) {
      previewCard.className = "beta-preview-card beta-preview-empty";
      previewName.textContent = i18nSelectedNone;
      previewDesc.textContent = defaultPreviewDesc;
      previewTarget.textContent = "";
      return;
    }
    previewCard.className = "beta-preview-card";
    previewCard.dataset.zone = selectedZone.id;
    previewName.textContent = selectedZone.label;
    previewDesc.textContent = selectedZone.desc || "";
    previewTarget.textContent = selectedZone.href;
  }

  function updateUi() {
    zones.forEach((zone) => {
      const selected = zone.id === state.selectedZoneId;
      const visited = state.visited.has(zone.id);
      zone.card.classList.toggle("is-selected", selected);
      zone.card.classList.toggle("is-visited", visited);
      const pill = zone.card.querySelector(".beta-visited-pill");
      if (pill) {
        pill.hidden = !visited;
      }
    });

    const selectedZone = zones.find((z) => z.id === state.selectedZoneId);
    selectedNode.textContent = selectedZone
      ? replaceToken(i18nSelectedPrefix, selectedZone.label)
      : i18nSelectedNone;
    visitedNode.textContent = replaceToken(i18nVisitedPrefix, state.visited.size);
    visitedCountNode.textContent = String(state.visited.size);
    visitedCountNode.setAttribute("aria-label", i18nVisitedCounter + ": " + state.visited.size);
    openBtn.textContent = selectedZone
      ? i18nOpenButton + ": " + selectedZone.label
      : i18nOpenButton;
    renderPreview(selectedZone);
    drawMap();
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

  function selectZone(zone, shouldTrack) {
    if (!zone) {
      return;
    }
    state.selectedZoneId = zone.id;
    if (shouldTrack) {
      track("preview", zone.id);
    }
    updateUi();
  }

  function markVisited(zone) {
    if (!zone || state.visited.has(zone.id)) {
      return;
    }
    state.visited.add(zone.id);
    saveVisited();
  }

  function openSelectedZone() {
    const selectedZone = zones.find((z) => z.id === state.selectedZoneId);
    if (!selectedZone) {
      window.alert(i18nOpenFirst);
      return;
    }
    markVisited(selectedZone);
    updateUi();
    track("open", selectedZone.id);
    window.setTimeout(() => {
      window.location.href = selectedZone.href;
    }, 60);
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
      selectZone(standingZone, true);
      return;
    }
    drawMap();
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
        selectZone(zone, true);
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
        selectZone(zone, true);
      });
    });

    openBtn.addEventListener("click", openSelectedZone);
    resetBtn.addEventListener("click", () => {
      state.visited.clear();
      saveVisited();
      state.selectedZoneId = null;
      updateUi();
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
