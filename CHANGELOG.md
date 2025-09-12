# Changelog

## [2.2.1] - 2025-09-12
### Changed
- Documentación reorganizada por idioma; se agregan glosario y principios base (Homedir) y se documenta la arquitectura de persistencia centralizada. Se eliminan documentos obsoletos.
- Actualización de plataforma Quarkus a 3.26.2 en `quarkus-app`.

### CI/Mantenimiento
- Actualizaciones de Actions: `actions/github-script@v8`, `google-github-actions/auth@v3`, `google-github-actions/get-gke-credentials@v3`.

### Notas
- No hay cambios funcionales relevantes; versión de mantenimiento y documentación.

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
