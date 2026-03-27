# Reputation Hub: estado de estabilidad y mejoras UX

Documento espejo de:

- [EN canonical: Reputation Hub Stability and UX Update](../../en/features/reputation-hub-stability-and-ux-update.md)

## Estado (25-03-2026)

El rollout de Reputation Hub esta en estado estable pre-GA, con fases 0-5 implementadas y cubiertas por pruebas enfocadas.

Validacion medida en HEAD:

- Comando:
  - `quarkus-app\mvnw.cmd "-Dtest=ReputationEngineFeatureFlagTest,ReputationEngineServiceTest,ReputationEventTaxonomyTest,AdminReputationApiResourceTest,AdminReputationPhase2ApiResourceTest,PublicProfileReputationSummaryTest,ReputationHubMigrationBannerTest,ReputationHubResourceTest,ReputationRecognitionApiDisabledTest,ReputationRecognitionApiResourceTest,CommunityReputationNavExposureTest,CommunityBoardPrimarySwitchTest,ReputationHubServicePerformanceTest" test`
- Resultado:
  - 31 tests ejecutados
  - 0 fallas
  - build exitoso

## Mejoras ya entregadas

## Experiencia de usuario

- Migracion de navegacion progresiva y reversible mediante flags:
  - `reputation.hub.ui.enabled`
  - `reputation.hub.nav.public.enabled`
  - `reputation.hub.primary.enabled`
- El submenu de comunidad oculta el link legado al board cuando el replacement gate esta activo, reduciendo confusion.
- Alias `/community/reputation-hub` hacia `/comunidad/reputation-hub` validado.
- Banner de onboarding de migracion disponible cuando el switch principal esta activo.

## Perfil publico

- El perfil publico incluye resumen reputacional (superficie fase 3).
- Exposicion controlada por flags de rollout para proteger estabilidad.
- Cobertura i18n validada en rutas publicas relacionadas con reputacion.

## Ciclo virtuoso (actividad -> retroalimentacion -> incentivos)

- El motor reputacional opera sobre 4 dimensiones:
  - Participation
  - Contribution
  - Recognition
  - Consistency
- El Hub incorpora:
  - leaderboards semanal y mensual
  - rising members
  - contribuciones reconocidas
- El API de reconocimiento tiene protecciones anti-abuso base:
  - sin auto-reconocimiento
  - controles de cooldown y duplicados
  - control de cupo diario
- La lectura GA ahora valida diversidad de reconocimiento en ventana (minimo de validadores unicos), no solo volumen bruto de reconocimientos.
- La lectura GA ahora tambien aplica umbral maximo de concentracion por validador en los reconocimientos de la ventana.
- La lectura GA ahora tambien exige diversidad minima de objetivos reconocidos dentro de la ventana.

## Pendiente para declarar GA estable

1. Levantar p95/p99 de latencia server en condicion tipo produccion para:
   - `/comunidad/reputation-hub`
   - `/comunidad/reputation-hub/how`
2. Levantar web-vitals en condicion tipo produccion:
   - LCP
   - INP
   - TBT
3. Mantener el switch principal sin rollback por al menos un ciclo semanal y mensual de leaderboard.
4. Mantener guardrails de regresion en verde (presupuesto de payload y tests objetivo) durante dos ventanas de release.

## Incertidumbre conocida

- Las pruebas funcionales/guardrails estan en verde, pero en esta actualizacion no se capturaron trazas frescas de navegador en entorno tipo produccion.
- La siguiente evidencia de decision debe venir de p95/p99 + web-vitals.

## Resumen listo para compartir

HomeDir avanzo Reputation Hub a un estado estable pre-GA con rollout progresivo y control de rollback rapido. La experiencia mejora la claridad de navegacion, agrega contexto reputacional en perfil publico y cierra el ciclo actividad-retroalimentacion con leaderboards y contribuciones reconocidas protegidas por reglas anti-abuso. El cierre de GA ahora depende de evidencia de medicion: sostener estabilidad del switch en ciclos semanal/mensual y validar p95/p99 mas web-vitals en carga representativa.
