# Mejoras para Reducir "needs-human" en SDLC Autónomo

**Fecha**: 2026-07-16  
**Motivación**: Reducir escalaciones innecesarias mejorando la capacidad de decisión autónoma del worker

## 📊 Problema Identificado

### Caso Real: Issue #1016
- **Issue**: [perf] Large CSS files (49KB + 54KB) with overlapping styles
- **Resultado**: `needs-human` después de 12 minutos de procesamiento
- **Razón**: "SCC completed without producing any branch changes"
- **Root Cause**: Worker no supo cómo proceder con optimización de CSS

## ✅ Soluciones Implementadas

### 1. Guías de Decisión Autónoma (Nuevo Documento)

**Archivo**: `platform/prompts/scc-autonomous-decision-guidelines.md`

**Contenido**:
- ✅ Matriz de decisión para 8 escenarios comunes
- ✅ Principios de decisión basados en mejores prácticas
- ✅ Ejemplos concretos con código
- ✅ Árbol de decisión: cuándo proceder vs cuándo escalar
- ✅ Guías de continuidad operacional

**Escenarios Cubiertos**:
1. Performance optimization (CSS/JS consolidation)
2. Code refactoring (callbacks → async/await)
3. Naming conventions
4. Dependency updates
5. Error handling
6. Test coverage
7. Documentation
8. Security fixes

### 2. Prompt Mejorado del Worker

**Archivo Modificado**: `platform/scripts/homedir-sdlc-worker.sh`

**Cambios**:

#### Antes:
```bash
5. CONSTRAINTS:
   - If ambiguous or blocked, comment on issue and stop (do not guess)
```

#### Después:
```bash
5. CONSTRAINTS:
   - When faced with ambiguity, apply industry best practices and document your decision

10. AUTONOMOUS DECISION-MAKING: When faced with implementation choices:
   - FOLLOW codebase patterns
   - APPLY best practices
   - PREFER incremental changes
   - CHOOSE reversible approaches
   - DOCUMENT reasoning in commit messages

   Examples of automatic decisions (DO NOT escalate):
   - Code style → Follow existing patterns
   - Performance optimization → Apply safe optimizations
   - Error handling → Always add
   - Naming → Use descriptive names
   - Refactoring → Prefer async/await
   - Tests → Add following patterns
   - Dependencies → Update if safe

   ONLY stop if:
   - Requires business/product judgment
   - Multiple approaches with different business tradeoffs
   - High risk of data loss/security
   - Fundamental architecture change
```

### 3. Ejemplo Práctico para CSS

**Archivo**: `platform/prompts/example-autonomous-decision-css-consolidation.md`

**Contenido**:
- ✅ Decision tree específico para issue #1016
- ✅ Pasos de ejecución concretos
- ✅ Commit message template
- ✅ PR body example
- ✅ Rollback plan
- ✅ Checklist de confianza

## 📈 Impacto Esperado

### Métricas Objetivo

| Métrica | Antes | Objetivo | Método de Medición |
|---------|-------|----------|-------------------|
| `needs-human` rate | ~40% | <15% | Issues escalados / Issues totales |
| Tiempo promedio | 12+ min | 5-15 min | Issue → PR merged |
| Re-procesamiento | Alto | <10% | Issues que vuelven a queue |
| Autonomous decisions | ~0 | ~80% | Decisiones documentadas en commits |

### Casos de Uso Mejorados

#### ✅ Ahora se Auto-Deciden:

1. **Performance Optimization**
   - Consolidar archivos CSS/JS
   - Remover duplicados
   - Minificar assets
   - Agregar caching

2. **Code Refactoring**
   - Callbacks → async/await
   - Extract functions
   - Rename variables/functions
   - Remove dead code

3. **Error Handling**
   - Agregar try/catch
   - Validar inputs
   - Return error codes apropiados
   - Log errors con contexto

4. **Testing**
   - Agregar tests unitarios
   - Seguir patrones existentes
   - Mock dependencies
   - Test happy path + errors

5. **Documentation**
   - Agregar JSDoc/comments
   - Actualizar README
   - Agregar ejemplos
   - Update CHANGELOG

#### ⚠️ Aún Requieren `needs-human`:

1. **Decisiones de Arquitectura**
   - Migrar base de datos
   - Cambiar framework
   - Rediseñar API pública
   - Remover features públicas

2. **Decisiones de Negocio**
   - ¿Almacenar PII?
   - ¿Deprecar API?
   - ¿Cambiar pricing?
   - ¿Modificar términos de servicio?

3. **Alto Riesgo**
   - Cambios en auth/security
   - Modificar datos sensibles
   - Bypass de validaciones
   - Cambios irreversibles

## 🔄 Próximos Pasos

### Fase 1: Validación (Esta Semana)
- [ ] Re-ejecutar test E2E en issue #1016 con nuevo prompt
- [ ] Medir si genera código o escala a needs-human
- [ ] Revisar calidad de decisiones autónomas
- [ ] Ajustar guidelines según resultados

### Fase 2: Refinamiento (Próxima Semana)
- [ ] Agregar más ejemplos a guidelines
- [ ] Crear templates de commit messages
- [ ] Implementar logging de decisiones autónomas
- [ ] Dashboard para monitorear tasa de needs-human

### Fase 3: Expansión (2 Semanas)
- [ ] Agregar scenarios para frameworks específicos (React, Java, etc.)
- [ ] Crear decision tree interactivo
- [ ] A/B testing: prompt viejo vs nuevo
- [ ] Feedback loop: humanos revisan decisiones autónomas

## 📝 Uso de las Guías

### Para Developers

**Antes de etiquetar issue con `ready-to-implement`**:

1. ✅ **Claridad**: Issue tiene criterios de aceptación claros
2. ✅ **Scope**: Es atómico o indica que debe descomponerse
3. ✅ **Context**: Incluye ejemplos o referencias si es complejo
4. ✅ **Constraints**: Menciona limitaciones o requisitos especiales

**Ejemplo de Issue Bien Escrito**:
```markdown
## Issue: Consolidate CSS files for performance

### Context
Currently loading 2 CSS files:
- homedir.css (49KB)
- retro-theme.css (54KB)

Many overlapping/duplicate styles detected.

### Acceptance Criteria
- [ ] Consolidate into single optimized CSS file
- [ ] Remove duplicate selectors
- [ ] Preserve all current visual styles (no regression)
- [ ] Update HTML references
- [ ] Reduce total CSS size by >20%

### Constraints
- Must not break existing UI
- Must maintain theme structure
- Must work in all supported browsers
```

### Para el Worker AI

El worker ahora sigue este flujo:

```
1. Lee issue
2. Verifica si hay pattern en codebase → grep/find similar files
3. Verifica si guidelines cubre el caso → apply decision matrix
4. Toma decisión autónoma si confianza HIGH
5. Documenta decisión en commit + PR
6. Escala a needs-human solo si confianza LOW
```

## 🎯 KPIs de Éxito

### Semana 1
- [ ] `needs-human` rate < 30% (vs 40% baseline)
- [ ] Al menos 3 issues auto-resueltos sin intervención
- [ ] 0 decisiones autónomas incorrectas que causen rollback

### Mes 1
- [ ] `needs-human` rate < 20%
- [ ] 50+ issues auto-resueltos
- [ ] <5% rollbacks por decisiones incorrectas
- [ ] Feedback positivo de team en PR reviews

### Trimestre 1
- [ ] `needs-human` rate < 15%
- [ ] 200+ issues auto-resueltos
- [ ] Guidelines cubren 90% de casos comunes
- [ ] Self-improving: worker aprende de feedback

## 📚 Archivos Creados

1. ✅ `platform/prompts/scc-autonomous-decision-guidelines.md` (12KB)
   - Guías completas de decisión autónoma

2. ✅ `platform/prompts/example-autonomous-decision-css-consolidation.md` (8KB)
   - Ejemplo práctico para issue #1016

3. ✅ `platform/scripts/homedir-sdlc-worker.sh` (modificado)
   - Prompt mejorado con sección de autonomous decision-making

4. ✅ `tests/e2e/` (suite completa)
   - Para validar mejoras E2E

5. ✅ `AUTONOMOUS-DECISION-IMPROVEMENTS.md` (este archivo)
   - Documentación de mejoras

## 🔬 Plan de Validación

### Test 1: Re-ejecutar Issue #1016
```bash
cd tests/e2e
./run-e2e-test.sh 1016
```

**Resultado Esperado**:
- ✅ Worker consolida CSS files
- ✅ Genera commit con decisiones documentadas
- ✅ Crea PR con section "Autonomous Decisions"
- ✅ NO escala a needs-human

### Test 2: Issue Simple (typo fix)
```bash
./run-e2e-test.sh --list | grep -i typo
./run-e2e-test.sh <issue_number>
```

**Resultado Esperado**:
- ✅ Completa en <5 minutos
- ✅ Auto-merge exitoso
- ✅ 0 intervenciones humanas

### Test 3: Issue Complejo (architecture change)
**Resultado Esperado**:
- ⚠️ Escala a needs-human (correcto)
- ✅ Explica por qué requiere decisión humana
- ✅ Sugiere qué decisiones humano debe tomar

## 🎓 Lecciones Aprendidas

### Del Caso #1016

**Problema Original**:
- Worker recibió issue de optimización de CSS
- No tenía guías sobre cómo proceder
- Defaulteó a "ambiguous" → needs-human

**Solución Aplicada**:
- Guías explícitas: "consolidate CSS = best practice"
- Decision tree: performance optimization → apply safe optimizations
- Template de decisión con rationale + rollback plan

**Principio General**:
> Cuando hay ambigüedad técnica (no de negocio), el worker debe:
> 1. Buscar pattern en codebase
> 2. Aplicar best practice de industria
> 3. Documentar decisión + rationale
> 4. Proceder con confianza si es reversible

## ✨ Próxima Iteración

### Ideas para Mejorar Más

1. **ML-based Decision Confidence**
   - Trackear decisiones autónomas + outcomes
   - Entrenar modelo de confianza
   - Auto-ajustar guidelines basado en resultados

2. **Interactive Decision Trees**
   - Dashboard interactivo para developers
   - "What would worker decide?" preview
   - Feedback loop en tiempo real

3. **Community Guidelines**
   - Open-source las guidelines
   - Contribuciones de la comunidad
   - A/B testing de diferentes approaches

---

**Estado**: ✅ Implementado, Pendiente de Validación  
**Próximo Review**: Después de re-ejecutar test en issue #1016  
**Owner**: AI SDLC Team
