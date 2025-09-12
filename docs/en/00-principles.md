# Principles

Light Manifesto:

-Light modular monolith ([unicore] (00-glossary.md#unicore-cloud-native-modular-core)): a binary with modules of clear limits.
- Simple persistence in JSON files; External bases only for compliance/scale.
- Without heavy messaging: modules communicate in-process; Outbox/brokers only if it hurts.
-Frontend without frameworks spa: [qute ssr] (07-Frontend-series-side.md) + fragments html + SSE; JS minimum.
- Blast Radius: isolate resources per module (threads, connections, caches).
- Server Security: JWT ED25519, RBAC in Users, CSRF in forms.
- Observability: Logs JSON, Module metrics, Readiness/Health Granulas.
- Controlled evolution: Extract “cells” only if ≥3 signals (rhythm, can, load, compliance).