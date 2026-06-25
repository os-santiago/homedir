# Tubería CI/CD

Los flujos de trabajo de GitHub Actions orquestan la compilación, aseguramiento de calidad y el pipeline de implementación para garantizar la calidad del código y un único digest de imagen inmutable desde la solicitud de extracción hasta producción.

## Aseguramiento de Calidad

### Revisión de Código con IA – `.github/workflows/ai-code-review.yml`

Análisis automatizado de código usando Claude AI que verifica:
- Code smells y métricas de complejidad
- Convenciones de nomenclatura y documentación
- Anti-patrones de rendimiento y problemas de seguridad
- Mejores prácticas y manejo de errores

El flujo de trabajo publica hallazgos detallados como comentarios en el PR con niveles de severidad y sugerencias accionables. Ver [Documentación de Revisión de Código con IA](../ci/ai-code-review.md) para más detalles.

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
git etiqueta -a v2.2.11 -m "homedir 2.2.11"
Git Push Origin v2.2.11

# Lanzamiento opcional de GitHub
GH Release Create v2.2.11 -f libe_notes.md -t "homedir 2.2.11"
`` `` ``
