---
description: Workflow para implementar y corregir bugs hasta producción (Ciclo Completo)
---

# Flujo de Corrección y Despliegue en Producción

Este workflow detalla los pasos seguidos para diagnosticar, corregir, y desplegar cambios robustos a producción, basado en la experiencia de corrección de UI.

## 1. Reproducción y Planes
1.  **Reproducir Localmente**: Intentar reproducir el bug con `mvn quarkus:dev`.
    *   Si falla el entorno local (e.g. errores 500 ajenos), analizar el código estáticamente para entender la causa raíz segura.
2.  **Plan de Implementación**: Crear un plan (`implementation_plan.md`) detallando qué archivos cambiar y por qué.
    *   *Tip*: Para bugs de UI/auth, preferir soluciones robustas (fallbacks) en lugar de depender de una sola variable global que pueda fallar.

## 2. Implementación
1.  **Git Branch**: Crear rama `fix/nombre-descriptivo`.
2.  **Código**: Aplicar la corrección usando las mejores prácticas y fallbacks identificados.
    *   Ejemplo: `{#if session.ok || fallback.ok}`.
3.  **Comprobación Estática**: Verificar que no se rompe la compilación.

## 3. Pull Request y CI/CD
1.  **Crear PR**: Usar `gh pr create` con título y descripción claros.
2.  **Auto-Merge**: Habilitar `gh pr merge --merge --auto`.
3.  **Monitorizar**: Usar `gh run list` y `gh run watch` para seguir el pipeline "Release Pipeline".

## 4. Verificación en Producción
1.  **Check Visual**: Una vez desplegado (workflow `success`), navegar a la URL de producción.
2.  **Validación**: Confirmar que el cambio se refleja (e.g., botón oculto, datos visibles).
    *   Usar herramientas de navegador para evidencia si es posible.
