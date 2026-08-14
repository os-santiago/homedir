# UI Performance Diagnostic

**Issue**: [#1374](https://github.com/os-santiago/homedir/issues/1374)
**Status**: Phases 1-2 complete (static analysis + live production measurement + Lighthouse Core Web Vitals)
**Author**: Sebithaz-dev
**Date**: 2026-08-13 (updated 2026-08-13 with Lighthouse benchmark and corrected compression finding)

> This is a **research** document. It does not implement optimizations; it records findings
> from static code analysis and live measurement of the production site, and proposes
> evidence-based improvements to be handled in separate issues/PRs.

## 1. Executive Summary

HomeDir presents a perceived sluggishness in visual effects and UI loading. Static analysis
of the shipped front-end assets, live measurement of the production site
(`https://homedir.opensourcesantiago.io`) and a Lighthouse core-web-vitals benchmark reveal
four primary concerns:

1. **A single 3.8 MB icon font dominates the page payload.** The Material Symbols Outlined
   variable font (all axes, `opsz,wght,FILL,GRAD`) is loaded on **every** page via Google
   Fonts and transfers **3.9 MB** — roughly **88% of the total ~4 MB** page weight
   (Lighthouse "enormous network payloads"; see [§3.3](#33-material-symbols-icon-font-is-the-dominant-payload-high-impact)).
2. **Global, monolithic asset loading** — `core-bundle.js` (50.5 KB raw) plus five
   stylesheets (led by `homedir.css` at 138.3 KB raw) are loaded on **every** page via the
   base layout, regardless of whether the page uses that functionality. A Lighthouse unused
   CSS audit shows 87-93% of those stylesheets are wasted bytes.
3. **A non-GPU-accelerated parallax effect** — `bannerParallax()` writes
   `backgroundPositionX` directly on every `scroll` event without `requestAnimationFrame`
   or throttling, forcing a repaint of the banner on each scroll frame. This is the most
   likely source of the "choppy / sluggish effects" symptom.
4. **Correction to Phase 1:** assets **are** served with Brotli/`br` compression at the
   CDN layer (`Content-Encoding: br`, verified live with an `Accept-Encoding: gzip, br`
   request). The Phase 1 "uncompressed" finding was a false negative caused by probing
   without the `Accept-Encoding` header. See [§3.5](#35-compression-correction-brotli-is-enabled-update-to-phase-1).

Lighthouse benchmark medians (3 runs each): **55 / 100 desktop-mobile performance** —
mobile LCP **22.5 s** (simulated), desktop LCP **4.3 s**, both far above the **< 2.5 s**
target. Observed (un-throttled) FCP/LCP are much lower (~1.6 s), which isolates the problem
to network transfer weight under real mobile conditions, dominated by the icon font. See
[§4](#4-lighthouse-core-web-vitals-benchmark).

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

### 2.4 Live production measurements (2026-08-13)

Captured from `https://homedir.opensourcesantiago.io` (HTTP 200). Two size columns are
reported: the **raw** on-disk size and the **actual transfer size** (Brotli-compressed,
verified with `Accept-Encoding: gzip, br` — see [§3.5](#35-compression-correction-brotli-is-enabled-update-to-phase-1)).

| Asset | Raw size | Transfer (Brotli) | Download time |
|-------|----------|-------------------|---------------|
| `styles.css` | 64.2 KB | **11.7 KB** | 0.22 s |
| `retro-theme.css` | 63.4 KB | **12.1 KB** | 0.21 s |
| `homedir.css` | 135.1 KB | **22.3 KB** | 0.23 s |
| `tokens.css` | 1.4 KB | **0.7 KB** | 0.31 s |
| `notifications.css` | 6.6 KB | **1.8 KB** | 0.27 s |
| `core-bundle.js` | 50.4 KB | **13.5 KB** | 0.23 s |
| `utils.js` | 1.8 KB | **0.8 KB** | 0.24 s |

**Home page CSS + JS transfer total ≈ 63 KB** (Brotli) vs. ~315 KB raw, loaded on every
page via the base layout. Compression is already active, so CSS/JS transfer weight is not
the load bottleneck; see [§3.5](#35-compression-correction-brotli-is-enabled-update-to-phase-1) and the
dominant icon-font finding in [§3.3](#33-material-symbols-icon-font-is-the-dominant-payload-high-impact).

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

### 3.3 Material Symbols icon font is the dominant payload (High impact)

Measured live on the home page (Lighthouse `network-requests`):

- `materialsymbolsoutlined/v364/...woff2` → **3,965,249 bytes (~3.8 MB)** — one request
  making up **~88% of the total ~4 MB page weight**.
- Loaded via Google Fonts CSS:
  `https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200`
  which requests the full **variable font** across all three axes (`opsz`, `wght`, `FILL`,
  `GRAD`) — the largest possible build.
- It is referenced on **every page** (base layout `<head>`), so the ~3.8 MB transfers
  site-wide, on mobile connections it becomes the effective LCP bottleneck.

Lighthouse `total-byte-weight` flags "Avoid enormous network payloads" (4,028 KiB total,
threshold 2,592 KiB) and `font-display-insight` estimates ~580 ms savings on that font alone.

**Recommended fix:** stop loading the full variable font. Options: (a) self-host a subsetted
static build with only the glyphs/settings actually used, (b) load a static axis value (e.g.
`opsz@24` + `wght@400` only), or (c) replace Material Symbols with inline SVG icons. This is
the single highest-leverage quick win for LCP/FCP on mobile.

### 3.4 Google Fonts (Medium impact)

Three families (Orbitron, Exo 2, Material Symbols) are loaded with `display=swap`. The
Material Symbols variable font is the dominant weight (see [§3.3](#33-material-symbols-icon-font-is-the-dominant-payload-high-impact));
Orbitron is a display font and can add weight. Verify `font-display` behavior and preload
the critical font to reduce FCP impact.

### 3.5 Compression correction: Brotli is enabled (Update to Phase 1)

Phase 1 reported assets were served without `Content-Encoding`. Re-verified live with an
explicit `Accept-Encoding: gzip, br` request, the CDN returns **`Content-Encoding: br`**
(Brotli) for all CSS/JS, with `Vary: accept-encoding`:

```
GET /css/homedir.css      → 200  content-encoding: br  (22.3 KB transfer)
GET /js/core-bundle.js    → 200  content-encoding: br  (13.5 KB transfer)
```

The Phase 1 check was a false negative because the probe did not send `Accept-Encoding`.
Brotli already reduces transfer size by ~70-80% vs. raw. **No server work is needed for
compression**; the remaining byte weight is the icon font (see [§3.3](#33-material-symbols-icon-font-is-the-dominant-payload-high-impact)),
not CSS/JS text.

### 3.6 Positive observations

- Images already use `loading="lazy" decoding="async" fetchpriority="low"` in some
  templates (e.g. `community-board`).
- `core-bundle.js` / `utils.js` use `defer`, so they do not block parsing.

## 4. Lighthouse Core Web Vitals Benchmark

Methodology: Lighthouse v13.4.1 (Chrome 152 for Testing, `--only-categories=performance`),
local runs against `https://homedir.opensourcesantiago.io`. **3 runs per form factor**;
medians below. Desktop uses `--preset=desktop` (no network/CPU throttling simulation);
mobile uses the default mobile emulation (4x CPU + Slow 4G throttling simulation). The
PageSpeed Insights API remained rate-limited (HTTP 429 without an API key).

| Metric | Target | Mobile (median) | Desktop (median) |
|--------|--------|-----------------|------------------|
| Performance score | ≥ 90 | **55** | **59** |
| FCP | < 1.8 s | 22.3 s (sim) / 1.6 s (obs) | 4.2 s (sim) / 1.5 s (obs) |
| LCP | < 2.5 s | **22.5 s (sim)** / 1.6 s (obs) | **4.3 s (sim)** / 1.5 s (obs) |
| TTI | < 3.8 s | 22.5 s (sim) | 4.3 s (sim) |
| TBT | < 200 ms | 0 ms | 0 ms |
| CLS | < 0.1 | 0.000 | 0.000 |
| SI | < 3.4 s | 22.3 s (sim) | 4.2 s (sim) |
| TTFB | < 600 ms | 0.17 s | 0.25 s |

*(sim = simulated throttle; obs = observed, un-throttled local connection)*

Key observations:

- **Mobile LCP/TTI ≈ 22.5 s under simulated Slow-4G** — far above target. The dominant
  contributor is the **3.8 MB Material Symbols font** (`total-byte-weight` = 4,028 KiB,
  "enormous network payload" flagged; single font = 88%). The `lcp-breakdown-insight`
  confirms TTFB (590 ms) + element render delay (1,124 ms) around the hero text element.
- **Observed FCP/LCP ~1.6 s** (un-throttled) shows server/data rendering are not slow — the
  bottleneck is transfer weight under real mobile bandwidth, matching the perceived
  "it's slow to load" symptom.
- **CLS = 0 and TBT = 0** on both — no layout shift and no long blocking tasks, so the
  sluggishness is **not** from render-blocking JS. This reinforces that the primary fix is
  payload reduction (icon font), not script optimization.
- **Unused CSS is heavy**: Lighthouse reports 87-93% wasted bytes on the three global
  stylesheets (`homedir.css` 87%, `retro-theme.css` 92%, `styles.css` 93%) ≈ 44 KiB after
  Brotli — a laziness-behind-monolithic-CSS issue, medium-term code-split target.
- Lighthouse found 2 long main-thread tasks (mobile) of ~115-121 ms during load — minor,
  below TBT-reporting threshold.

## 5. Proposed Optimization Roadmap (ranked by measured impact)

### Quick wins

| # | Optimization | Expected impact | Effort |
|---|--------------|-----------------|--------|
| 1 | Replace/subset the 3.8 MB Material Symbols variable font (static build or inline SVG) | Removes ~88% of page weight → biggest LCP/FCP gain on mobile | Low-Med |
| 2 | GPU-accelerate + rAF-throttle the parallax | Removes scroll jank | Low |
| 3 | Preload critical font + refine `font-display` | Improves FCP/LCP | Low |
| 4 | Inline critical CSS for above-the-fold | Improves FCP/LCP | Medium |

> Note: "enable gzip/brotli" from Phase 1 is **removed** — compression is already active
> (§3.5). It was a false positive.

### Long-term

| # | Optimization | Expected impact | Effort |
|---|--------------|-----------------|--------|
| 5 | Code-split `core-bundle.js` by route | Reduces bytes on most pages | High |
| 6 | Split/trim `homedir.css` (138 KB raw / 22 KB Brotli) | Reduces render-blocking bytes + unused CSS | High |
| 7 | Remove unused CSS/animation rules (87-93% wasted) | Reduces bytes + jank | Medium |

## 6. Next Steps (Phase 2 complete / issue closure)

1. ✅ **Live network baseline captured** (2026-08-13): transfer sizes and download times for
   the global CSS/JS assets (see [§2.4](#24-live-production-measurements-2026-08-13)).
2. ✅ **Lighthouse benchmark captured** (2026-08-13): Core Web Vitals per §4, mobile +
   desktop, 3-run medians. PSI API rate-limited (HTTP 429 without a key) — optional future
   follow-up with an API key.
3. ✅ **Network waterfall captured** (§2.4, §3.3): per-asset transfer sizes and load order;
   documented as tables to avoid binary screenshots (repo policy).
4. ✅ **Parallax confirmed** with scroll data (§3.1): `backgroundPositionX` written every
   `scroll` frame without rAF — fix goes to a dedicated implementation issue/PR.
5. **Implementation issues/PRs to create** (per item in the §5 roadmap): icon-font subset,
   parallax fix, critical-CSS inline, code-splitting. This document closes the *research*
   deliverable; the optimizations are tracked separately.

## 7. References

- Issue: [#1374](https://github.com/os-santiago/homedir/issues/1374)
- `quarkus-app/src/main/resources/META-INF/resources/js/core-bundle.js`
- `quarkus-app/src/main/resources/META-INF/resources/css/homedir.css`
- `quarkus-app/src/main/resources/templates/layout/main.html`
- Lighthouse v13.4.1 (Chrome 152 for Testing) — 3-run medians, 2026-08-13
