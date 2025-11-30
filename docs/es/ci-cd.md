# Tubería CI/CD

Dos flujos de trabajo de GitHub Actions orquestan la tubería de compilación e implementación y aseguran un solo flujo de resumen de imágenes inmutables de la solicitud de extracción a la producción.

## Solicitudes de extracción-`.Github/Workflows/sbom-and-scan.yml`

-** Build and Test **: `./mvnw -b -ntp Test Package` se ejecuta dentro de` quarkus -app`.
-** Crear imagen nativa **: `./mvnw -b -ntp paquete -pnative -dskipTests` Empaca el corredor nativo una vez y lo etiqueta para la solicitud de confirmación y extracción.
 -** SBOM / Vulnerability Scan **: Anchore's SBOM-Action (`V0`) y Scan-Action (` V6`) producen informes de SBOM y vulnerabilidad, cargado como el artefacto de 'PR-Security-informes` y al escaneo de códigos. El resumen del trabajo muestra la referencia de imagen exacta utilizada para la promoción posterior.
- ** Firma opcional **: Si están presentes las teclas cosign, se firma el mismo digest de imagen.

La variable de repositorio `Security_Gating` Togga Scan Enforcement:

- `Permisive` (predeterminado) - Las fallas de escaneo no bloquean el flujo de trabajo.
- `Ejecución de fallas ' - Las fallas de escaneo hacen que el trabajo falle.

## fusionar con `main`-` .github/flujos de trabajo/main-deploy.yml`

El flujo de trabajo de implementación resuelve el resumen de la imagen de solicitud de extracción y lo promueve sin reconstruir. Puede etiquetar el resumen para la trazabilidad y luego autenticarse a GKE para aplicar manifiestos y extender la imagen exacta por Digest.

## Informes e identidad de artefactos

-** Informes de seguridad **: Artefacto `PR-Security-Regists 'y resultados de escaneo de código.
- ** Identidad de imagen **: Escrito en el resumen del flujo de trabajo de PR y almacenado en `image-ref.txt` dentro del artefacto.

Cambie a la activación obligatoria estableciendo la variable de repositorio `Security_gating` a` ejecutando 'en la configuración del repositorio.

## Etiqueta y lanzamiento

Después de fusionarse con 'Main`:

`` `Bash
Git Fetch Origin && Git Checkout Main && Git Pull
git etiqueta -a v2.2.3 -m "eventflow 2.2.3"
Git Push Origin v2.2.3

# Lanzamiento opcional de GitHub
GH Release Create v2.2.3 -f libe_notes.md -t "eventflow 2.2.3"
`` `` ``

