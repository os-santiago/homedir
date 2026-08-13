# Diagnóstico de Performance de la UI

**Issue**: [#1374](https://github.com/os-santiago/homedir/issues/1374)
**Estado**: Diagnóstico preliminar (Fase 1 — análisis estático)
**Autor**: Sebithaz-dev
**Fecha**: 2026-08-13

> Este es un documento de **investigación**. No implementa optimizaciones; registra hallazgos
> del análisis estático del código y propone mejoras basadas en evidencia que se manejarán
> en issues/PRs separados.

## 1. Resumen Ejecutivo

HomeDir presenta una lentitud percibida en los efectos visuales y la carga de la UI. El
análisis estático de los assets front-end publicados revela dos preocupaciones principales:

1. **Carga de assets globales y monolíticos** — `core-bundle.js` (50.5 KB) más cinco hojas
   de estilo (lideradas por `homedir.css` con 138.3 KB) se cargan en **todas** las páginas
   mediante el layout base, estén o no en uso.
2. **Un efecto parallax no acelerado por GPU** — `bannerParallax()` escribe
   `backgroundPositionX` directamente en cada evento `scroll` sin `requestAnimationFrame`
   ni throttling, forzando un repaint del banner en cada frame de scroll. Esta es la causa
   más probable del síntoma de "efectos entrecortados / lentos".

Los valores base de Core Web Vitals (LCP < 2.5s, FID < 100ms, CLS < 0.1, FCP < 1.8s,
TTI < 3.8s) son objetivos; las mediciones reales de Lighthouse/PageSpeed están pendientes
y se registrarán en la entrega formal de benchmark.

## 2. Inventario de Assets Medido

### 2.1 JavaScript (`META-INF/resources/js/`)

| Bundle | Tamaño | Líneas | Se carga en |
|--------|--------|--------|-------------|
| `community-bundle.js` | 76.9 KB | 2242 | Página Community |
| `core-bundle.js` | 50.5 KB | 1373 | **Todas las páginas** (layout base) |
| `community-content.js` | 46.2 KB | 1388 | Community |
| `beta-map.js` | 37.9 KB | 1211 | Beta |
| `app.js` | 23.5 KB | 658 | fuente (no bundle) |
| `retro-theme.js` | 20.5 KB | 518 | páginas de tema retro |
| `home-lightning.js` | 17.3 KB | 487 | Home |
| `notifications-center.js` | 11.6 KB | 345 | Notificaciones |

### 2.2 CSS (`META-INF/resources/css/`)

| Hoja de estilo | Tamaño | Líneas |
|----------------|--------|--------|
| `homedir.css` | **138.3 KB** | 6539 |
| `retro-theme.css` | 65.5 KB | 3593 |
| `community-page.css` | 31.2 KB | 1372 |
| `achievements.css` | 8.2 KB | 431 |
| `beta-map.css` | 6.9 KB | 368 |

### 2.3 Layout base (`layout/main.html`)

El `<head>` carga siempre: `styles.css`, `tokens.css`, `retro-theme.css`, `homedir.css`,
`notifications.css` — además de Google Fonts (Orbitron, Exo 2, Material Symbols) con
`display=swap`. El `<body>` carga siempre `utils.js` y `core-bundle.js` con `defer`.

## 3. Hallazgos

### 3.1 Parallax escribe a `backgroundPositionX` en scroll (Impacto alto)

En `core-bundle.js`:

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

Problemas:
- **Escribe `backgroundPositionX` en cada evento `scroll`.** Esta propiedad no se compone en
  la capa GPU (a diferencia de `transform`), por lo que cada escritura provoca un **repaint**
  del banner en cada frame de scroll.
- **Sin `requestAnimationFrame` ni throttle.** El handler corre tantas veces como el
  navegador emita `scroll` (potencialmente mucho más de 60 fps), causando trabajo redundante
  y jank.
- **Se ejecuta en todas las páginas.** `core-bundle.js` es global; el handler se adjunta a
  `scroll` incondicionalmente (salvo en `ultraLiteMode`).

Medido en `core-bundle.js`: 2 usos de `requestAnimationFrame` (ambos para transiciones de
vistas), 1 listener de `scroll`, 1 escritura de `backgroundPosition` — la ruta de scroll
**no** está throttled con rAF.

**Fix recomendado:** mover el efecto a `transform: translateY()` (acelerado por GPU) y
envolver el handler en `requestAnimationFrame` con throttle, o saltar el efecto si no hay
banner presente.

### 3.2 Assets globales monolíticos (Impacto medio-alto)

- `core-bundle.js` (50.5 KB) se carga en todas las páginas aunque no se use. Candidato a
  code-splitting / carga on-demand.
- `homedir.css` (138.3 KB) + 4 hojas más bloquean el render en `<head>`. Candidato a
  inlining de CSS crítico (above-the-fold) y división por ruta.
- Medido en `homedir.css`: 3 `@keyframes`, 38 declaraciones `transition:`, 9 declaraciones
  `animation:` — una gran superficie de estilos a auditar por reglas sin uso y sobre-animación.

### 3.3 Google Fonts (Impacto medio)

Se cargan tres familias (Orbitron, Exo 2, Material Symbols) con `display=swap`. Orbitron es
una fuente display y puede añadir peso. Verificar el comportamiento de `font-display` y
precargar la fuente crítica para reducir el impacto en FCP.

### 3.4 Observaciones positivas

- Las imágenes ya usan `loading="lazy" decoding="async" fetchpriority="low"` en algunos
  templates (ej. `community-board`).
- `core-bundle.js` / `utils.js` usan `defer`, por lo que no bloquean el parseo.

## 4. Roadmap de Optimización Propuesto (a cuantificar)

### Quick wins

| # | Optimización | Impacto esperado | Esfuerzo |
|---|--------------|------------------|----------|
| 1 | Acelerar por GPU + rAF-throttle el parallax | Elimina el jank de scroll | Bajo |
| 2 | Inline de CSS crítico above-the-fold | Mejora FCP/LCP | Medio |
| 3 | Precargar fuente crítica + refinar `font-display` | Mejora FCP/LCP | Bajo |

### Largo plazo

| # | Optimización | Impacto esperado | Esfuerzo |
|---|--------------|------------------|----------|
| 4 | Code-split de `core-bundle.js` por ruta | Reduce bytes en la mayoría de páginas | Alto |
| 5 | Dividir/recortar `homedir.css` (138 KB) | Reduce bytes bloqueantes de render | Alto |
| 6 | Eliminar reglas CSS/animaciones sin uso | Reduce bytes + jank | Medio |

## 5. Próximos Pasos (para completar Fases 1-2)

1. Ejecutar **Lighthouse** (desktop/mobile) y **PageSpeed Insights** en staging/prod;
   registrar la línea base de Core Web Vitals (LCP/FID/CLS/FCP/TTI).
2. Capturar un perfil de **DevTools Performance** de una sesión de scroll y anotar una
   captura del **Network waterfall**.
3. Confirmar el impacto del parallax con un perfil de scroll (antes/después).
4. Entregar el reporte de diagnóstico completo + propuesta priorizada con estimaciones de
   impacto-vs-esfuerzo y un plan de implementación.

## 6. Referencias

- Issue: [#1374](https://github.com/os-santiago/homedir/issues/1374)
- `quarkus-app/src/main/resources/META-INF/resources/js/core-bundle.js`
- `quarkus-app/src/main/resources/META-INF/resources/css/homedir.css`
- `quarkus-app/src/main/resources/templates/layout/main.html`
