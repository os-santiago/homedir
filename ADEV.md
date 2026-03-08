# ADEV.md

## Reglas Normativas Unicas
1. Modo por defecto: cada iteracion debe salir en una rama dedicada y un unico PR atomico con objetivo claro.
2. Todo cambio debe llegar por PR; no se permite push directo a `main`.
3. Los commits deben ser atomicos y con `Conventional Commits`.
4. No mezclar en el mismo PR: refactor + feature + cambios visuales + infraestructura.
5. Mantener look and feel, estructura CSS y HTML del producto salvo instruccion explicita en contrario.
6. Reutilizar y extender la capa de persistencia de Homedir; evitar servicios externos salvo requerimiento explicito.
7. Minimizar impacto sobre APIs/servicios existentes y mantener compatibilidad hacia atras cuando aplique.
8. Si otra persona/agente modifica archivos locales, no tocar los mismos archivos en paralelo.
9. Resolver conflictos preservando mejoras validas de ambos lados; no perder mejoras por sobreescritura.
10. Ejecutar validaciones antes de commit y no incluir archivos no relacionados en el PR.
11. No avanzar a una nueva iteracion sin validar la anterior en produccion.
12. CI obligatoria en verde antes de merge.
13. Para cambios menores finalizados: crear y publicar tag con version consistente.
14. Para cambios mayores finalizados: crear y publicar release.
15. Al versionar, actualizar referencias de version en todo el repo (iniciando por `pom.xml` cuando aplique).
16. Documentacion del repo debe mantenerse en estructura bilingue oficial:
    - Canonico: `docs/en/**`
    - Espejo: `docs/es/**` (traduccion o stub)
    - Gateway: `docs/README.md`
17. Todo resultado debe seguirse hasta verificacion de despliegue en produccion.
18. Para nuevas funciones, endpoints o APIs, aplicar rollout incremental obligatorio en 3 iteraciones:
    - Iteracion 1: introducir recurso nuevo oculto/sin consumo productivo.
    - Iteracion 2: integrar consumo progresivo del recurso nuevo.
    - Iteracion 3: retirar versiones legadas/deprecadas una vez validado en produccion.
19. La calidad objetivo de entrega debe mantenerse sobre 95% de exito tanto en checks de PR como en pases a produccion; si baja, priorizar estabilizacion antes de nuevas features.
20. Si el proyecto es multilenguaje, todo texto visible debe implementarse via archivos/bundles de idioma; evitar texto hardcoded de forma global en templates, JS, backend y mensajes UI.
21. Override explicito permitido: si se solicita trabajar/ejecutar en modo "entrega por lotes" (o equivalente), se permiten multiples iteraciones atomicas dentro de un solo PR, con validaciones intermedias por etapa y un punto de restauracion para rollback completo del lote.

## Flujo Operativo
1. Sincronizar con `origin/main`.
2. Crear rama corta con scope explicito (`feat/*`, `fix/*`, `hotfix/*`, `docs/*`, `chore/*`).
3. Elegir modo de entrega:
   - Default: 1 iteracion = 1 PR.
   - "Entrega por lotes": varias iteraciones atomicas en 1 PR, solo si fue solicitado explicitamente.
4. Implementar solo el alcance acordado para la iteracion (o para la etapa actual del lote).
5. Para nuevas funciones/endpoints/APIs, ejecutar secuencia incremental: oculto/sin uso -> integrado/consumido -> limpieza legado/deprecado.
6. En modo "entrega por lotes", crear punto de restauracion al inicio del lote (tag/commit de referencia) y mantener checkpoints por etapa.
7. Ejecutar validacion local rapida (build y pruebas criticas del cambio) en cada iteracion/etapa.
8. Commit atomico.
9. Push de rama.
10. Crear PR con:
   - Summary
   - Why
   - Scope (in/out)
   - Validation
   - Production verification plan
   - Rollback plan
11. Activar auto-merge cuando los checks requeridos esten listos.
12. Monitorear `PR Validation` y luego `Production Release` en GitHub Actions.
13. Antes de solicitar merge, ejecutar validaciones locales enfocadas al alcance del cambio para reducir fallas en checks del PR y sostener objetivo de exito >95%.
14. Verificar en produccion:
    - HTTP 200 en `/`, `/comunidad`, `/eventos`, `/proyectos`
    - Comportamiento funcional del cambio
    - Sin errores criticos nuevos en consola de navegador
15. Si falla produccion:
    - detener iteraciones nuevas
    - revertir o rollback a version estable
    - abrir PR correctivo con causa raiz y prevencion
