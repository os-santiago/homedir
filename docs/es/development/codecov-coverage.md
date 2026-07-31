# Cobertura de Código con Codecov

Este documento explica cómo se mide y publica la cobertura de código de HomeDir.

## Resumen

La cobertura se mide con JaCoCo dentro del perfil `coverage` de Maven de la aplicación
Quarkus (`quarkus-app/pom.xml`). El job `tests_cov` de
`.github/workflows/pr-quality-suite.yml` ejecuta la suite de pruebas con ese perfil y
sube el reporte a Codecov. El README muestra un badge de cobertura en vivo alimentado
por Codecov.

## Cómo funciona

1. El job `tests_cov` ejecuta `./mvnw verify -B -Pcoverage`.
2. JaCoCo genera el reporte en `quarkus-app/target/site/jacoco/jacoco.xml`.
3. El `codecov/codecov-action` sube ese archivo a Codecov.
4. Codecov renderiza el badge en el README y provee comentarios de cobertura por PR.

## Umbrales de cobertura

JaCoCo aplica ratios mínimos durante `verify`:

- Cobertura de líneas: `0.60` (60%)
- Cobertura de ramas: `0.40` (40%)

Estos valores están definidos en la ejecución `check` del perfil `coverage` en
`quarkus-app/pom.xml`. Un PR que baje de estos umbrales falla la compuerta `tests_cov`
antes de que se reporte cualquier cobertura a Codecov.

## Configuración requerida (admin del repositorio)

Los siguientes pasos requieren permisos de `Admin` del repositorio:

1. Crea una cuenta de Codecov para la organización `os-santiago` y autoriza el
   repositorio `homedir`.
2. Agrega un secreto de repositorio llamado `CODECOV_TOKEN` en
   `Settings > Secrets and variables > Actions`, usando el token de repositorio que
   provee Codecov.
3. Verifica que la primera ejecución de un PR aparezca en el panel de Codecov.

Hasta que el secreto esté configurado, el paso de subida no bloquea
(`fail_ci_if_error: false`), por lo que CI sigue pasando pero el badge no muestra datos.

## Roles

- Los datos de cobertura son públicos una vez que el repositorio es público en Codecov.
- Solo los miembros con permiso `Admin` pueden cambiar los secretos del repositorio. Si
  necesitas acceso, pide a un admin actual que te conceda el rol `Admin` en
  `os-santiago/homedir`.

## Cómo verificarlo localmente

```bash
cd quarkus-app
./mvnw verify -Pcoverage
```

El reporte HTML está disponible en `target/site/jacoco/index.html`.
