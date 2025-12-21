# HomeDir
> **DevRel, OpenSource, InnerSource Community Platform**

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![CI Tests](https://github.com/os-santiago/homedir/actions/workflows/pr-check.yml/badge.svg?branch=main&label=CI%20Tests)](https://github.com/os-santiago/homedir/actions/workflows/pr-check.yml)
[![Version](https://img.shields.io/github/v/release/os-santiago/homedir?label=Version)](https://github.com/os-santiago/homedir/releases)
[![Discord](https://img.shields.io/badge/Discord-Join%20the%20chat-5865F2?logo=discord&logoColor=white)](https://discord.gg/3eawzc9ybc)

**Homedir** is a unique platform designed to empower modern technical communities. Unlike generic solutions, Homedir focuses on **identity, professional development, and gamification** of community participation, acting as a bridge between individual developers and the Open Source / Inner Source ecosystem.

## Market Differentiator: HomeDir & OpenQuest
> *"Beyond simulation."*

Unlike platforms that function as **"eternal labs of simulated things"** (e.g., Code Cloud), **HomeDir and OpenQuest** open the world to **real tasks**.

- **Verifiable Experience**: We don't simulate work; we gamify real work. Quests are production Issues, bugs are real, and experience (XP) is proof of demonstrable technical capability.
- **For Real Organizations**: We transform boring backlogs into a **Quest Board (OpenQuest)** that motivates teams and communities.
- **Complete Professional Identity**: Your profile doesn't just show "completed courses", but the real impact you've had on live projects.

## Key Features

### 🌟 DevRel & Community
- **Gamified Profiles**: Users earn XP and level up (Engineer, Mage, Warrior, Scientist) based on their contributions.
- **Member Directory**: Visibility for all members, searchable by skills and roles.
- **GitHub Integration**: Automatic account linking and Pull Requests to join the community.

### 🛡️ Professional Development
- **Quest Board**: Real technical missions (Issues) that grant rewards and recognition.
- **Project Showcase**: Space to highlight community and personal projects.

### 🚀 Tech Stack and Innovation
Homedir is built on hybrid cloud native technologies, tested on both containers and traditional VPS and Google Cloud.

- **Event Management**: Robust system for meetups, talks, and speakers.
- **Singular Persistence**: Optimized persistence strategy (JSON/YAML backend with GitOps capabilities).
- **Session Handling & Cache**: Custom implementation of secure sessions and distributed cache (in-memory/Redis ready) for high performance.
- **Health and Resilience**: Advanced Health Checks and fault tolerance mechanisms.
- **Best Practices**: Hexagonal Architecture, Clean Code, and rigorous CI/CD pipelines (Quality, Security, Supply Chain).

## Quick start
Run the application in development mode:

```bash
mvn -f quarkus-app/pom.xml quarkus:dev
```

Then visit `http://localhost:8080`.

## Configuration and Auth
The platform supports hybrid authentication:
- **Google OAuth**: For general access and secure authentication.
- **GitHub OAuth**: For developer identity linking and git operations.
- **Local Dev**: Offline mode for rapid development.

## Community
Project driven by the **OpenSource Santiago** community.
Join our [Discord](https://discord.gg/3eawzc9ybc).

---
*Homedir: Where code finds its home.*
