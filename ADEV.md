# ADEV.md

## Reglas Normativas Unicas
1. Cada iteracion debe salir en una rama dedicada y un unico PR con objetivo claro.
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

## Flujo Operativo
1. Sincronizar con `origin/main`.
2. Crear rama corta con scope explicito (`feat/*`, `fix/*`, `hotfix/*`, `docs/*`, `chore/*`).
3. Implementar solo el alcance acordado para la iteracion.
4. Ejecutar validacion local rapida (build y pruebas criticas del cambio).
5. Commit atomico.
6. Push de rama.
7. Crear PR con:
   - Summary
   - Why
   - Scope (in/out)
   - Validation
   - Production verification plan
   - Rollback plan
8. Activar auto-merge cuando los checks requeridos esten listos.
9. Monitorear `PR Validation` y luego `Production Release` en GitHub Actions.
10. Verificar en produccion:
    - HTTP 200 en `/`, `/comunidad`, `/eventos`, `/proyectos`
    - Comportamiento funcional del cambio
    - Sin errores criticos nuevos en consola de navegador
11. Si falla produccion:
    - detener iteraciones nuevas
    - revertir o rollback a version estable
    - abrir PR correctivo con causa raiz y prevencion
