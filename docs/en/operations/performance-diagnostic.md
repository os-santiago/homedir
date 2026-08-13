# UI Performance Diagnostic

**Issue**: [#1374](https://github.com/os-santiago/homedir/issues/1374)
**Status**: Preliminary diagnosis (Phase 1 — static analysis)
**Author**: Sebithaz-dev
**Date**: 2026-08-13

> This is a **research** document. It does not implement optimizations; it records findings
> from static code analysis and proposes evidence-based improvements to be handled in
> separate issues/PRs.

## 1. Executive Summary

HomeDir presents a perceived sluggishness in visual effects and UI loading. Static analysis
of the shipped front-end assets reveals two primary concerns:

1. **Global, monolithic asset loading** — `core-bundle.js` (50.5 KB) plus five stylesheets
   (led by `homedir.css` at 138.3 KB) are loaded on **every** page via the base layout,
   regardless of whether the page uses that functionality.
2. **A non-GPU-accelerated parallax effect** — `bannerParallax()` writes
   `backgroundPositionX` directly on every `scroll` event without `requestAnimationFrame`
   or throttling, forcing a repaint of the banner on each scroll frame. This is the most
   likely source of the "choppy / sluggish effects" symptom.

Core Web Vitals baselines (LCP < 2.5s, FID < 100ms, CLS < 0.1, FCP < 1.8s, TTI < 3.8s)
are targets; actual Lighthouse/PageSpeed measurements are pending and will be recorded in
the formal benchmark deliverable.

## 2. Measured Asset Inventory

### 2.1 JavaScript (`META-INF/resources/js/`)

| Bundle | Size | Lines | Loaded on |
|--------|------|-------|-----------|
| `community-bundle.js` | 76.9 KB | 2242 | Community page |
| `core-bundle.js` | 50.5 KB | 1373 | **Every page** (base layout) |
| `community-content.js` | 46.2 KB | 1388 | Community |
| `beta-map.js` | 37.9 KB | 1211 | Beta |
| `app.js` | 23.5 KB | 658 | source (not a bundle) |
| `retro-theme.js` | 20.5 KB | 518 | retro theme pages |
| `home-lightning.js` | 17.3 KB | 487 | Home |
| `notifications-center.js` | 11.6 KB | 345 | Notifications |

### 2.2 CSS (`META-INF/resources/css/`)

| Stylesheet | Size | Lines |
|------------|------|-------|
| `homedir.css` | **138.3 KB** | 6539 |
| `retro-theme.css` | 65.5 KB | 3593 |
| `community-page.css` | 31.2 KB | 1372 |
| `achievements.css` | 8.2 KB | 431 |
| `beta-map.css` | 6.9 KB | 368 |

### 2.3 Base layout (`layout/main.html`)

The `<head>` always loads: `styles.css`, `tokens.css`, `retro-theme.css`, `homedir.css`,
`notifications.css` — plus Google Fonts (Orbitron, Exo 2, Material Symbols) with
`display=swap`. The `<body>` always loads `utils.js` and `core-bundle.js` with `defer`.

## 3. Findings

### 3.1 Parallax writes to `backgroundPositionX` on scroll (High impact)

In `core-bundle.js`:

```js
function bannerParallax() {
    if (isUltraLiteMode()) return;
    const banner = $('banner');
    if (banner) {
        banner.style.backgroundPositionX = (window.scrollY * 0.3) + 'px';
    }
}
// ...
scrollHandler = bannerParallax;
window.addEventListener('scroll', scrollHandler);
```

Problems:
- **Writes `backgroundPositionX` on every `scroll` event.** This property is not composited
  on the GPU layer (unlike `transform`), so each write triggers a **repaint** of the banner
  on every scroll frame.
- **No `requestAnimationFrame` or throttle.** The handler runs as often as the browser
  emits `scroll` (potentially far more than 60 fps), causing redundant work and jank.
- **Runs on every page.** `core-bundle.js` is global; the handler is attached to `scroll`
  unconditionally (unless `ultraLiteMode`).

Measured in `core-bundle.js`: 2 `requestAnimationFrame` uses (both for view transitions),
1 `scroll` listener, 1 `backgroundPosition` write — the scroll path is **not** rAF-throttled.

**Recommended fix:** move the effect to `transform: translateY()` (GPU-accelerated) and wrap
the handler in `requestAnimationFrame` with a throttle, or skip the effect entirely when no
banner is present.

### 3.2 Monolithic global assets (Medium-High impact)

- `core-bundle.js` (50.5 KB) is loaded on every page even when unused. Candidate for
  code-splitting / on-demand loading.
- `homedir.css` (138.3 KB) + 4 other stylesheets block render in `<head>`. Candidate for
  critical-CSS inlining (above-the-fold) and splitting by route.
- Measured in `homedir.css`: 3 `@keyframes`, 38 `transition:` declarations, 9 `animation:`
  declarations — a large style surface to audit for unused rules and over-animation.

### 3.3 Google Fonts (Medium impact)

Three families (Orbitron, Exo 2, Material Symbols) are loaded with `display=swap`.
Orbitron is a display font and can add weight. Verify `font-display` behavior and preload
the critical font to reduce FCP impact.

### 3.4 Positive observations

- Images already use `loading="lazy" decoding="async" fetchpriority="low"` in some
  templates (e.g. `community-board`).
- `core-bundle.js` / `utils.js` use `defer`, so they do not block parsing.

## 4. Proposed Optimization Roadmap (to be quantified)

### Quick wins

| # | Optimization | Expected impact | Effort |
|---|--------------|-----------------|--------|
| 1 | GPU-accelerate + rAF-throttle the parallax | Removes scroll jank | Low |
| 2 | Inline critical CSS for above-the-fold | Improves FCP/LCP | Medium |
| 3 | Preload critical font + refine `font-display` | Improves FCP/LCP | Low |

### Long-term

| # | Optimization | Expected impact | Effort |
|---|--------------|-----------------|--------|
| 4 | Code-split `core-bundle.js` by route | Reduces bytes on most pages | High |
| 5 | Split/trim `homedir.css` (138 KB) | Reduces render-blocking bytes | High |
| 6 | Remove unused CSS/animation rules | Reduces bytes + jank | Medium |

## 5. Next Steps (to complete Phases 1-2)

1. Run **Lighthouse** (desktop/mobile) and **PageSpeed Insights** on staging/prod; record
   baseline Core Web Vitals (LCP/FID/CLS/FCP/TTI).
2. Capture a **DevTools Performance** profile of a scrolling session and annotate a
   **Network waterfall** screenshot.
3. Confirm the parallax impact with a scroll profile (before/after).
4. Deliver the full diagnostic report + prioritized proposal with impact-vs-effort
   estimates and an implementation plan.

## 6. References

- Issue: [#1374](https://github.com/os-santiago/homedir/issues/1374)
- `quarkus-app/src/main/resources/META-INF/resources/js/core-bundle.js`
- `quarkus-app/src/main/resources/META-INF/resources/css/homedir.css`
- `quarkus-app/src/main/resources/templates/layout/main.html`
