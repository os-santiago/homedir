# eventflow

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Quality Gate](https://github.com/scanalesespinoza/eventflow/actions/workflows/quality.yml/badge.svg)](https://github.com/scanalesespinoza/eventflow/actions/workflows/quality.yml)
[![PR Quality — Architecture Rules](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-architecture-rules.yml/badge.svg)](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-architecture-rules.yml)
[![PR Quality — Static Analysis](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-static-analysis.yml/badge.svg)](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-static-analysis.yml)
[![PR Quality — Dependencies](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-deps-hygiene.yml/badge.svg)](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-deps-hygiene.yml)
[![PR Quality — Tests & Coverage](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-tests-coverage.yml/badge.svg)](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-tests-coverage.yml)
[![PR Quality — Suite](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-quality-suite.yml/badge.svg)](https://github.com/scanalesespinoza/eventflow/actions/workflows/pr-quality-suite.yml)

Plataforma inteligente de gestión de eventos: espacios, actividades, ponentes, asistentes y planificación personalizada.

Versión estable más reciente: **v2.2.1**.

## Funciones
- Gestiona eventos, ponentes, escenarios y charlas
- Inicio de sesión con Google usando Quarkus OIDC
- Área de administración protegida por `ADMIN_LIST`
- Importa eventos desde JSON
- Notificaciones en la aplicación para cambios de estado de charlas
- Seguridad de la cadena de suministro con generación de SBOM, firma de imágenes y escaneo de vulnerabilidades

## Inicio rápido
Ejecuta la aplicación en modo desarrollo:

```bash
mvn -f quarkus-app/pom.xml quarkus:dev
```

Luego navega a `http://localhost:8080`.

### Autenticación local en desarrollo
El perfil de desarrollo desactiva el inicio de sesión con Google y habilita cuentas en memoria
definidas en `quarkus-app/src/main/resources/application.properties`:

- `user@example.com` / `userpass` — usuario sin privilegios
- `admin@example.org` / `adminpass` — administrador

Usa estas credenciales con el formulario de "Modo local" en `/ingresar`.

### Configuración de Google OAuth 2.0
Configura estas propiedades en `application.properties` o variables de entorno:

```
quarkus.oidc.provider=google
quarkus.oidc.client-id=<CLIENT_ID>
quarkus.oidc.credentials.secret=<CLIENT_SECRET>
quarkus.oidc.authentication.redirect-path=/private
quarkus.oidc.authentication.scopes=openid profile email
quarkus.oidc.logout.post-logout-path=/
```

Registra `https://eventflow.opensourcesantiago.io/private` como URI de redirección autorizada para despliegues en producción.

### Acceso de administrador
Solo los correos listados en `ADMIN_LIST` pueden crear o editar eventos:

```
ADMIN_LIST=sergio.canales.e@gmail.com,alice@example.org
```

### Importación de eventos
Carga un archivo JSON llamado `file` en `/private/admin/events` para importar eventos. Los IDs duplicados devuelven `409 Conflict`; el JSON inválido devuelve `400 Bad Request`.

## Cadena de suministro
La compilación produce SBOMs para dependencias e imágenes de contenedor y escanea las imágenes en busca de vulnerabilidades conocidas. CI publica artefactos como `target/bom.json` y `sbom-image.cdx.json`, y las imágenes pueden firmarse con Cosign.

## Comunidad
Proyecto respaldado por la comunidad OpenSource Santiago. Únete a nuestro [servidor de Discord](https://discord.gg/3eawzc9ybc).

Para divulgación coordinada de vulnerabilidades, consulta [SECURITY.es.md](SECURITY.es.md).
