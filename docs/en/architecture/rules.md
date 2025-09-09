# Architecture Rules (V1 · Monorepo)

## layers (packages)
- `io.eventflow.api… | … Web… `→ IU/Rest
- `io.eventflow.app… | … Service… `→ application/orchestration
- `io.eventflow.domain…` → Domain (entities/value/rules)
- `io.eventflow.infra…` → Persistence / External clients

## Blockers
1) Without cycles between packages.
2) Without critical cape jumps: `API → Infra` and` Domain → Infra` prohibited.
3) Without mutable global state (`public static` non-final).

## Informative rules (do not block)
- `API` I shouldn't use JPa/Plant directly.
- `Domain` I shouldn't use web APIS.

## Exceptions
- Only with justification and ticket; temporal.