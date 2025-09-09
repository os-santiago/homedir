# Métricos de administración UI

Esta página resume los tokens de diseño y los puntos de interrupción utilizados en la pantalla de administración móvil primero → Métricas.

## Puntos de interrupción
- ≥1200px: cuadrícula de cuatro columnas
- 900–1199px: cuadrícula de tres columnas
- 600–899px: cuadrícula de dos columnas
- <600px: tarjetas apiladas de una sola columna

## Tokens
-Utiliza variables de sitio existentes para colores (`--Color-Primary`,`--Color-Bg`, `-Color-Light`).
-Los chips, los selectos y los botones comparten `var (-space-sm)` relleno y `var (-radio-md)` radio para objetivos de toque consistentes.
- Las tarjetas mantienen el radio de 12px y la sombra suave.
- Gap de cuadrícula entre las tarjetas: `1Rem`.

## Estados
- La barra de herramientas de filtro es pegajosa a la parte superior y admite el enfoque de chip/flotante/estilos activos.
- Cada tarjeta puede mostrar mensajes de contenido vacíos cuando no hay datos.

## Capturas de pantalla
-Antes:
-After: