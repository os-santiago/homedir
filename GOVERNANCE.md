# Gobernanza del Proyecto Homedir

## Mantenedores Oficiales

Los **mantenedores** son responsables de revisar y aprobar Pull Requests, mantener la calidad del código, y guiar la dirección técnica del proyecto.

### Mantenedores Actuales

| Usuario GitHub | Rol | Área de Responsabilidad |
|----------------|-----|-------------------------|
| [@scanales-stack](https://github.com/scanales-stack) | Lead Maintainer | Arquitectura general, releases, CI/CD |

**Equipo en GitHub Organizations:** [@os-santiago/core-devs](https://github.com/orgs/os-santiago/teams/core-devs)

---

## Cómo Convertirse en Mantenedor

La comunidad de Homedir valora la participación activa y constante. Un colaborador puede convertirse en mantenedor oficial al cumplir los siguientes criterios:

### Requisitos para Ascenso a Mantenedor

1. **Contribuciones Técnicas Consistentes**
   - Haber contribuido con al menos **5 Pull Requests** valiosos que hayan sido aceptados y mergeados
   - Las contribuciones deben demostrar comprensión profunda del código base y arquitectura
   - Incluye: nuevas features, bug fixes significativos, mejoras de rendimiento, o refactorizaciones importantes

2. **Participación en Code Review**
   - Haber revisado al menos **10 Pull Requests** de otros colaboradores
   - Proporcionar feedback constructivo y detallado
   - Demostrar conocimiento de las prácticas de calidad del proyecto

3. **Actividad Sostenida**
   - Estar activo en la comunidad durante **al menos 3 meses**
   - Participar en discusiones de issues y Pull Requests
   - Responder a preguntas de otros colaboradores

4. **Alineación con los Valores del Proyecto**
   - Seguir el [Code of Conduct](CODE_OF_CONDUCT.md)
   - Demostrar colaboración respetuosa y constructiva
   - Apoyar a nuevos colaboradores

5. **Conocimiento del Ecosistema**
   - Familiaridad con el stack tecnológico: Quarkus, Java 21, Maven
   - Comprensión de los workflows de CI/CD y procesos de release
   - Conocimiento de las convenciones de código y estándares de calidad

### Proceso de Nominación

1. Un mantenedor existente nomina al candidato abriendo un issue privado en la organización
2. Los mantenedores actuales evalúan las contribuciones del candidato
3. Se requiere **mayoría simple** (>50%) de aprobación de los mantenedores existentes
4. El candidato es invitado al equipo `@os-santiago/core-devs`
5. Se actualiza este documento con el nuevo mantenedor

---

## Responsabilidades de los Mantenedores

### Revisión de Pull Requests
- Revisar PRs en tiempo razonable (objetivo: 48 horas para primera respuesta)
- Asegurar que cumplen con los estándares de calidad y testing
- Proveer feedback constructivo y educativo

### Gestión de Issues
- Triagear nuevos issues
- Asignar labels apropiados
- Cerrar issues duplicados o resueltos

### Releases y Versionamiento
- Participar en la planificación de releases
- Revisar y aprobar release notes
- Asegurar compatibilidad backward cuando sea necesario

### Mentoría de la Comunidad
- Guiar a nuevos colaboradores
- Responder preguntas técnicas
- Fomentar un ambiente inclusivo y colaborativo

### Mantenimiento del Código
- Mantener la documentación actualizada
- Refactorizar código legacy cuando sea necesario
- Mejorar la infraestructura de CI/CD

---

## Toma de Decisiones

### Decisiones Técnicas Menores
- Un solo mantenedor puede aprobar y mergear PRs que:
  - Arreglan bugs menores
  - Mejoran documentación
  - Actualizan dependencias sin breaking changes
  - Agregan tests

### Decisiones Técnicas Mayores
Requieren **consenso** (acuerdo de todos los mantenedores activos):
- Cambios de arquitectura significativos
- Adopción de nuevas tecnologías o frameworks
- Breaking changes en APIs públicas
- Cambios en el proceso de release

### Proceso de Consenso
1. Abrir un issue o RFC (Request for Comments) explicando la propuesta
2. Período de discusión de al menos **7 días**
3. Los mantenedores expresan su posición (aprobación, rechazo, o abstención)
4. Si hay desacuerdo, se busca llegar a un compromiso
5. En caso de empate, el Lead Maintainer tiene voto de desempate

---

## Inactividad de Mantenedores

- Un mantenedor que está inactivo por **6 meses** sin comunicación previa puede ser movido a la categoría de "Mantenedor Emérito"
- Los Mantenedores Eméritos son reconocidos por sus contribuciones pasadas pero no tienen derechos de aprobación
- Pueden regresar a mantenedor activo en cualquier momento notificando al equipo

---

## Transparencia y Comunicación

### Canales Oficiales
- **GitHub Issues/PRs**: Discusiones técnicas públicas
- **Discord**: [Open Source Santiago](https://discord.gg/3eawzc9ybc) - Chat en tiempo real
- **GitHub Discussions**: RFCs y propuestas de largo plazo

### Reportes de Estado
- Los mantenedores publican un resumen mensual de actividad del proyecto
- Se comunican decisiones mayores a través de GitHub Discussions

---

## Reconocimiento Público

### Insignias de Organización
Los miembros del equipo `@os-santiago/core-devs` pueden configurar su membresía como **pública** en la configuración de la organización para mostrar la insignia de **Open Source Santiago** en sus perfiles de GitHub.

**Cómo hacer pública tu membresía:**
1. Ve a https://github.com/orgs/os-santiago/people
2. Busca tu usuario
3. Cambia la visibilidad de "Private" a "Public"

### All Contributors
Este proyecto sigue la especificación [All Contributors](https://github.com/all-contributors/all-contributors). Reconocemos contribuciones de todo tipo, no solo código.

---

## Modificaciones a este Documento

Cambios a `GOVERNANCE.md` requieren:
- Aprobación de **todos los mantenedores activos**
- Un período de comentarios públicos de **14 días**
- Ser documentados en el changelog del proyecto

---

**Última actualización:** 2026-08-04

**Versión:** 1.0.0
