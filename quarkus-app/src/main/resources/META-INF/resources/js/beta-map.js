(function () {
  const root = document.getElementById("beta-game-root");
  if (!root) {
    return;
  }

  const canvas = document.getElementById("beta-map-canvas");
  const selectedNode = document.getElementById("beta-selected-zone");
  const visitedNode = document.getElementById("beta-visited-zones");
  const visitedCountNode = document.getElementById("beta-visited-count");
  const inventoryCountNode = document.getElementById("beta-inventory-count");
  const deliveriesCountNode = document.getElementById("beta-deliveries-count");
  const resetBtn = document.getElementById("beta-reset-progress");
  const zoneModelsNode = document.getElementById("beta-zone-models");
  const modal = document.getElementById("beta-interaction-modal");
  const modalClose = document.getElementById("beta-modal-close");
  const modalTitle = document.getElementById("beta-modal-title");
  const modalMeta = document.getElementById("beta-modal-meta");
  const modalBody = document.getElementById("beta-modal-body");
  const modalLink = document.getElementById("beta-modal-link");
  if (
    !canvas ||
    !selectedNode ||
    !visitedNode ||
    !visitedCountNode ||
    !inventoryCountNode ||
    !deliveriesCountNode ||
    !resetBtn ||
    !zoneModelsNode ||
    !modal ||
    !modalClose ||
    !modalTitle ||
    !modalMeta ||
    !modalBody ||
    !modalLink
  ) {
    return;
  }

  const ctx = canvas.getContext("2d", { alpha: false });
  if (!ctx) {
    return;
  }

  const isAuthenticated = root.dataset.authenticated === "true";
  const interactionLocked = !isAuthenticated || document.getElementById("beta-login-gate") !== null;
  const trackUrl = root.dataset.trackUrl || "/api/beta/interaction";
  const i18nSelectedPrefix = root.dataset.i18nSelectedPrefix || "Current district: {0}";
  const i18nSelectedNone = root.dataset.i18nSelectedNone || "Move to a district to open its in-game view.";
  const i18nVisitedPrefix = root.dataset.i18nVisitedPrefix || "Visited districts: {0}/4";
  const i18nVisitedCounter = root.dataset.i18nVisitedCounter || "Visited";
  const i18nHintLocked = root.dataset.i18nHintLocked || "Login required to enter buildings";
  const i18nHintEnter = root.dataset.i18nHintEnter || "Click or step on a district to enter";
  const i18nOpenSection = root.dataset.i18nOpenSection || "Open section";
  const i18nOpenCard = root.dataset.i18nOpenCard || "Open";
  const i18nInsideSuffix = root.dataset.i18nInsideSuffix || "inside";
  const i18nInteriorSuffix = root.dataset.i18nInteriorSuffix || "INTERIOR";
  const i18nBackTown = root.dataset.i18nBackTown || "Back to town";
  const i18nInventoryLabel = root.dataset.i18nInventoryLabel || "Inventory";
  const i18nDeliveriesLabel = root.dataset.i18nDeliveriesLabel || "Deliveries";
  const i18nInteractHint = root.dataset.i18nInteractHint || "E/Enter to interact · Esc to exit";
  const i18nActionRead = root.dataset.i18nActionRead || "Read";
  const i18nActionLook = root.dataset.i18nActionLook || "Look";
  const i18nActionTake = root.dataset.i18nActionTake || "Take";
  const i18nActionCollect = root.dataset.i18nActionCollect || "Collect";
  const i18nActionDeliver = root.dataset.i18nActionDeliver || "Deliver";
  const i18nDeliverEmpty = root.dataset.i18nDeliverEmpty || "You have nothing to deliver yet.";
  const i18nDeliverSuccess = root.dataset.i18nDeliverSuccess || "Delivery completed.";
  const i18nCollectedAlready = root.dataset.i18nCollectedAlready || "Already collected in this room.";
  const i18nCollectedSuccess = root.dataset.i18nCollectedSuccess || "Item collected.";
  const i18nModalClose = root.dataset.i18nModalClose || "Close";
  const playerAvatarUrl = (root.dataset.playerAvatarUrl || "").trim();
  const playerInitial = (root.dataset.playerInitial || "HD").slice(0, 2).toUpperCase();
  const storageScope = isAuthenticated ? "auth" : "guest";
  const visitedKey = "homedir.beta.map.v8." + storageScope + ".visited";
  const inventoryKey = "homedir.beta.map.v8." + storageScope + ".inventory";
  const deliveriesKey = "homedir.beta.map.v8." + storageScope + ".deliveries";
  const collectedKey = "homedir.beta.map.v8." + storageScope + ".collected";
  modalClose.setAttribute("aria-label", i18nModalClose);

  const zoneMeta = {
    inn: { roof: "#ffc06b", wall: "#9b5d3b", accent: "#ffd9a4", x: 6, y: 4, interior: "#5b3b1d" },
    guild: { roof: "#6fc7ff", wall: "#2f6283", accent: "#bde9ff", x: 3, y: 10, interior: "#1f3d57" },
    theater: { roof: "#ff8f7a", wall: "#853b35", accent: "#ffc6bc", x: 13, y: 7, interior: "#5e2422" },
    cityhall: { roof: "#95d58a", wall: "#376f40", accent: "#caf5c4", x: 10, y: 13, interior: "#1f4530" }
  };

  const zoneModels = parseZoneModels(zoneModelsNode);
  const zones = Object.keys(zoneMeta).map((id) => {
    const model = zoneModels.get(id);
    const meta = zoneMeta[id];
    return {
      id,
      tileX: meta.x,
      tileY: meta.y,
      meta,
      label: model ? model.label : id,
      desc: model ? model.description : "",
      link: model ? model.link : "/",
      items: model ? model.items : []
    };
  });

  const state = {
    scene: "town",
    mapSize: 16,
    tileW: 56,
    tileH: 28,
    avatarX: 8,
    avatarY: 8,
    selectedZoneId: "inn",
    interiorZoneId: null,
    interiorLayout: null,
    visited: loadSet(visitedKey),
    inventoryCount: loadNumber(inventoryKey),
    deliveriesCount: loadNumber(deliveriesKey),
    collected: loadSet(collectedKey),
    hotspots: [],
    modalOpen: false
  };

  const avatarSprite = {
    image: null,
    ready: false
  };
  if (isAuthenticated && playerAvatarUrl) {
    const image = new Image();
    image.crossOrigin = "anonymous";
    image.onload = () => {
      avatarSprite.ready = true;
      drawScene();
    };
    image.onerror = () => {
      avatarSprite.ready = false;
      drawScene();
    };
    image.src = playerAvatarUrl;
    avatarSprite.image = image;
  }

  const decorativeHouses = [
    { x: 2, y: 3, roof: "#8ca4ce", wall: "#3f4d66" },
    { x: 12, y: 2, roof: "#8ca4ce", wall: "#3f4d66" },
    { x: 2, y: 13, roof: "#8ca4ce", wall: "#3f4d66" },
    { x: 14, y: 12, roof: "#8ca4ce", wall: "#3f4d66" },
    { x: 7, y: 2, roof: "#8ca4ce", wall: "#3f4d66" },
    { x: 5, y: 14, roof: "#8ca4ce", wall: "#3f4d66" }
  ];

  function parseZoneModels(container) {
    const out = new Map();
    const modelNodes = Array.from(container.querySelectorAll("[data-zone-model]"));
    modelNodes.forEach((node) => {
      const id = (node.dataset.zoneModel || "").trim().toLowerCase();
      if (!id) {
        return;
      }
      const items = Array.from(node.querySelectorAll("[data-item-kind]")).map((itemNode) => ({
        kind: itemNode.dataset.itemKind || "entry",
        title: itemNode.dataset.title || "",
        summary: itemNode.dataset.summary || "",
        meta: itemNode.dataset.meta || "",
        value: itemNode.dataset.value || "",
        link: itemNode.dataset.link || ""
      }));
      out.set(id, {
        label: node.dataset.label || id,
        description: node.dataset.description || "",
        link: node.dataset.link || "/",
        items
      });
    });
    return out;
  }

  function loadSet(key) {
    try {
      const raw = window.localStorage.getItem(key);
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

  function saveSet(key, set) {
    try {
      window.localStorage.setItem(key, JSON.stringify(Array.from(set)));
    } catch (error) {
      // ignore local storage limitations
    }
  }

  function loadNumber(key) {
    try {
      const raw = window.localStorage.getItem(key);
      if (!raw) {
        return 0;
      }
      const parsed = Number.parseInt(raw, 10);
      if (!Number.isFinite(parsed) || parsed < 0) {
        return 0;
      }
      return parsed;
    } catch (error) {
      return 0;
    }
  }

  function saveNumber(key, value) {
    try {
      window.localStorage.setItem(key, String(Math.max(0, value)));
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

  function showModal(payload) {
    if (!payload) {
      return;
    }
    modalTitle.textContent = payload.title || "";
    modalMeta.textContent = payload.meta || "";
    modalBody.textContent = payload.body || "";
    if (payload.link) {
      modalLink.href = payload.link;
      modalLink.hidden = false;
      modalLink.textContent = i18nOpenCard;
    } else {
      modalLink.hidden = true;
      modalLink.removeAttribute("href");
    }
    modal.hidden = false;
    state.modalOpen = true;
  }

  function hideModal() {
    modal.hidden = true;
    state.modalOpen = false;
  }

  function saveVisited() {
    saveSet(visitedKey, state.visited);
  }

  function saveProgress() {
    saveSet(collectedKey, state.collected);
    saveNumber(inventoryKey, state.inventoryCount);
    saveNumber(deliveriesKey, state.deliveriesCount);
  }

  function updateMapScale() {
    state.tileW = Math.max(28, Math.min(68, Math.floor(canvas.width / (state.mapSize + 5))));
    state.tileH = Math.max(15, Math.floor(state.tileW / 2));
  }

  function isoToScreen(x, y) {
    const originX = canvas.width * 0.5;
    const originY = canvas.height * 0.22;
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

  function drawRoundedRectPath(x, y, width, height, radius) {
    const r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + width, y, x + width, y + height, r);
    ctx.arcTo(x + width, y + height, x, y + height, r);
    ctx.arcTo(x, y + height, x, y, r);
    ctx.arcTo(x, y, x + width, y, r);
    ctx.closePath();
  }

  function drawRoad(fromX, fromY, toX, toY) {
    const steps = 26;
    for (let step = 0; step <= steps; step += 1) {
      const t = step / steps;
      const x = fromX + (toX - fromX) * t;
      const y = fromY + (toY - fromY) * t;
      const p = isoToScreen(x, y);
      drawDiamond(p.x, p.y + 1, "rgba(98, 109, 126, 0.7)", "rgba(178, 195, 219, 0.25)");
    }
  }

  function drawBuilding(zone, isSelected, isVisited) {
    const p = isoToScreen(zone.tileX, zone.tileY);
    const tone = zone.meta;
    drawDiamond(p.x, p.y - 1, tone.wall, "rgba(255, 255, 255, 0.22)");
    drawDiamond(
      p.x,
      p.y - state.tileH * 0.48,
      isSelected ? "rgba(255, 238, 169, 0.95)" : tone.roof,
      "rgba(255, 255, 255, 0.35)"
    );
    drawDiamond(
      p.x,
      p.y + state.tileH * 0.68,
      isVisited ? "rgba(112, 227, 144, 0.84)" : "rgba(126, 197, 255, 0.7)",
      isSelected ? "#ffeaa7" : "rgba(208, 234, 255, 0.56)"
    );
    ctx.fillStyle = "rgba(8, 19, 33, 0.88)";
    ctx.fillRect(p.x - 64, p.y - 54, 128, 18);
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
    [
      [4, 6],
      [7, 6],
      [11, 10],
      [6, 12],
      [12, 8],
      [9, 4]
    ].forEach(([x, y]) => {
      const p = isoToScreen(x, y);
      ctx.fillStyle = "rgba(82, 61, 38, 0.9)";
      ctx.fillRect(p.x - 2, p.y - 7, 4, 8);
      drawDiamond(p.x, p.y - 13, "rgba(78, 174, 109, 0.86)", "rgba(149, 227, 175, 0.45)");
    });
  }

  function drawAvatar(x, y, size) {
    const fallbackSize = Math.max(20, Math.floor(state.tileW * 0.48));
    const avatarSize = size || fallbackSize;
    const px = x - Math.floor(avatarSize / 2);
    const py = y - Math.floor(avatarSize / 2);

    if (avatarSprite.ready && avatarSprite.image) {
      ctx.save();
      drawRoundedRectPath(px, py, avatarSize, avatarSize, 6);
      ctx.clip();
      ctx.drawImage(avatarSprite.image, px, py, avatarSize, avatarSize);
      ctx.restore();
      ctx.strokeStyle = "rgba(255, 255, 255, 0.88)";
      ctx.lineWidth = 2;
      drawRoundedRectPath(px, py, avatarSize, avatarSize, 6);
      ctx.stroke();
      return;
    }

    ctx.fillStyle = isAuthenticated ? "rgba(255, 165, 96, 0.95)" : "rgba(125, 143, 166, 0.95)";
    drawRoundedRectPath(px, py, avatarSize, avatarSize, 6);
    ctx.fill();
    ctx.strokeStyle = "rgba(255, 255, 255, 0.88)";
    ctx.lineWidth = 2;
    drawRoundedRectPath(px, py, avatarSize, avatarSize, 6);
    ctx.stroke();
    ctx.fillStyle = "rgba(17, 26, 42, 0.92)";
    ctx.font = "700 11px monospace";
    ctx.textAlign = "center";
    ctx.fillText(playerInitial, x, py + Math.floor(avatarSize * 0.62));
  }

  function withAlpha(hex, alpha) {
    const value = (hex || "#2f4858").replace("#", "");
    const normalized =
      value.length === 3
        ? value
            .split("")
            .map((c) => c + c)
            .join("")
        : value.padEnd(6, "0").slice(0, 6);
    const r = parseInt(normalized.slice(0, 2), 16) || 0;
    const g = parseInt(normalized.slice(2, 4), 16) || 0;
    const b = parseInt(normalized.slice(4, 6), 16) || 0;
    return "rgba(" + r + ", " + g + ", " + b + ", " + alpha + ")";
  }

  function drawPill(text, x, y, fill, stroke) {
    ctx.font = "600 12px monospace";
    const width = Math.ceil(ctx.measureText(text).width) + 22;
    const height = 26;
    ctx.fillStyle = fill;
    drawRoundedRectPath(x, y, width, height, 8);
    ctx.fill();
    ctx.strokeStyle = stroke;
    ctx.lineWidth = 1;
    drawRoundedRectPath(x, y, width, height, 8);
    ctx.stroke();
    ctx.fillStyle = "#f3f8ff";
    ctx.textAlign = "left";
    ctx.fillText(text, x + 11, y + 17);
    return { x, y, width, height };
  }

  function drawParagraph(text, x, y, maxWidth, lineHeight) {
    const words = (text || "").split(/\s+/).filter(Boolean);
    let line = "";
    let row = 0;
    words.forEach((word) => {
      const test = line ? line + " " + word : word;
      if (ctx.measureText(test).width > maxWidth && line) {
        ctx.fillText(line, x, y + row * lineHeight);
        line = word;
        row += 1;
      } else {
        line = test;
      }
    });
    if (line) {
      ctx.fillText(line, x, y + row * lineHeight);
      row += 1;
    }
    return row;
  }

  function crop(text, maxChars) {
    if (!text) {
      return "";
    }
    if (text.length <= maxChars) {
      return text;
    }
    return text.slice(0, Math.max(0, maxChars - 1)).trim() + "…";
  }

  function pushHotspot(x, y, width, height, action, payload) {
    state.hotspots.push({ x, y, width, height, action, payload });
  }

  function drawTownScene() {
    ctx.fillStyle = "#050f1c";
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    for (let y = 0; y < state.mapSize; y += 1) {
      for (let x = 0; x < state.mapSize; x += 1) {
        const p = isoToScreen(x, y);
        drawDiamond(
          p.x,
          p.y,
          (x + y) % 2 === 0 ? "rgba(35, 78, 51, 0.93)" : "rgba(31, 67, 45, 0.93)",
          "rgba(130, 179, 146, 0.2)"
        );
      }
    }

    drawRoad(8, 8, 6, 4);
    drawRoad(8, 8, 3, 10);
    drawRoad(8, 8, 13, 7);
    drawRoad(8, 8, 10, 13);
    const plaza = isoToScreen(8, 8);
    drawDiamond(plaza.x, plaza.y, "rgba(154, 172, 188, 0.72)", "rgba(245, 250, 255, 0.42)");

    drawDecor();

    zones.forEach((zone) => {
      const isSelected = state.selectedZoneId === zone.id;
      const isVisited = state.visited.has(zone.id);
      drawBuilding(zone, isSelected, isVisited);
    });

    const avatarPoint = isoToScreen(state.avatarX, state.avatarY);
    drawDiamond(
      avatarPoint.x,
      avatarPoint.y + state.tileH * 0.08,
      "rgba(255, 255, 255, 0.16)",
      "rgba(255, 255, 255, 0.12)"
    );
    drawAvatar(avatarPoint.x, avatarPoint.y - Math.floor(state.tileH * 0.75));

    const hintRect = drawPill(
      interactionLocked
        ? i18nHintLocked
        : i18nHintEnter,
      18,
      canvas.height - 44,
      "rgba(8, 18, 31, 0.86)",
      "rgba(170, 206, 245, 0.28)"
    );
    if (interactionLocked) {
      pushHotspot(hintRect.x, hintRect.y, hintRect.width, hintRect.height, "noop", null);
    }
  }

  function drawCard(item, cardX, cardY, cardW, cardH) {
    ctx.fillStyle = "rgba(9, 18, 31, 0.92)";
    drawRoundedRectPath(cardX, cardY, cardW, cardH, 10);
    ctx.fill();
    ctx.strokeStyle = "rgba(178, 212, 246, 0.24)";
    ctx.lineWidth = 1;
    drawRoundedRectPath(cardX, cardY, cardW, cardH, 10);
    ctx.stroke();

    if (item.kind === "stat") {
      ctx.fillStyle = "rgba(205, 223, 246, 0.84)";
      ctx.font = "600 11px monospace";
      ctx.textAlign = "left";
      drawParagraph(crop(item.title, 30), cardX + 12, cardY + 20, cardW - 24, 13);
      ctx.fillStyle = "#f8d27a";
      ctx.font = "700 20px monospace";
      ctx.fillText(crop(item.value, 12), cardX + 12, cardY + 48);
      return;
    }

    if (item.kind === "empty") {
      ctx.fillStyle = "rgba(205, 223, 246, 0.9)";
      ctx.font = "600 12px monospace";
      ctx.textAlign = "left";
      drawParagraph(crop(item.title, 82), cardX + 12, cardY + 22, cardW - 24, 15);
      return;
    }

    ctx.fillStyle = "#f3f8ff";
    ctx.font = "700 12px monospace";
    ctx.textAlign = "left";
    const titleRows = drawParagraph(crop(item.title, 60), cardX + 12, cardY + 20, cardW - 24, 14);
    ctx.fillStyle = "rgba(196, 216, 240, 0.84)";
    ctx.font = "600 11px monospace";
    const metaY = cardY + 20 + titleRows * 14 + 4;
    drawParagraph(crop(item.meta, 70), cardX + 12, metaY, cardW - 24, 13);
    ctx.fillStyle = "rgba(217, 231, 249, 0.92)";
    drawParagraph(crop(item.summary, 110), cardX + 12, metaY + 16, cardW - 24, 13);

    if (item.link) {
      ctx.fillStyle = "#8bd0ff";
      ctx.font = "700 11px monospace";
      ctx.fillText(i18nOpenCard, cardX + 12, cardY + cardH - 10);
      pushHotspot(cardX, cardY, cardW, cardH, "open-link", item.link);
    }
  }

  function buildInteriorLayout(zone) {
    const rows = 7;
    const cols = 10;
    const tiles = [];
    tiles.push({
      id: zone.id + ".exit",
      type: "exit",
      label: "EXIT",
      verb: i18nBackTown,
      x: 0,
      y: rows - 1
    });

    const zoneItems = zone.items.filter((item) => item.kind === "entry" || item.kind === "stat");
    const points = [[2, 2], [4, 2], [6, 2]];
    zoneItems.slice(0, 3).forEach((item, index) => {
      const point = points[index] || [2 + index, 2];
      tiles.push({
        id: zone.id + ".info." + index,
        type: "content",
        label: index % 2 === 0 ? "READ" : "LOOK",
        verb: index % 2 === 0 ? i18nActionRead : i18nActionLook,
        x: point[0],
        y: point[1],
        item
      });
    });

    tiles.push({
      id: zone.id + ".collect",
      type: "collect",
      label: "TAKE",
      verb: i18nActionCollect,
      x: cols - 2,
      y: rows - 2
    });
    tiles.push({
      id: zone.id + ".deliver",
      type: "deliver",
      label: "TURNIN",
      verb: i18nActionDeliver,
      x: Math.max(1, Math.floor(cols / 2)),
      y: rows - 2
    });

    return {
      rows,
      cols,
      avatarX: 1,
      avatarY: rows - 1,
      tiles
    };
  }

  function drawInteriorScene(zone) {
    if (!zone) {
      state.scene = "town";
      state.interiorZoneId = null;
      state.interiorLayout = null;
      drawTownScene();
      return;
    }

    if (!state.interiorLayout) {
      state.interiorLayout = buildInteriorLayout(zone);
    }
    const layout = state.interiorLayout;

    const accent = zone.meta.accent || "#a7d7ff";
    const interior = zone.meta.interior || "#243747";
    const gradient = ctx.createLinearGradient(0, 0, 0, canvas.height);
    gradient.addColorStop(0, withAlpha(accent, 0.22));
    gradient.addColorStop(1, withAlpha(interior, 0.9));
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    const gridX = 18;
    const gridY = 132;
    const panelW = Math.min(280, Math.max(230, Math.floor(canvas.width * 0.27)));
    const gridW = Math.max(220, canvas.width - panelW - 54);
    const gridH = Math.max(190, canvas.height - gridY - 34);
    const cellSize = Math.max(24, Math.min(Math.floor(gridW / layout.cols), Math.floor(gridH / layout.rows)));
    const usedW = cellSize * layout.cols;
    const usedH = cellSize * layout.rows;
    const originX = gridX + Math.floor((gridW - usedW) / 2);
    const originY = gridY + Math.floor((gridH - usedH) / 2);

    ctx.fillStyle = "rgba(5, 12, 20, 0.46)";
    drawRoundedRectPath(gridX, gridY, gridW, gridH, 12);
    ctx.fill();
    ctx.strokeStyle = withAlpha(accent, 0.35);
    ctx.lineWidth = 1;
    drawRoundedRectPath(gridX, gridY, gridW, gridH, 12);
    ctx.stroke();

    for (let row = 0; row < layout.rows; row += 1) {
      for (let col = 0; col < layout.cols; col += 1) {
        const x = originX + col * cellSize;
        const y = originY + row * cellSize;
        const checker = (row + col) % 2 === 0;
        ctx.fillStyle = checker ? withAlpha(zone.meta.wall, 0.35) : withAlpha(zone.meta.wall, 0.24);
        ctx.fillRect(x, y, cellSize - 1, cellSize - 1);
        ctx.strokeStyle = withAlpha(accent, 0.12);
        ctx.strokeRect(x + 0.5, y + 0.5, cellSize - 1, cellSize - 1);
      }
    }

    layout.tiles.forEach((tile) => {
      const x = originX + tile.x * cellSize;
      const y = originY + tile.y * cellSize;
      const collected = tile.type === "collect" && state.collected.has(tile.id);
      const focused = layout.avatarX === tile.x && layout.avatarY === tile.y;
      ctx.fillStyle = tile.type === "exit"
        ? "rgba(236, 107, 99, 0.86)"
        : tile.type === "deliver"
        ? "rgba(117, 232, 178, 0.84)"
        : collected
        ? "rgba(126, 140, 160, 0.82)"
        : "rgba(131, 183, 255, 0.86)";
      ctx.fillRect(x + 2, y + 2, cellSize - 4, cellSize - 4);
      ctx.strokeStyle = focused ? "#ffeaa7" : "rgba(245, 250, 255, 0.55)";
      ctx.lineWidth = focused ? 2 : 1;
      ctx.strokeRect(x + 2.5, y + 2.5, cellSize - 5, cellSize - 5);
      ctx.fillStyle = "rgba(8, 16, 27, 0.95)";
      ctx.font = "700 9px monospace";
      ctx.textAlign = "center";
      ctx.fillText(crop(tile.label, 7), x + Math.floor(cellSize / 2), y + Math.floor(cellSize / 2) + 3);
      pushHotspot(x, y, cellSize, cellSize, "interact-tile", tile.id);
    });

    const avatarX = originX + layout.avatarX * cellSize + Math.floor(cellSize / 2);
    const avatarY = originY + layout.avatarY * cellSize + Math.floor(cellSize / 2);
    drawAvatar(avatarX, avatarY, Math.max(18, Math.floor(cellSize * 0.72)));

    drawPill(
      zone.label.toUpperCase() + " " + i18nInteriorSuffix,
      18,
      18,
      "rgba(8, 18, 31, 0.9)",
      withAlpha(accent, 0.45)
    );
    const backButton = drawPill(
      i18nBackTown,
      18,
      52,
      "rgba(8, 18, 31, 0.9)",
      withAlpha(accent, 0.45)
    );
    pushHotspot(backButton.x, backButton.y, backButton.width, backButton.height, "back-town", null);

    const sectionButton = drawPill(
      i18nOpenSection,
      canvas.width - 150,
      18,
      "rgba(8, 18, 31, 0.9)",
      withAlpha(accent, 0.45)
    );
    pushHotspot(sectionButton.x, sectionButton.y, sectionButton.width, sectionButton.height, "open-section", zone.link);

    ctx.fillStyle = "rgba(230, 240, 252, 0.95)";
    ctx.font = "600 12px monospace";
    ctx.textAlign = "left";
    drawParagraph(crop(zone.desc, 180), 18, 92, canvas.width - 36, 14);

    const panelX = gridX + gridW + 18;
    const panelY = gridY;
    const panelH = gridH;
    ctx.fillStyle = "rgba(5, 12, 20, 0.52)";
    drawRoundedRectPath(panelX, panelY, panelW, panelH, 12);
    ctx.fill();
    ctx.strokeStyle = withAlpha(accent, 0.32);
    ctx.lineWidth = 1;
    drawRoundedRectPath(panelX, panelY, panelW, panelH, 12);
    ctx.stroke();

    ctx.fillStyle = "rgba(236, 245, 255, 0.95)";
    ctx.font = "700 12px monospace";
    ctx.fillText(i18nInteractHint, panelX + 12, panelY + 20);
    ctx.font = "600 11px monospace";
    ctx.fillStyle = "rgba(202, 222, 246, 0.9)";
    ctx.fillText(i18nInventoryLabel + ": " + state.inventoryCount, panelX + 12, panelY + 40);
    ctx.fillText(i18nDeliveriesLabel + ": " + state.deliveriesCount, panelX + 12, panelY + 56);

    const currentTile = layout.tiles.find((tile) => tile.x === layout.avatarX && tile.y === layout.avatarY);
    if (currentTile) {
      ctx.fillStyle = "rgba(255, 228, 173, 0.95)";
      ctx.fillText("Action: " + currentTile.verb, panelX + 12, panelY + 78);
    }

    let cardY = panelY + 102;
    zone.items.slice(0, 3).forEach((item) => {
      const cardH = 58;
      if (cardY + cardH > panelY + panelH - 12) {
        return;
      }
      drawCard(item, panelX + 10, cardY, panelW - 20, cardH);
      cardY += cardH + 8;
    });
  }

  function drawScene() {
    state.hotspots = [];
    if (state.scene === "interior") {
      const zone = zones.find((candidate) => candidate.id === state.interiorZoneId);
      drawInteriorScene(zone);
      return;
    }
    drawTownScene();
  }

  function getCurrentInteriorLayout() {
    if (state.scene !== "interior" || !state.interiorLayout) {
      return null;
    }
    return state.interiorLayout;
  }

  function findInteriorTileById(tileId) {
    const layout = getCurrentInteriorLayout();
    if (!layout) {
      return null;
    }
    return layout.tiles.find((tile) => tile.id === tileId) || null;
  }

  function findInteriorTileAtAvatar() {
    const layout = getCurrentInteriorLayout();
    if (!layout) {
      return null;
    }
    return layout.tiles.find((tile) => tile.x === layout.avatarX && tile.y === layout.avatarY) || null;
  }

  function executeInteriorInteraction(tile) {
    if (!tile || state.scene !== "interior") {
      return;
    }
    const zone = zones.find((candidate) => candidate.id === state.interiorZoneId);
    if (!zone) {
      return;
    }

    if (tile.type === "exit") {
      exitToTown();
      return;
    }

    if (tile.type === "content") {
      const item = tile.item || {};
      showModal({
        title: item.title || zone.label,
        meta: item.meta || tile.verb || "",
        body: item.summary || zone.desc,
        link: item.link || ""
      });
      track("inspect", zone.id);
      return;
    }

    if (tile.type === "collect") {
      if (state.collected.has(tile.id)) {
        showModal({
          title: i18nActionTake,
          meta: zone.label,
          body: i18nCollectedAlready
        });
        return;
      }
      state.collected.add(tile.id);
      state.inventoryCount += 1;
      saveProgress();
      showModal({
        title: i18nActionCollect,
        meta: zone.label,
        body: i18nCollectedSuccess
      });
      track("collect", zone.id);
      updateUi();
      return;
    }

    if (tile.type === "deliver") {
      if (state.inventoryCount <= 0) {
        showModal({
          title: i18nActionDeliver,
          meta: zone.label,
          body: i18nDeliverEmpty
        });
        return;
      }
      const delivered = state.inventoryCount;
      state.deliveriesCount += delivered;
      state.inventoryCount = 0;
      saveProgress();
      showModal({
        title: i18nActionDeliver,
        meta: zone.label,
        body: i18nDeliverSuccess + " +" + delivered
      });
      track("deliver", zone.id);
      updateUi();
    }
  }

  function moveInteriorAvatar(dx, dy) {
    const layout = getCurrentInteriorLayout();
    if (!layout || state.modalOpen) {
      return;
    }
    const nextX = Math.max(0, Math.min(layout.cols - 1, layout.avatarX + dx));
    const nextY = Math.max(0, Math.min(layout.rows - 1, layout.avatarY + dy));
    if (nextX === layout.avatarX && nextY === layout.avatarY) {
      return;
    }
    layout.avatarX = nextX;
    layout.avatarY = nextY;
    drawScene();
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
    if (!selectedZone) {
      selectedNode.textContent = i18nSelectedNone;
    } else if (state.scene === "interior") {
      selectedNode.textContent = replaceToken(i18nSelectedPrefix, selectedZone.label + " · " + i18nInsideSuffix);
    } else {
      selectedNode.textContent = replaceToken(i18nSelectedPrefix, selectedZone.label);
    }

    visitedNode.textContent = replaceToken(i18nVisitedPrefix, state.visited.size);
    visitedCountNode.textContent = String(state.visited.size);
    visitedCountNode.setAttribute("aria-label", i18nVisitedCounter + ": " + state.visited.size);
    inventoryCountNode.textContent = String(state.inventoryCount);
    deliveriesCountNode.textContent = String(state.deliveriesCount);
    drawScene();
  }

  function enterZone(zone, options) {
    if (!zone) {
      return;
    }
    const force = Boolean(options && options.force);
    if (interactionLocked && !force) {
      return;
    }
    const shouldTrack = Boolean(options && options.track);
    state.selectedZoneId = zone.id;
    state.avatarX = zone.tileX;
    state.avatarY = zone.tileY;
    markVisited(zone, shouldTrack);
    state.scene = "interior";
    state.interiorZoneId = zone.id;
    state.interiorLayout = buildInteriorLayout(zone);
    hideModal();
    if (shouldTrack) {
      track("open", zone.id);
    }
    updateUi();
  }

  function exitToTown() {
    state.scene = "town";
    state.interiorZoneId = null;
    state.interiorLayout = null;
    hideModal();
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
    const threshold = Math.max(26, state.tileW * 0.66);
    return nearestDistance <= threshold ? nearest : null;
  }

  function detectStandingZone() {
    return zones.find((zone) => zone.tileX === state.avatarX && zone.tileY === state.avatarY) || null;
  }

  function moveAvatar(dx, dy) {
    if (interactionLocked || state.scene !== "town" || state.modalOpen) {
      return;
    }
    const nextX = Math.max(0, Math.min(state.mapSize - 1, state.avatarX + dx));
    const nextY = Math.max(0, Math.min(state.mapSize - 1, state.avatarY + dy));
    if (nextX === state.avatarX && nextY === state.avatarY) {
      return;
    }
    state.avatarX = nextX;
    state.avatarY = nextY;
    const standing = detectStandingZone();
    if (standing) {
      enterZone(standing, { track: true });
      return;
    }
    drawScene();
  }

  function findHotspot(x, y) {
    for (let index = state.hotspots.length - 1; index >= 0; index -= 1) {
      const item = state.hotspots[index];
      if (
        x >= item.x &&
        x <= item.x + item.width &&
        y >= item.y &&
        y <= item.y + item.height
      ) {
        return item;
      }
    }
    return null;
  }

  function handleHotspot(hit) {
    if (!hit) {
      return;
    }
    if (hit.action === "back-town") {
      exitToTown();
      return;
    }
    if (hit.action === "open-section" && hit.payload) {
      window.location.href = hit.payload;
      return;
    }
    if (hit.action === "open-link" && hit.payload) {
      window.open(hit.payload, "_blank", "noopener,noreferrer");
      return;
    }
    if (hit.action === "interact-tile" && hit.payload) {
      const tile = findInteriorTileById(hit.payload);
      const layout = getCurrentInteriorLayout();
      if (tile && layout) {
        layout.avatarX = tile.x;
        layout.avatarY = tile.y;
        executeInteriorInteraction(tile);
      }
    }
  }

  function resizeCanvas() {
    const stage = root.querySelector(".beta-map-stage");
    const rect = stage ? stage.getBoundingClientRect() : canvas.getBoundingClientRect();
    const width = Math.max(360, Math.floor(rect.width));
    const height = Math.max(280, Math.floor(rect.height));
    canvas.width = width;
    canvas.height = height;
    updateMapScale();
    drawScene();
  }

  function bindEvents() {
    canvas.addEventListener("click", (event) => {
      const rect = canvas.getBoundingClientRect();
      const x = (event.clientX - rect.left) * (canvas.width / rect.width);
      const y = (event.clientY - rect.top) * (canvas.height / rect.height);

      if (state.scene === "interior") {
        const hit = findHotspot(x, y);
        if (hit) {
          handleHotspot(hit);
        } else {
          hideModal();
        }
        drawScene();
        return;
      }

      if (interactionLocked) {
        return;
      }
      const zone = nearestZoneAt(x, y);
      if (zone) {
        enterZone(zone, { track: true });
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

      if (state.scene === "interior") {
        if (state.modalOpen && (event.key === "Escape" || event.key === "Enter")) {
          event.preventDefault();
          hideModal();
          drawScene();
          return;
        }
        if (event.key === "Escape" || event.key === "Backspace") {
          event.preventDefault();
          exitToTown();
          return;
        }
        switch (event.key) {
          case "ArrowUp":
          case "w":
          case "W":
            event.preventDefault();
            moveInteriorAvatar(0, -1);
            return;
          case "ArrowDown":
          case "s":
          case "S":
            event.preventDefault();
            moveInteriorAvatar(0, 1);
            return;
          case "ArrowLeft":
          case "a":
          case "A":
            event.preventDefault();
            moveInteriorAvatar(-1, 0);
            return;
          case "ArrowRight":
          case "d":
          case "D":
            event.preventDefault();
            moveInteriorAvatar(1, 0);
            return;
          case "Enter":
          case "e":
          case "E":
            event.preventDefault();
            executeInteriorInteraction(findInteriorTileAtAvatar());
            drawScene();
            return;
          default:
            break;
        }
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

    if (interactionLocked) {
      resetBtn.disabled = true;
    }
    resetBtn.addEventListener("click", () => {
      if (interactionLocked) {
        return;
      }
      state.visited.clear();
      state.collected.clear();
      state.inventoryCount = 0;
      state.deliveriesCount = 0;
      saveVisited();
      saveProgress();
      hideModal();
      exitToTown();
    });

    modalClose.addEventListener("click", () => {
      hideModal();
      drawScene();
    });

    modal.addEventListener("click", (event) => {
      if (event.target === modal) {
        hideModal();
        drawScene();
      }
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
    state.selectedZoneId = initialZone.id;
  }
  updateUi();
})();
