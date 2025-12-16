# HomeDir
> **DevRel, OpenSource, InnerSource Community Platform**

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Quality Gate](https://github.com/scanalesespinoza/eventflow/actions/workflows/quality.yml/badge.svg)](https://github.com/scanalesespinoza/eventflow/actions/workflows/quality.yml)

**Homedir** es una plataforma única diseñada para potenciar comunidades técnicas modernas. A diferencia de soluciones genéricas, Homedir se centra en la **identidad, el desarrollo profesional y la gamificación** de la participación en comunidades, actuando como un puente entre desarrolladores individuales y el ecosistema Open Source / Inner Source.

## Diferencial de Mercado
Homedir no es solo un gestor de eventos o un directorio de miembros. Es un **Hub de Experiencia de Desarrollador (DevX)** que:
- Integra perfiles de usuario con su identidad real en GitHub.
- Gamifica la contribución mediante Quests, XP y niveles (RPG-style).
- Fomenta la colaboración InnerSource y Open Source en un entorno unificado.
- Proporciona una identidad visual única (Retro/Cyberpunk) que resuena con la cultura hacker.

## Características Principales

### 🌟 DevRel & Community
- **Perfiles Gamificados**: Los usuarios ganan XP y suben de nivel (Engineer, Mage, Warrior, Scientist) según sus contribuciones.
- **Directorio de Miembros**: Visibilidad para todos los integrantes, con búsqueda por skills y roles.
- **Integración GitHub**: Vinculación automática de cuentas y Pull Requests para unirse a la comunidad.

### 🛡️ Desarrollo Profesional
- **Quest Board**: Misiones técnicas reales (Issues) que otorgan recompensas y reconocimiento.
- **Showcase de Proyectos**: Espacio para destacar proyectos comunitarios y personales.

### 🚀 Stack Tecnológico e Innovación
Homedir está construido sobre tecnologías nativas de nube híbrida, testeado tanto en contenedores como en VPS tradicionales y Google Cloud.

- **Gestión de Eventos**: Sistema robusto para meetups, charlas y speakers.
- **Persistencia Singular**: Estrategia de persistencia optimizada (JSON/YAML backend con capacidades de GitOps).
- **Manejo de Sesiones & Cache**: Implementación personalizada de sesiones seguras y caché distribuido (in-memory/Redis ready) para alta performance.
- **Salud y Resiliencia**: Mecanismos avanzados de Health Checks y tolerancia a fallos.
- **Buenas Prácticas**: Arquitectura hexagonal, Clean Code, y pipelines de CI/CD rigurosos (Calidad, Seguridad, Supply Chain).

## Quick start
Ejecutar la aplicación en modo desarrollo:

```bash
mvn -f quarkus-app/pom.xml quarkus:dev
```

Luego visita `http://localhost:8080`.

## Configuración y Auth
La plataforma soporta autenticación híbrida:
- **Google OAuth**: Para acceso general y autenticación segura.
- **GitHub OAuth**: Para vinculación de identidad de desarrollador y operaciones de git.
- **Local Dev**: Modo offline para desarrollo rápido.

## Comunidad
Proyecto impulsado por la comunidad **OpenSource Santiago**.
Únete a nuestro [Discord](https://discord.gg/3eawzc9ybc).

---
*Homedir: Donde el código encuentra su hogar.*
