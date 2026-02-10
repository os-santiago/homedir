# Changelog

## [3.330.1] - 2026-02-10
### Changed
- Community section now resolves UI text from i18n keys in Picks, Propose Content, and Moderation flows.
- Community client-side date formatting now follows browser/document locale.
- `full_release_cycle` default version updated to `3.330.1`.

### Fixed
- Removed `z-index` from `.alpha-banner` to prevent overlap with the user profile popup.

### Quality
- Updated moderation page test expectation to match default English i18n output.

## [2.2.1] - 2025-09-12
### Changed
- Actualización de versión y documentación para release 2.2.1.

### Security/Supply Chain
- Imagen publicada con alias `:2.2.1`, firmada y con SBOM adjunto.

## [2.2.0] - 2025-08-17
### Added
- Quality gate en PR: análisis estático (diff-aware), reglas de arquitectura (monorepo), cobertura en el diff, higiene de dependencias.
- Optimización de workflows: ejecución paralela, paths-filter y resúmenes de PR.

### Changed
- Módulo de métricas: últimas mejoras de panel Admin (cards, grid responsive, export CSV) y persistencia.

### Fixed
- Ajustes de CSS en Admin (métricas y listados).
- Correcciones menores en funcionalidades de charlas/eventos.

### Security/Supply Chain
- Publicación de imagen con alias `:2.2.0`, firma con Cosign y SBOM adjunto (también al digest).
- Despliegue continúa por digest para mayor seguridad.

## [2.1.3] - Skipped
- Número de versión saltado; no se publicó release.
