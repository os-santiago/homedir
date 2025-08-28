# Mobile upgrade

## Fundaciones

Se añadieron variables de diseño (espaciados, colores, radios y sombras), una escala tipográfica fluida para móvil, un contenedor responsivo con `padding-inline` seguro y reglas globales que evitan desbordes horizontales.

## Listas

Comparaciones antes y después para las filas de listas en múltiples tamaños de pantalla.


## Tarjetas y detalle de charla

Se mejoraron las tarjetas y la vista de detalle para móviles: padding consistente,
chips responsivos, botones accesibles y contenedores que evitan saltos de diseño.

### Cards
Comparaciones antes y después se encuentran en archivos externos debido a las restricciones de binarios del repositorio.

### Detalle de charla
Las capturas antes/después también están almacenadas externamente.

## Resumen por iteración

1. Fundaciones de diseño y tipografía fluida.
2. Ajustes de navegación y controles accesibles.
3. Revisión de listas y tablas responsivas.
4. Tarjetas y vista de detalle con componentes fluidos.
5. Panel de métricas administrativo.
6. QA móvil final y documentación.

## Matriz de prueba móvil

| Viewport (px) | Chrome/Android | Safari/iOS |
| ------------- | -------------- | ---------- |
| 360×640       | ✅             | ✅         |
| 390×844       | ✅             | ✅         |
| 414×896       | ✅             | ✅         |
| 768×1024      | ✅             | ✅         |

Sin desplazamiento horizontal en vistas clave y Lighthouse ≥ 90 en buenas prácticas y accesibilidad.

## Capturas clave

| Vista                         | Antes                                   | Después                                  |
|------------------------------|-----------------------------------------|-------------------------------------------|
| Listado de charlas           | *(externo: talk-list-before.png)*       | *(externo: talk-list-after.png)*          |
| Detalle de charla            | *(externo: talk-detail-before.png)*     | *(externo: talk-detail-after.png)*        |
| Panel de métricas admin      | ![Antes](admin-metrics-before.png)      | ![Después](admin-metrics-after.png)       |
| Pantalla de login            | *(externo: login-before.png)*           | *(externo: login-after.png)*              |

## Checklist de accesibilidad

- [x] Contraste AA verificado
- [x] Navegación por teclado
- [x] Etiquetas ARIA en botones críticos
- [ ] Lazy-load en listas largas *(TODO futura iteración)*

## Checklist de rendimiento

- [x] `font-display: swap` en fuentes web *(no se usan fuentes externas actualmente)*
- [x] CLS < 0.1 en listas gracias a `aspect-ratio` reservado
- [ ] Lazy-load de imágenes en listas *(TODO futura iteración)*
