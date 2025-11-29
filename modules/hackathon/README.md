# Módulo Hackathon

## Propósito
- Preparar funcionalidades enfocadas en hackatones sin añadir una base de datos nueva.
- Reutilizar el motor de persistencia actual (JSON + `PersistenceService`) para eventos, speakers y agendas.
- Apoyarse en `EventType.HACKATHON` para diferenciar flujos y vistas sin romper eventos existentes.

## Alcance inicial (MVP)
- Plantillas y endpoints mínimos sobre el modelo actual para publicar agenda y resultados del hackatón.
- Soporte de registro y seguimiento rápido (equipos/proyectos) usando el storage existente.
- UI: mostrar badges de tipo Hackatón y permitir selección desde el panel Admin (ya habilitado en #62).

## Enfoque técnico
- Persistencia: misma carpeta `data/` y archivos JSON utilizados por `PersistenceService` (sin SQL ni migraciones).
- Dominio: reutilizar `Event`, `Talk` y `Scenario`; extender con modelos livianos para `Team`/`Project` si se necesitan.
- Feature flag sugerido: `feature.hackathon.enabled=true|false` para aislar el módulo.
- Integración: rutas bajo `/hackathon` (público) y `/private/hackathon` (admin), apoyándose en la seguridad ya disponible.

## Pendientes sugeridos
- Definir DTOs/base de datos en memoria para equipos y proyectos.
- Página pública de proyectos + tablero de resultados.
- Exportar estado a JSON para juzgado y premiación.
- Documentar comandos rápidos de build/test específicos del módulo (cuando existan).

> Objetivo: entregar valor rápido para eventos de hackatón reutilizando al máximo la infraestructura existente.
