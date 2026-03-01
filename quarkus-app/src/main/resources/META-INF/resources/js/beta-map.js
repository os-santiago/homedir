(function () {
  const root = document.getElementById("beta-game-root");
  if (!root) {
    return;
  }

  const canvas = document.getElementById("beta-map-canvas");
  const selectedNode = document.getElementById("beta-selected-zone");
  const visitedNode = document.getElementById("beta-visited-zones");
  const visitedCountNode = document.getElementById("beta-visited-count");
  const resetBtn = document.getElementById("beta-reset-progress");
  const zoneTitle = document.getElementById("beta-zone-title");
  const zoneDescription = document.getElementById("beta-zone-description");
  const zonePanels = Array.from(root.querySelectorAll(".beta-zone-panel[data-zone-panel]"));
  const travelButtons = Array.from(root.querySelectorAll(".beta-travel-btn[data-zone-jump]"));
  if (
    !canvas ||
    !selectedNode ||
    !visitedNode ||
    !visitedCountNode ||
    !resetBtn ||
    !zoneTitle ||
    !zoneDescription ||
    zonePanels.length === 0 ||
    travelButtons.length === 0
  ) {
    return;
  }

  const ctx = canvas.getContext("2d", { alpha: false });
  if (!ctx) {
    return;
  }

  const trackUrl = root.dataset.trackUrl || "/api/beta/interaction";
  const i18nSelectedPrefix = root.dataset.i18nSelectedPrefix || "Current district: {0}";
  const i18nSelectedNone = root.dataset.i18nSelectedNone || "Move to a district to open its in-game view.";
  const i18nVisitedPrefix = root.dataset.i18nVisitedPrefix || "Visited districts: {0}/4";
  const i18nVisitedCounter = root.dataset.i18nVisitedCounter || "Visited";
  const storageKey = "homedir.beta.map.v3.visited";

  const zoneDescriptions = new Map();
  zonePanels.forEach((panel) => {
    const id = panel.dataset.zonePanel;
    const descNode = panel.querySelector(".beta-zone-copy");
    zoneDescriptions.set(id, descNode ? descNode.textContent.trim() : "");
  });

  const zoneMeta = {
    inn: { roof: "#ffc06b", wall: "#9b5d3b", accent: "#ffd9a4", x: 4, y: 3 },
    guild: { roof: "#6fc7ff", wall: "#2f6283", accent: "#bde9ff", x: 2, y: 7 },
    theater: { roof: "#ff8f7a", wall: "#853b35", accent: "#ffc6bc", x: 9, y: 5 },
    cityhall: { roof: "#95d58a", wall: "#376f40", accent: "#caf5c4", x: 7, y: 9 }
  };

  const zones = travelButtons
    .map((button) => {
      const id = button.dataset.zoneJump;
      const meta = zoneMeta[id];
      if (!meta) {
        return null;
      }
      return {
        id,
        label: button.textContent.trim(),
        desc: zoneDescriptions.get(id) || "",
        tileX: meta.x,
        tileY: meta.y,
        meta
      };
    })
    .filter(Boolean);

  const state = {
    mapSize: 12,
    tileW: 56,
    tileH: 28,
    avatarX: 5,
    avatarY: 6,
    selectedZoneId: "inn",
    visited: loadVisited()
  };

  const decorativeHouses = [
    { x: 1, y: 2, roof: "#8ca4ce", wall: "#3f4d66" },
    { x: 10, y: 2, roof: "#8ca4ce", wall: "#3f4d66" },
    { x: 1, y: 10, roof: "#8ca4ce", wall: "#3f4d66" },
    { x: 10, y: 10, roof: "#8ca4ce", wall: "#3f4d66" }
  ];

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
      return new Set(parsed.filter((value) => typeof value === "string"));
    } catch (error) {
      return new Set();
    }
  }

  function saveVisited() {
    try {
      window.localStorage.setItem(storageKey, JSON.stringify(Array.from(state.visited)));
    } catch (error) {
      // ignore local storage limitations
    }
  }

  function replaceToken(template, value) {
    const normalized = String(value);
    if (template.includes("{0}") || template.includes("{count}")) {
      return template.replaceAll("{0}", normalized).replaceAll("{count}", normalized);
    }
    return template + " " + normalized;
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

  function updateMapScale() {
    state.tileW = Math.max(34, Math.min(74, Math.floor(canvas.width / (state.mapSize + 4))));
    state.tileH = Math.max(18, Math.floor(state.tileW / 2));
  }

  function isoToScreen(x, y) {
    const originX = canvas.width * 0.5;
    const originY = canvas.height * 0.18;
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
    const steps = 20;
    for (let step = 0; step <= steps; step += 1) {
      const t = step / steps;
      const x = fromX + (toX - fromX) * t;
      const y = fromY + (toY - fromY) * t;
      const p = isoToScreen(x, y);
      drawDiamond(p.x, p.y + 1, "rgba(90, 105, 128, 0.7)", "rgba(168, 183, 206, 0.3)");
    }
  }

  function drawBuilding(zone, isSelected, isVisited) {
    const p = isoToScreen(zone.tileX, zone.tileY);
    const tone = zone.meta;
    drawDiamond(p.x, p.y - 1, tone.wall, "rgba(255, 255, 255, 0.22)");
    drawDiamond(
      p.x,
      p.y - state.tileH * 0.46,
      isSelected ? "rgba(255, 238, 169, 0.95)" : tone.roof,
      "rgba(255, 255, 255, 0.35)"
    );
    drawDiamond(
      p.x,
      p.y + state.tileH * 0.66,
      isVisited ? "rgba(112, 227, 144, 0.84)" : "rgba(126, 197, 255, 0.7)",
      isSelected ? "#ffeaa7" : "rgba(208, 234, 255, 0.56)"
    );
    ctx.fillStyle = "rgba(14, 26, 40, 0.82)";
    ctx.fillRect(p.x - 56, p.y - 54, 112, 18);
    ctx.fillStyle = "#f3f8ff";
    ctx.font = "600 12px monospace";
    ctx.textAlign = "center";
    ctx.fillText(zone.label, p.x, p.y - 40);
  }

  function drawDecor() {
    decorativeHouses.forEach((house) => {
      const p = isoToScreen(house.x, house.y);
      drawDiamond(p.x, p.y - 1, house.wall, "rgba(255,255,255,0.12)");
      drawDiamond(p.x, p.y - state.tileH * 0.44, house.roof, "rgba(255,255,255,0.2)");
    });
    [[3, 5], [6, 5], [8, 8], [4, 9], [9, 7]].forEach(([x, y]) => {
      const p = isoToScreen(x, y);
      ctx.fillStyle = "rgba(82, 61, 38, 0.9)";
      ctx.fillRect(p.x - 2, p.y - 7, 4, 8);
      drawDiamond(
        p.x,
        p.y - 13,
        "rgba(78, 174, 109, 0.86)",
        "rgba(149, 227, 175, 0.45)"
      );
    });
  }

  function drawMap() {
    ctx.fillStyle = "#070f1b";
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    for (let y = 0; y < state.mapSize; y += 1) {
      for (let x = 0; x < state.mapSize; x += 1) {
        const p = isoToScreen(x, y);
        drawDiamond(
          p.x,
          p.y,
          (x + y) % 2 === 0 ? "rgba(37, 79, 51, 0.9)" : "rgba(32, 69, 45, 0.9)",
          "rgba(130, 179, 146, 0.2)"
        );
      }
    }

    drawRoad(5, 6, 4, 3);
    drawRoad(5, 6, 2, 7);
    drawRoad(5, 6, 9, 5);
    drawRoad(5, 6, 7, 9);
    const plaza = isoToScreen(5, 6);
    drawDiamond(plaza.x, plaza.y, "rgba(154, 172, 188, 0.72)", "rgba(245, 250, 255, 0.42)");
    drawDecor();

    zones.forEach((zone) => {
      const isSelected = state.selectedZoneId === zone.id;
      const isVisited = state.visited.has(zone.id);
      drawBuilding(zone, isSelected, isVisited);
    });

    const avatar = isoToScreen(state.avatarX, state.avatarY);
    drawDiamond(avatar.x, avatar.y - 5, "rgba(255, 112, 114, 0.95)", "rgba(255, 202, 202, 0.95)");
    ctx.fillStyle = "rgba(255, 247, 247, 0.92)";
    ctx.fillRect(avatar.x - 3, avatar.y - 25, 6, 7);
  }

  function markVisited(zone, shouldTrack) {
    if (!zone || state.visited.has(zone.id)) {
      return;
    }
    state.visited.add(zone.id);
    saveVisited();
    if (shouldTrack) {
      track("visit", zone.id);
    }
  }

  function updateUi() {
    const selectedZone = zones.find((zone) => zone.id === state.selectedZoneId) || null;
    selectedNode.textContent = selectedZone
      ? replaceToken(i18nSelectedPrefix, selectedZone.label)
      : i18nSelectedNone;
    visitedNode.textContent = replaceToken(i18nVisitedPrefix, state.visited.size);
    visitedCountNode.textContent = String(state.visited.size);
    visitedCountNode.setAttribute("aria-label", i18nVisitedCounter + ": " + state.visited.size);

    if (selectedZone) {
      zoneTitle.textContent = selectedZone.label;
      zoneDescription.textContent = selectedZone.desc || "";
    } else {
      zoneTitle.textContent = i18nSelectedNone;
      zoneDescription.textContent = "";
    }

    zonePanels.forEach((panel) => {
      const active = selectedZone && panel.dataset.zonePanel === selectedZone.id;
      panel.hidden = !active;
    });

    travelButtons.forEach((button) => {
      const zoneId = button.dataset.zoneJump;
      button.classList.toggle("is-selected", zoneId === state.selectedZoneId);
      button.classList.toggle("is-visited", state.visited.has(zoneId));
    });

    drawMap();
  }

  function selectZone(zone, options) {
    if (!zone) {
      return;
    }
    const shouldTrack = Boolean(options && options.track);
    const trackPreview = shouldTrack && state.selectedZoneId !== zone.id;
    state.selectedZoneId = zone.id;
    state.avatarX = zone.tileX;
    state.avatarY = zone.tileY;
    markVisited(zone, true);
    if (trackPreview) {
      track("preview", zone.id);
    }
    updateUi();
  }

  function nearestZoneAt(px, py) {
    let nearest = null;
    let nearestDistance = Number.POSITIVE_INFINITY;
    zones.forEach((zone) => {
      const p = isoToScreen(zone.tileX, zone.tileY);
      const dx = p.x - px;
      const dy = p.y - py;
      const distance = Math.sqrt(dx * dx + dy * dy);
      if (distance < nearestDistance) {
        nearestDistance = distance;
        nearest = zone;
      }
    });
    const threshold = Math.max(28, state.tileW * 0.7);
    return nearestDistance <= threshold ? nearest : null;
  }

  function detectStandingZone() {
    return zones.find((zone) => zone.tileX === state.avatarX && zone.tileY === state.avatarY) || null;
  }

  function moveAvatar(dx, dy) {
    const nextX = Math.max(0, Math.min(state.mapSize - 1, state.avatarX + dx));
    const nextY = Math.max(0, Math.min(state.mapSize - 1, state.avatarY + dy));
    if (nextX === state.avatarX && nextY === state.avatarY) {
      return;
    }
    state.avatarX = nextX;
    state.avatarY = nextY;
    const standing = detectStandingZone();
    if (standing) {
      selectZone(standing, { track: true });
      return;
    }
    drawMap();
  }

  function resizeCanvas() {
    const frame = canvas.parentElement;
    const rect = frame.getBoundingClientRect();
    const width = Math.max(320, Math.floor(rect.width));
    const height = Math.max(260, Math.floor(rect.height));
    canvas.width = width;
    canvas.height = height;
    updateMapScale();
    drawMap();
  }

  function bindEvents() {
    canvas.addEventListener("click", (event) => {
      const rect = canvas.getBoundingClientRect();
      const x = (event.clientX - rect.left) * (canvas.width / rect.width);
      const y = (event.clientY - rect.top) * (canvas.height / rect.height);
      const zone = nearestZoneAt(x, y);
      if (zone) {
        selectZone(zone, { track: true });
      }
    });

    travelButtons.forEach((button) => {
      button.addEventListener("click", () => {
        const zone = zones.find((item) => item.id === button.dataset.zoneJump);
        if (zone) {
          selectZone(zone, { track: true });
        }
      });
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
        default:
          break;
      }
    });

    resetBtn.addEventListener("click", () => {
      state.visited.clear();
      saveVisited();
      updateUi();
    });

    let resizeTimer = null;
    window.addEventListener("resize", () => {
      window.clearTimeout(resizeTimer);
      resizeTimer = window.setTimeout(resizeCanvas, 120);
    });
  }

  bindEvents();
  resizeCanvas();
  const initialZone = zones.find((zone) => zone.id === state.selectedZoneId) || zones[0];
  if (initialZone) {
    selectZone(initialZone, { track: false });
  } else {
    updateUi();
  }
})();
