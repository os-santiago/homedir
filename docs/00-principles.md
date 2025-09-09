# Principios

Manifiesto ligero:

- Monolito modular ligero ([UniCore](00-glossary.md#unicore-cloud-native-modular-core)): un binario con módulos de límites claros.
- Persistencia simple ([HDP](00-glossary.md#hdp-homedir-persist)): JSON + WAL + volumen; bases externas solo por compliance/escala.
- Sin mensajería pesada: módulos comunican in-process; Outbox/brokers solo si duele.
- Frontend sin frameworks SPA: [Qute SSR](07-frontend-server-side.md) + fragmentos HTML + SSE; JS mínimo.
- Blast radius: aislar recursos por módulo (threads, conexiones, cachés).
- Seguridad en servidor: JWT Ed25519, RBAC en users, CSRF en formularios.
- Observabilidad: logs JSON, métricas por módulo, readiness/health granulares.
- Evolución controlada: extraer “células” solo si ≥3 señales (ritmo, SLOs, carga, compliance).
