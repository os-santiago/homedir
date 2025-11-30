# EventFlow 2.2.3

**Resumen**
- Canonicaliza el `redirect_uri` de GitHub para que siempre apunte a `/auth/post-login` y mueve la lógica a `GithubLinkService`, evitando diferencias entre entornos y el callback registrado.
- Añade la propiedad `app.public-url` a la documentación y ajustes de despliegue para que el valor público real se propague en el contenedor; el workflow obtiene el digest correcto más limpio.
- Actualiza los recursos públicos y los artefactos de build/deploy para que reflejen la nueva versión y los contenedores se publiquen con `2.2.3`.

**Detalles**
- La interacción con GitHub ya no reconstruye URIs manualmente; el nuevo servicio gestiona cookies y respuestas coherentes con el OAuth app configurado.
- Las instrucciones de tagging/release se actualizaron a `2.2.3` y los Dockerfiles comentados muestran el tag actual para referencia.
- Deploy workflow (tag `fix/deploy-logging`) publica el digest, loguea el estado de podman y usa las variables de entorno requeridas sin tocar `URI`s desde el recurso.

**Container**
- `quay.io/sergio_canales_e/eventflow:2.2.3` (alias)
- Despliegue por digest siempre que sea posible; el pipeline lo registra en el artefacto `pr-image-ref`.

## EventFlow 2.2.2

**Resumen**
- Correcciones menores y mejoras de documentación.
- Calidad: pipelines ajustados y verificación actualizada.
- Supply chain: imagen `:2.2.2` firmada + SBOM; deploy por digest.

**Detalles**
- Ver CHANGELOG para lista completa.

**Container**
- `quay.io/sergio_canales_e/eventflow:2.2.2` (alias)
- Despliegue por digest recomendado (CD actual lo aplica).
