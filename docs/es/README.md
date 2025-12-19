# HomeDir
> **DevRel, OpenSource, InnerSource Community Platform**

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![CI Tests](https://github.com/os-santiago/homedir/actions/workflows/pr-check.yml/badge.svg?branch=main&label=CI%20Tests)](https://github.com/os-santiago/homedir/actions/workflows/pr-check.yml)
[![Version](https://img.shields.io/github/v/release/os-santiago/homedir?label=Version)](https://github.com/os-santiago/homedir/releases)
[![Discord](https://img.shields.io/badge/Discord-Join%20the%20chat-5865F2?logo=discord&logoColor=white)](https://discord.gg/3eawzc9ybc)

**Homedir** es una plataforma única diseñada para potenciar comunidades técnicas modernas. A diferencia de soluciones genéricas, Homedir se centra en la **identidad, el desarrollo profesional y la gamificación** de la participación en comunidades, actuando como un puente entre desarrolladores individuales y el ecosistema Open Source / Inner Source.

## Diferencial de Mercado: HomeDir & OpenQuest
> *"Más allá de la simulación."*

A diferencia de plataformas que funcionan como **"laboratorios eternos de cosas simuladas"** (e.g., Code Cloud), **HomeDir y OpenQuest** abren el mundo a **tareas reales**.

- **Experiencia Verificable**: No simulamos el trabajo; gamificamos el trabajo real. Las misiones son Issues de producción, los bugs son reales, y la experiencia (XP) es prueba de capacidad técnica demostrable.
- **Para Organizaciones Reales**: Transformamos backlogs aburridos en un **Tablero de Misiones (OpenQuest)** que motiva a equipos y comunidades.
- **Identidad Profesional Completa**: Tu perfil no muestra solo "cursos terminados", sino el impacto real que has tenido en proyectos vivos.

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
