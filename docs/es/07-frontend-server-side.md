# Frontend del lado del servidor

Sin Next.js, Node.js, React, Vue o Angular.

## Qute SSR
Layout base, vistas y fragmentos HTML. Funciona sin JS; validación en servidor y CSRF en formularios.

## Fragmentos
Endpoints `/_fragment/*` por módulo.

## Asíncrono mínimo
`fetch()` de fragmentos y Server-Sent Events para Pulse.

## Accesibilidad
Debe funcionar sin JS; rutas protegidas por roles.

Ver la [arquitectura](02-architecture.md) para la ubicación de templates.
