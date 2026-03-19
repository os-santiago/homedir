# Reglas de arquitectura (v1 · monorepo)

## Capas (paquetes)
- `io.homedir.api… | …web…` → UI/REST
- `io.homedir.app… | …service…` → aplicación/orquestación
- `io.homedir.domain…` → dominio (entidades/valor/reglas)
- `io.homedir.infra…` → persistencia / clientes externos

## Reglas bloqueantes
1) Sin ciclos entre paquetes.
2) Sin saltos críticos de capa: `api → infra` y `domain → infra` prohibidos.
3) Sin estado global mutable (`public static` no-final).

## Reglas informativas (no bloquean)
- `api` no debería usar JPA/repos directamente.
- `domain` no debería usar APIs web.

## Excepciones
- Solo con justificación y ticket; temporales.
