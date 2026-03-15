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
13. El versionado y tagging se ejecuta en cadencia manual por iniciativa; no asumir tag por cada PR o cambio menor.
14. La publicacion de releases se realiza en cadencia manual cuando una iniciativa o corte de version lo requiera; no por defecto tras cada PR.
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
19. La calidad objetivo de entrega debe mantenerse sobre 95% de exito semanal (ultimos 7 dias) tanto en checks de PR como en pases a produccion; si baja, priorizar estabilizacion antes de nuevas features.
20. Si el proyecto es multilenguaje, todo texto visible debe implementarse via archivos/bundles de idioma; evitar texto hardcoded de forma global en templates, JS, backend y mensajes UI.
21. Override explicito permitido: si se solicita trabajar/ejecutar en modo "entrega por lotes" (o equivalente), se permiten multiples iteraciones atomicas dentro de un solo PR, con validaciones intermedias por etapa y un punto de restauracion para rollback completo del lote.
22. Toda falla de PR, bloqueo de integracion o incidente generado por un cambio debe cerrarse incorporando el aprendizaje resultante en `ADEV.md` como regla o principio consolidado, evitando redundancia y duplicidad.
23. Todo cambio que toque templates, copy visible, i18n, rutas publicas o vistas admin debe actualizar o crear pruebas del comportamiento afectado en la misma iteracion; no se permite dejar assertions heredadas contra UI anterior.

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
12. Monitorear `PR Validation` y, cuando aplique a la iniciativa, el workflow manual de release/produccion correspondiente.
13. Antes de solicitar merge, ejecutar validaciones locales enfocadas al alcance del cambio para reducir fallas en checks del PR y sostener objetivo de exito >95%.
   - Si el cambio toca vistas renderizadas o contenido multilenguaje, incluir al menos build + pruebas dirigidas del recurso/pagina afectada con locale explicito cuando corresponda.
14. Verificar en produccion:
    - HTTP 200 en `/`, `/comunidad`, `/eventos`, `/proyectos`
    - Comportamiento funcional del cambio
    - Sin errores criticos nuevos en consola de navegador
15. Si falla produccion:
    - detener iteraciones nuevas
    - revertir o rollback a version estable
    - abrir PR correctivo con causa raiz y prevencion

## Lecciones Operativas del Historial
1. Si una regla, plantilla o automatizacion contradice el flujo real del repo, corregir primero la regla/documentacion antes de institucionalizar el error.
2. Documentar y automatizar solo comandos, workflows y supuestos que esten respaldados por el repositorio o la operacion real; si falta contexto, dejarlo explicito como `TODO` en vez de inventarlo.
3. Para scripts, operaciones y DR, no basta con validacion sintactica: ejecutar smoke tests reales en el entorno que importa y separar fallas del harness/quoting de fallas funcionales.
4. Para rendimiento, trabajar con comparaciones "apples-to-apples" contra un baseline concreto, medir latencia/error/payload y declarar incertidumbre cuando falten fixtures, trazas o datos productivos.
5. Priorizar siempre el fix de mayor apalancamiento demostrado por evidencia; evitar optimizaciones amplias o redisenos si aun no existe medicion que justifique el costo.
6. En personalizaciones visuales por evento o contexto, aplicar overrides de color/branding solo dentro de ese alcance y preservar la identidad global del sitio fuera de ese contexto.
7. Para backups y recuperacion ante desastre, validar restaurabilidad completa del servicio; no considerar suficiente un respaldo si no permite reconstruir el sitio con datos, artefactos y procedimiento probado.
8. Mantener workstreams limpios: abrir rama dedicada desde una base estable, no mezclar cambios ajenos y revalidar cuando cambien flags, comandos o herramientas para conservar comparabilidad.
9. En paginas multilenguaje, los tests deben fijar explicitamente el locale esperado y validar el contenido localizado correspondiente; no depender del idioma por defecto o de textos heredados.
10. Cuando cambie la narrativa, jerarquia o copy de una vista, revisar tambien pruebas hermanas del mismo recurso para evitar dejar expectations antiguas que solo fallan en CI.
11. Cuando una refactorizacion cambie el modelo de interaccion UI (por ejemplo, modal a pagina de detalle), actualizar en la misma iteracion las pruebas para validar el nuevo comportamiento observable y eliminar dependencias a markup legado.
