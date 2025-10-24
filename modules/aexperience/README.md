# Módulo Experimental de Experiencia Aumentada (EventFlow3)

## Propósito

El módulo de Experiencia Aumentada de EventFlow3 es una iniciativa experimental orientada a explorar nuevas formas de interacción entre usuarios y plataformas en Homedir. Su objetivo es validar cómo la inteligencia artificial, la personalización dinámica y la navegación basada en intención pueden mejorar la experiencia digital sin comprometer la estabilidad del producto principal.

## Enfoque y Naturaleza Experimental

El módulo funciona como un laboratorio de innovación controlada, diseñado para coexistir con el núcleo de Homedir sin acoplarse directamente.

- Puede integrarse, probarse y evolucionar dentro del entorno real de Homedir.
- Puede desactivarse o eliminarse completamente sin afectar las funcionalidades estables del sistema.

> Forma parte del código base, pero no del camino crítico.

## Estrategia de Integración Técnica

### 1. Integración desacoplada
- Se implementa dentro del monorepo de Homedir, bajo la ruta `/modules/aexperience/`.
- Se comunica con el núcleo mediante interfaces públicas (API o eventos) y hooks opcionales, sin modificar el flujo base.
- No inyecta dependencias ni altera el comportamiento de componentes existentes.

### 2. Feature Flag de activación
- Se controla mediante un flag de configuración: `feature.experimental.aexperience = true|false`.
- Si está desactivado, el código no se ejecuta ni impacta el rendimiento.
- El flag puede gestionarse desde `application.properties` (nivel técnico) o la UI principal (nivel usuario).

### 3. Experiencia de usuario opt-in
- La barra superior o menú de usuario incluye la opción **"🧠 Activar experiencia aumentada (experimental)"**.
- Al activarla:
  - Se inicializa el Surfer Assistant (IA + UX adaptativa).
  - Se guarda la preferencia por sesión o cuenta.
  - Se muestra una insignia "Experimental" para transparencia.

### 4. Modularidad y mantenimiento
- Arquitectura plug-and-play, fácil de agregar o retirar.
- Estilos, dependencias y rutas del módulo están aislados.
- La documentación y los scripts de build incluyen exclusión selectiva (`--exclude aexperience`).

## Principios de Diseño

1. **Innovar sin interrumpir**: la evolución experimental no afecta la estabilidad ni los flujos existentes.
2. **Opt-in consciente**: solo los usuarios que lo deseen experimentan la nueva experiencia.
3. **Modularidad real**: integrado, pero no acoplado. Fácil de activar, desactivar o eliminar.
4. **Evidencia antes de adopción**: las funcionalidades con valor probado se promoverán a features estables previa evaluación.
5. **Transparencia total**: toda función experimental está identificada visual y documentalmente.

## Alcance Inicial del Módulo (MVP)

Componentes base:

- Surfer Assistant (interfaz IA asistida)
- Navegación por intención (comandos naturales)
- UI adaptable (Surfer View)
- Aprendizaje de uso (recientes, frecuentes)
- Dock inferior dinámico (opcional)

**Objetivo del MVP**: Probar la interacción humano–plataforma y su impacto en descubrimiento, eficiencia y satisfacción del usuario.

## Ciclo de Vida Experimental

| Etapa | Descripción | Estado |
| --- | --- | --- |
| Exploración | Diseño conceptual, experimentación rápida | ✅ Activa |
| Integración controlada | Integrado como módulo experimental (opt-in) | ⏳ Próxima fase |
| Evaluación | Métricas de adopción, feedback y usabilidad | 📊 En planificación |
| Promoción o retiro | Si aporta valor → feature estable; si no → se desconecta sin impacto | 🔁 Continuo |

## Gobernanza y Responsabilidad

- El mantenimiento del módulo corresponde al aExperience Lab dentro del proyecto Homedir.
- Las decisiones de promoción, cambio o retiro se discuten con los maintainers de Homedir y la comunidad OpenSourceSantiago.
- Toda funcionalidad experimental incluye documentación técnica, evidencia de pruebas y plan de rollback o desactivación.

## Visión a Largo Plazo

Si la iniciativa demuestra valor real, el módulo puede convertirse en parte estable del producto como "Homedir aExperience", servir como framework reutilizable para otras plataformas open source y establecer un modelo replicable de innovación segura dentro del ecosistema de Homedir.

> "Innovar sin romper, experimentar sin miedo, evolucionar con evidencia." — Homedir aExperience Lab
