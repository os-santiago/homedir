# Arquitectura

[UniCore (Cloud-Native Modular Core)](00-glossary.md#unicore-cloud-native-modular-core) con Quarkus 3 y Java 21.

## Estructura
- `apps/homedir-api`
- `modules/{users,events,spaces,pulse}/{core,app,adapters,migrations}`
- `shared/`, `contracts/`, `infra/`, `tests/`
- `templates/` (Qute), `static/{css,js}`

## Persistencia
[HDP](00-glossary.md#hdp-homedir-persist) como almacenamiento por defecto; schemas lógicos por namespace (módulo). Ver [persistencia](04-persistence-hdp.md).

## UI
UI SSR: vistas y fragmentos por módulo (`/_fragment/*`). Detalles en [frontend server-side](07-frontend-server-side.md).

## Guardrails
ArchUnit, FT (timeouts/bulkheads/circuit) y pools/cuotas por módulo. Más en [guardrails](03-guardrails.md).

## Evolución
Extraer “células” solo si aparecen ≥3 señales (ritmo, SLOs, carga, compliance).
