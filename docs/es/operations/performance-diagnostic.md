# Diagnóstico de Performance de la UI

**Issue**: [#1374](https://github.com/os-santiago/homedir/issues/1374)
**Estado**: Fases 1-2 completadas (análisis estático + medición en producción + benchmark Lighthouse Core Web Vitals)
**Autor**: Sebithaz-dev
**Fecha**: 2026-08-13 (actualizado 2026-08-13 con benchmark Lighthouse y hallazgo de compresión corregido)

> Este es un documento de **investigación**. No implementa optimizaciones; registra hallazgos
> del análisis estático del código y de la medición en vivo del sitio de producción, y
> propone mejoras basadas en evidencia que se manejarán en issues/PRs separados.

## 1. Resumen Ejecutivo

HomeDir presenta una lentitud percibida en los efectos visuales y la carga de la UI. El
análisis estático de los assets front-end publicados, la medición en vivo del sitio de
producción (`https://homedir.opensourcesantiago.io`) y un benchmark Lighthouse de Core Web
Vitals revelan cuatro preocupaciones principales:

1. **Un único iconfont de 3.8 MB domina el payload de la página.** La fuente variable
   Material Symbols Outlined (todos los ejes, `opsz,wght,FILL,GRAD`) se carga en **todas**
   las páginas vía Google Fonts y transfiere **3.9 MB** — aproximadamente **88% del peso
   total de ~4 MB** de la página (auditoría "enormous network payloads"; ver
   [§3.3](#33-el-iconfont-material-symbols-es-el-payload-dominante-impacto-alto)).
2. **Carga de assets globales y monolíticos** — `core-bundle.js` (50.5 KB brutos) más cinco
   hojas de estilo (lideradas por `homedir.css` con 138.3 KB brutos) se cargan en **todas**
   las páginas mediante el layout base, estén o no en uso. La auditoría de CSS no utilizado
   de Lighthouse muestra 87-93% de bytes desperdiciados en esas hojas.
3. **Un efecto parallax no acelerado por GPU** — `bannerParallax()` escribe
   `backgroundPositionX` directamente en cada evento `scroll` sin `requestAnimationFrame`
   ni throttling, forzando un repaint del banner en cada frame de scroll. Esta es la causa
   más probable del síntoma de "efectos entrecortados / lentos".
4. **Corrección a la Fase 1:** los assets **sí** se sirven con compresión Brotli/`br` en la
   capa CDN (`Content-Encoding: br`, verificado en vivo con una petición
   `Accept-Encoding: gzip, br`). El hallazgo "sin compresión" de la Fase 1 fue un falso
   negativo por sondear sin la cabecera `Accept-Encoding`. Ver [§3.5](#35-corrección-de-compresión-brotli-habilitado-actualización-a-la-fase-1).

Medianas del benchmark Lighthouse (3 corridas por formato): **55 / 100 en rendimiento
móvil** — LCP móvil **22.5 s** (simulado), LCP desktop **4.3 s**, ambas muy por encima del
objetivo **< 2.5 s**. El FCP/LCP observado (sin throttle) es mucho menor (~1.6 s), lo que
aisla el problema al peso de transferencia bajo ancho de banda móvil real, dominado por el
iconfont. Ver [§4](#4-benchmark-lighthouse-core-web-vitals).

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

### 2.4 Mediciones reales de producción (2026-08-13)

Capturadas de `https://homedir.opensourcesantiago.io` (HTTP 200). Se reportan dos columnas
de tamaño: el **bruto** en disco y el **tamaño de transferencia** real (comprimido con
Brotli, verificado con `Accept-Encoding: gzip, br` — ver [§3.5](#35-corrección-de-compresión-brotli-habilitado-actualización-a-la-fase-1)).

| Asset | Tamaño bruto | Transferencia (Brotli) | Tiempo de descarga |
|-------|--------------|------------------------|--------------------|
| `styles.css` | 64.2 KB | **11.7 KB** | 0.22 s |
| `retro-theme.css` | 63.4 KB | **12.1 KB** | 0.21 s |
| `homedir.css` | 135.1 KB | **22.3 KB** | 0.23 s |
| `tokens.css` | 1.4 KB | **0.7 KB** | 0.31 s |
| `notifications.css` | 6.6 KB | **1.8 KB** | 0.27 s |
| `core-bundle.js` | 50.4 KB | **13.5 KB** | 0.23 s |
| `utils.js` | 1.8 KB | **0.8 KB** | 0.24 s |

**Total CSS + JS de la home ≈ 63 KB** (Brotli) vs. ~315 KB brutos, cargado en todas las
páginas vía el layout base. La compresión ya está activa, por lo que el peso de
transferencia CSS/JS no es el cuello de botella de carga; ver [§3.5](#35-corrección-de-compresión-brotli-habilitado-actualización-a-la-fase-1)
y el hallazgo dominante del iconfont en [§3.3](#33-el-iconfont-material-symbols-es-el-payload-dominante-impacto-alto).

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

### 3.3 El iconfont Material Symbols es el payload dominante (Impacto alto)

Medido en vivo en la home (Lighthouse `network-requests`):

- `materialsymbolsoutlined/v364/...woff2` → **3,965,249 bytes (~3.8 MB)** — una sola petición
  que supone **~88% del peso total de ~4 MB** de la página.
- Cargado vía CSS de Google Fonts:
  `https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200`
  que solicita la **fuente variable** completa en los tres ejes (`opsz`, `wght`, `FILL`,
  `GRAD`) — la build más grande posible.
- Se referencia en **todas** las páginas (layout base en `<head>`), por lo que los ~3.8 MB
  se transfieren en todo el sitio; en conexiones móviles se convierte en el cuello de
  botella efectivo del LCP.

Lighthouse `total-byte-weight` marca "Avoid enormous network payloads" (4,028 KiB total,
umbral 2,592 KiB) y `font-display-insight` estima ~580 ms de ahorro solo en esa fuente.

**Fix recomendado:** dejar de cargar la fuente variable completa. Opciones: (a) alojar una
build estática recortada con solo los glifos/configuraciones realmente usados, (b) cargar un
valor estático de eje (p.ej. solo `opsz@24` + `wght@400`), o (c) reemplazar Material Symbols
por iconos SVG inline. Es el quick win de mayor apalancamiento para LCP/FCP en móvil.

### 3.4 Google Fonts (Impacto medio)

Se cargan tres familias (Orbitron, Exo 2, Material Symbols) con `display=swap`. El iconfont
variable Material Symbols es el peso dominante (ver [§3.3](#33-el-iconfont-material-symbols-es-el-payload-dominante-impacto-alto));
Orbitron es una fuente display y puede añadir peso. Verificar el comportamiento de
`font-display` y precargar la fuente crítica para reducir el impacto en FCP.

### 3.5 Corrección de compresión: Brotli habilitado (Actualización a la Fase 1)

La Fase 1 reportó assets sin `Content-Encoding`. Re-verificado en vivo con una petición
explícita `Accept-Encoding: gzip, br`, el CDN devuelve **`Content-Encoding: br`** (Brotli)
para todo el CSS/JS, con `Vary: accept-encoding`:

```
GET /css/homedir.css      → 200  content-encoding: br  (22.3 KB transferencia)
GET /js/core-bundle.js    → 200  content-encoding: br  (13.5 KB transferencia)
```

La comprobación de la Fase 1 fue un falso negativo porque la sonda no enviaba
`Accept-Encoding`. Brotli ya reduce el tamaño de transferencia ~70-80% vs. bruto.
**No hace falta trabajo de servidor para la compresión**; el peso restante es el iconfont
(ver [§3.3](#33-el-iconfont-material-symbols-es-el-payload-dominante-impacto-alto)), no el texto CSS/JS.

### 3.6 Observaciones positivas

- Las imágenes ya usan `loading="lazy" decoding="async" fetchpriority="low"` en algunos
  templates (ej. `community-board`).
- `core-bundle.js` / `utils.js` usan `defer`, por lo que no bloquean el parseo.

## 4. Benchmark Lighthouse Core Web Vitals

Metodología: Lighthouse v13.4.1 (Chrome 152 for Testing, `--only-categories=performance`),
corridas locales contra `https://homedir.opensourcesantiago.io`. **3 corridas por formato**;
medianas abajo. Desktop usa `--preset=desktop` (sin simulación de throttling de
red/CPU); mobile usa la emulación móvil por defecto (4x CPU + throttling Slow 4G simulado).
La API de PageSpeed Insights siguió rate-limited (HTTP 429 sin clave API).

| Métrica | Objetivo | Mobile (mediana) | Desktop (mediana) |
|---------|----------|------------------|-------------------|
| Score de rendimiento | ≥ 90 | **55** | **59** |
| FCP | < 1.8 s | 22.3 s (sim) / 1.6 s (obs) | 4.2 s (sim) / 1.5 s (obs) |
| LCP | < 2.5 s | **22.5 s (sim)** / 1.6 s (obs) | **4.3 s (sim)** / 1.5 s (obs) |
| TTI | < 3.8 s | 22.5 s (sim) | 4.3 s (sim) |
| TBT | < 200 ms | 0 ms | 0 ms |
| CLS | < 0.1 | 0.000 | 0.000 |
| SI | < 3.4 s | 22.3 s (sim) | 4.2 s (sim) |
| TTFB | < 600 ms | 0.17 s | 0.25 s |

*(sim = throttling simulado; obs = observado, conexión local sin throttle)*

Observaciones clave:

- **LCP/TTI móvil ≈ 22.5 s bajo Slow-4G simulado** — muy por encima del objetivo. El
  contribuyente dominante es el **iconfont Material Symbols de 3.8 MB** (`total-byte-weight`
  = 4,028 KiB, marcado "enormous network payload"; una sola fuente = 88%). El
  `lcp-breakdown-insight` confirma TTFB (590 ms) + retardo de render del elemento (1,124 ms)
  alrededor del texto del hero.
- **FCP/LCP observado ~1.6 s** (sin throttle) muestra que el render del servidor/datos no
  es lento — el cuello de botella es el peso de transferencia bajo ancho de banda móvil
  real, consistente con el síntoma percibido de "carga lenta".
- **CLS = 0 y TBT = 0** en ambos — sin layout shift ni tareas bloqueantes largas, por lo que
  la lentitud **no** proviene de JS bloqueante de render. Esto refuerza que el fix principal
  es reducción de payload (iconfont), no optimización de scripts.
- **CSS sin uso es alto**: Lighthouse reporta 87-93% de bytes desperdiciados en las tres
  hojas globales (`homedir.css` 87%, `retro-theme.css` 92%, `styles.css` 93%) ≈ 44 KiB tras
  Brotli — reflejo del CSS monolítico, objetivo de code-split a medio plazo.
- Lighthouse encontró 2 tareas largas de main-thread (mobile) de ~115-121 ms durante la
  carga — menor, por debajo del umbral de reporte de TBT.

## 5. Roadmap de Optimización Propuesto (ordenado por impacto medido)

### Quick wins

| # | Optimización | Impacto esperado | Esfuerzo |
|---|--------------|------------------|----------|
| 1 | Reemplazar/recortar el iconfont Material Symbols de 3.8 MB (build estática o SVG inline) | Elimina ~88% del peso de página → mayor ganancia de LCP/FCP en móvil | Bajo-Medio |
| 2 | Acelerar por GPU + rAF-throttle el parallax | Elimina el jank de scroll | Bajo |
| 3 | Precargar fuente crítica + refinar `font-display` | Mejora FCP/LCP | Bajo |
| 4 | Inline de CSS crítico above-the-fold | Mejora FCP/LCP | Medio |

> Nota: "habilitar gzip/brotli" de la Fase 1 se **elimina** — la compresión ya está activa
> (§3.5). Fue un falso positivo.

### Largo plazo

| # | Optimización | Impacto esperado | Esfuerzo |
|---|--------------|------------------|----------|
| 5 | Code-split de `core-bundle.js` por ruta | Reduce bytes en la mayoría de páginas | Alto |
| 6 | Dividir/recortar `homedir.css` (138 KB brutos / 22 KB Brotli) | Reduce bytes bloqueantes de render + CSS sin uso | Alto |
| 7 | Eliminar reglas CSS/animaciones sin uso (87-93% desperdiciado) | Reduce bytes + jank | Medio |

## 6. Próximos Pasos (Fase 2 completa / cierre del issue)

1. ✅ **Línea base de red capturada** (2026-08-13): tamaños de transferencia y tiempos de
   descarga de los assets CSS/JS globales (ver [§2.4](#24-mediciones-reales-de-producción-2026-08-13)).
2. ✅ **Benchmark Lighthouse capturado** (2026-08-13): Core Web Vitals según §4, mobile +
   desktop, medianas de 3 corridas. API de PSI rate-limited (HTTP 429 sin clave) — posible
   seguimiento futuro con clave API.
3. ✅ **Network waterfall capturado** (§2.4, §3.3): tamaños de transferencia y orden de
   carga por asset; documentado en tablas para evitar capturas binarias (política del repo).
4. ✅ **Parallax confirmado** con datos de scroll (§3.1): `backgroundPositionX` escrito en
   cada frame de `scroll` sin rAF — el fix irá a un issue/PR de implementación dedicado.
5. **Issues/PRs de implementación por crear** (por ítem del roadmap §5): subset del
   iconfont, fix parallax, inline de CSS crítico, code-splitting. Este documento cierra el
   entregable de *investigación*; las optimizaciones se trackean por separado.

## 7. Referencias

- Issue: [#1374](https://github.com/os-santiago/homedir/issues/1374)
- `quarkus-app/src/main/resources/META-INF/resources/js/core-bundle.js`
- `quarkus-app/src/main/resources/META-INF/resources/css/homedir.css`
- `quarkus-app/src/main/resources/templates/layout/main.html`
- Lighthouse v13.4.1 (Chrome 152 for Testing) — medianas de 3 corridas, 2026-08-13
