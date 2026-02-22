# Documentation Language Policy

This policy defines how documentation is organized and maintained in this repository.

## Scope

- Applies to all documentation under `docs/`.
- Applies to markdown (`.md`) files and language indexes.

## Canonical language

- Canonical maintenance language is **English** (`docs/en`).
- Spanish (`docs/es`) is maintained as a mirror.

## Allowed structure

- `docs/README.md` (language gateway only)
- `docs/en/**` (canonical docs)
- `docs/es/**` (translation or stubs)

No other top-level markdown documents are allowed directly under `docs/`.

## Required mirroring rule

For every markdown document in `docs/en/<path>.md`, there must be a corresponding file in `docs/es/<path>.md`:

- Full translation, or
- Stub pointing to the canonical English source.

And vice versa:

- If the canonical source is temporarily in Spanish, `docs/en/<path>.md` must exist as an English stub.

## Stub format

A stub must include:

- Clear status label (`stub`).
- Link to canonical source.
- Short note explaining translation status.

## Category ownership

Use the same category path in both languages:

- `ai-context/`
- `architecture/`
- `community/`
- `development/`
- `events/`
- `features/`
- `modules/`
- `tasks/`
- `ui/`

## Contribution workflow

1. Create/update the canonical doc in `docs/en`.
2. Create/update mirrored file in `docs/es`.
3. If translation is not ready, add/update Spanish stub.
4. Update indexes:
   - `docs/en/README.md`
   - `docs/es/README.md`
   - `docs/README.md` (if language-level navigation changed)
5. Validate that no markdown file is left outside `docs/en` or `docs/es` (except `docs/README.md`).
