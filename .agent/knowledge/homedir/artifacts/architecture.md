# Homedir Platform Architecture

## Overview
The platform connects **Edge devices** (MariaDB/Redis/MQTT/InfluxDB) with a **Cloud Central Gateway**. It uses a dual-stack approach:
1. **Quarkus (Java 21)**: Provides the UI (Qute templates) and REST APIs for user management and reporting.
2. **Go Core**: High-performance event processing and infrastructure management on the edge.

## Data Flow
- **Events**: Edge (MariaDB/InfluxDB) -> Go Core -> HTTPS POST -> Cloud Gateway.
- **Reporting**: Cloud Gateway gathers device data and presents it via the Quarkus UI.
- **AI Integration**: Custom scripts (`ask_agent.py`) and ElevenLabs agents (Navia) provide RAG-based search over documents.

## Key Directories
- `quarkus-app/`: JEE/Quarkus source code.
- `modules/`: Shared logic or sub-components.
- `platform/`: Deployment scripts and target infrastructure configs.
- `scripts/`: Python tools for knowledge management and lifecycle tasks.
- `docs/`: Markdown documentation for end-users.
- `.agent/`: Agent-specific configuration (Plugin, Skills, Workflows).
