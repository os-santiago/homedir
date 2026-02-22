# Contribuir a Homedir

Disponible en [inglés](../en/CONTRIBUTING.md).

¡Gracias por tu interés en contribuir! Mantenemos el desarrollo **simple y rápido** con un enfoque basado en trunk:

- **Usa siempre Pull Requests** — nada de pushes directos a `main`.
- **`main` está protegida** — los chequeos requeridos bloquean merges; sin aprobaciones manuales.
- **Trunk-Based Development** — ramas de corta vida, merges frecuentes.
- **Define incidentes antes de crear ramas/mergear** — clasifica y documenta el incidente primero.

---

## Principios

1. **Solo Pull Requests**
   Todos los cambios deben llegar vía PR. Pushes directos a `main` están bloqueados.

2. **Main protegida**
   `main` es el tronco único. Requiere:
   - Pasar la **PR Quality Suite**: `style`, `static`, `arch`, `tests_cov`, `deps`.
   - **Squash & Merge** para mantener un historial lineal.

3. **Ramas pequeñas y de corta vida**
   Mantén el trabajo en horas/días. Usa **feature flags** para entregas incrementales.

4. **Incidentes definidos por adelantado**
   Si estás arreglando un problema urgente, abre primero un **Incident issue** y referéncialo en tu rama y PR.

---

## Flujo de Trabajo

1. **Abre un Issue** (bug/feature/incidente) y acuerda el alcance.
2. **Crea una rama de corta vida** desde `main`:
   - `feat/<slug-corto>`
   - `fix/<slug-corto>`
   - `hotfix/INC-<id>-<slug-corto>` (para incidentes P0/P1)
   - `docs/<slug-corto>` o `chore/<slug-corto>`
3. **Commit** usando [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat: …`, `fix: …`, `docs: …`, `chore: …`, `refactor: …`, `test: …`
4. **Abre un PR temprano** (Drafts bienvenidos). Enlaza al Issue/Incidente.
5. **Chequeos verdes** → atiende cualquier feedback.
6. **Squash & Merge** a `main`.
7. **Post-merge**: vigila CI/CD y reportes de errores.

---

## Definición y Manejo de Incidentes

Antes de crear una rama o pedir merge en ítems urgentes, **crea un Incident issue** que incluya:

- **Severidad (P0–P3)**, impacto, versiones afectadas
- **Línea de tiempo/contexto**, pasos para reproducir, logs relevantes
- **Workaround** (si existe)
- **Criterios de salida** (cómo confirmaremos la resolución)

**Guía de severidad**

- **P0 – Crítica**: caída total, pérdida de datos, problema de seguridad activo
  _Acción_: `hotfix/INC-<id>-<slug>` → PR priorizado → release inmediato
- **P1 – Alta**: degradación mayor, sin workaround razonable
  _Acción_: similar a P0; puede esperar una ventana corta
- **P2 – Media**: bug importante, workaround viable
  _Acción_: `fix/<slug>`; agendar próximo release
- **P3 – Baja**: casos borde, copy/UI menor
  _Acción_: `chore/` o `fix/` con menor prioridad

Siempre referencia el Incidente en el nombre de la rama y descripción del PR (ej., `Refs INC-42`).

---

## Requisitos del Pull Request

Incluye una descripción clara (problema → solución) y el checklist:

- [ ] Enlaza Issue/Incidente (`Closes #123` / `Refs INC-42`)
- [ ] Chequeos de PR Quality Suite (`style`, `static`, `arch`, `tests_cov`, `deps`) en verde
- [ ] Capturas/clip para cambios de UI/UX
- [ ] Notas de Rollout/feature flag si aplica
- [ ] Docs actualizados cuando sea necesario (README/CHANGELOG/etc.)

**Consejos**
- Prefiere múltiples PRs pequeños sobre uno grande.
- No mezcles refactors amplios con cambios funcionales.
- Mantén commits con significado; el mensaje de squash debe contar la historia.

---

## Inicio Rápido de Desarrollo

```bash
# Actualiza trunk y crea rama
git checkout main && git pull --ff-only
git switch -c feat/<slug-corto>

# Haz el trabajo, luego commit con Conventional Commits
git add .
git commit -m "feat: agregar navegación sticky a Detalle de Charla"

# Push y abre un Draft PR
git push -u origin feat/<slug-corto>
```

## Cómo Pasa tu PR

Sigue estos pasos rápidos antes de abrir un pull request:

1. **Instala prerequisitos** – Java 21 y Maven 3.9 o superior.
2. **Formatea código** – `mvn -f quarkus-app/pom.xml spotless:apply`.
3. **Chequeo dependencias** – corre `mvn -f quarkus-app/pom.xml enforcer:enforce` y `./dev/deps-check.sh`.
4. **Commit con Conventional Commits**.
5. **Push y abre el PR** – arregla problemas reportados por CI y re-pushea.

### Cobertura de tests

- Corre `./dev/pr-check.sh` para revisar formato, compilar, correr tests y generar `quarkus-app/target/site/jacoco/index.html`.
- Mantén **≥ 70%** de cobertura en líneas y ramas en el diff de tu PR.

## Contribuyendo a la UI (Frontend)

La UI de Homedir está construida con:
- Quarkus + Qute para renderizado de vistas.
- Templates en `quarkus-app/src/main/resources/templates`.
- Estilos estandarizados en `quarkus-app/src/main/resources/META-INF/resources/css/homedir.css`.

### Correr UI Localmente

1. Ve a `quarkus-app`:
   ```bash
   cd quarkus-app
   ```
2. Corre en modo dev:
   ```bash
   mvn quarkus:dev
   ```
3. Abre http://localhost:8080.

### Convenciones de Diseño

- Usa siempre el layout principal: `{#extends layout/main}` o `{#include layout/main}`.
- Usa clases `hd-*` existentes siempre que sea posible.
- No agregues estilos inline; extiende `homedir.css`.
- Si se necesitan nuevos componentes, documéntalos en `docs/es/ui/architecture.md` (y agrega stub EN si aplica).

### Alineación con Mockup
- Textos, colores y espaciados deben alinearse con el mockup oficial de Homedir.
