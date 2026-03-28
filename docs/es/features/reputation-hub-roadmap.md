# Reputation Hub Roadmap (espejo)

Documento espejo de:

- [EN: Reputation Hub Roadmap (Stability-First)](../../en/features/reputation-hub-roadmap.md)

## Estado

- Draft v1 (plan de implementacion derivado del PRD)
- Propietario: HomeDir

## Objetivo

Reemplazar `Community Board` por `Reputation Hub` de forma gradual y reversible, priorizando estabilidad de plataforma.

## Alcance MVP

- reemplazo conceptual y visual de `Community Board`
- motor de reputacion con 4 dimensiones:
  - `Participation`
  - `Contribution`
  - `Recognition`
  - `Consistency`
- resumen de reputacion visible en perfil publico
- leaderboards:
  - semanal
  - mensual
  - rising members
- contribuciones reconocidas
- seccion `How reputation works`
- reglas minimas anti-abuso

## No alcance MVP

- integracion de reputacion externa (`GitHub`, `LinkedIn`, etc.)
- reputacion multi-tenant
- endorsements complejos
- monetizacion o economia de rewards
- ML o recomendadores avanzados

## Hoja de ruta gradual y segura

## Fase 0 - Auditoria e instrumentacion (sin cambio UX)

- inventario de rutas/datos actuales de `Community Board`
- taxonomia de eventos reputacionales
- baseline de metricas
- flags creados y apagados por defecto

Gate:

- cero regresiones visibles
- baseline disponible

## Fase 1 - Motor oculto v1 (solo escritura)

- esquema de eventos + persistencia
- ingestion de senales existentes
- agregados por usuario/dimension/ventana temporal
- idempotencia y deduplicacion

Gate:

- tests de ingestion/agregacion en verde
- sin degradacion de performance sensible

## Fase 2 - Shadow mode y explicabilidad interna

- API interna de resumen reputacional
- vista interna para QA de scoring
- payload de explicabilidad sin filtrar formula exacta
- anti-abuso base (duplicados + cooldown)

Gate:

- coherencia validada con muestras manuales
- sin fugas de PII ni logs inseguros

## Fase 3 - Resumen en perfil publico (exposicion limitada)

- estado reputacional + fortalezas + hitos + badges preview
- soporte `en/es` en i18n
- sin reemplazo de navegacion todavia

Gate:

- UX estable en desktop/mobile + locales
- comprension basica del usuario

## Fase 4 - Beta de Reputation Hub en paralelo

- nueva ruta `Reputation Hub` en paralelo a `Community Board`
- leaderboards semanales/mensuales + rising + reconocimientos
- reconocimiento MVP (`recommended`, `helpful`, `standout`)
- limites anti-abuso: sin auto-reconocimiento, cupo diario, cooldown

Gate:

- guardrails sanos (concentracion, anomalias, error rate)
- feedback de comprension positivo

## Fase 5 - Switch de navegacion principal

- `Reputation Hub` pasa a entrada principal
- `Community Board` queda deprecado con redirect compatible
- mensaje de onboarding del cambio
- runbook de promocion y rollback:
  - la verificacion en produccion se resuelve desde el playbook de entrega del SDLC, no desde un modulo admin dentro del producto

Gate:

- estabilidad sostenida al menos un ciclo semanal y mensual
- metricas clave >= baseline o neutras

## Fase 6 - Hardening y cleanup legado

- eliminacion de implementacion legacy
- pruebas de regresion y runbooks finales
- documentacion operativa cerrada

Gate:

- sin gatillos de rollback por al menos dos ventanas de release

## Criterios de seguridad por iteracion

- validaciones locales enfocadas al alcance
- pruebas de UI/rutas/i18n afectadas
- preflight tipo CodeQL para redirects, auth/session, logging y persistencia
- rollback definido por fase con flags de corte rapido

## Orden recomendado de implementacion

1. documentacion + flags + taxonomia de eventos (sin cambios funcionales)
2. ingestion oculta y agregados internos
3. resumen reputacional en perfil detras de flag
4. beta de hub con audiencia acotada
5. switch de navegacion y deprecacion de board
