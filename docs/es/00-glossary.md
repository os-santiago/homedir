# Glosario

## ADeveloper (AI-Augmented Developer)
Desarrollador apoyado por IA que lidera la construcción con prompts, revisión ligera y pipelines automáticos.

## ADevelopment (AI-Augmented Development)
Práctica de desarrollo asistido por IA con guardrails, contratos y tests automatizados.

## HDP (Homedir Persist)
Motor embebido de documentos JSON (cache en memoria + WAL + snapshots en volúmenes; compactor; cuotas/aislamiento por módulo). Más detalles en [persistencia](04-persistence-hdp.md).

## UniCore (Cloud-Native Modular Core)
Patrón arquitectónico central de Homedir: monolito modular moderno en un único binario Quarkus con modularidad estricta, blast radius controlado, ligereza (sin DBs/brokers/gateways pesados por defecto; usa HDP), UI SSR con Qute, cloud-native (GitOps/OpenShift) y enfoque AI-augmented ([ADevelopment](#adevelopment-ai-augmented-development)/[ADeveloper](#adeveloper-ai-augmented-developer)). Ver [arquitectura](02-architecture.md).
