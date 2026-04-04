# Homedir Expert Agent

Expert assistant for the Homedir platform. This agent operates according to the **ADEV.md** normative rules and is optimized for the Quarkus/Go dual-stack architecture.

## 📜 Core Normative Rules (Summary from ADEV.md)
1. **Atomics**: 1 Iteration = 1 dedicated branch = 1 atomic PR.
2. **PR First**: No direct pushes to `main`.
3. **Rollout**: Mandatory 3-stage incremental rollout for new features (Hidden -> Integrated -> Cleanup).
4. **Validation**: Local validation > 95% success rate required before PR.
5. **Quality**: CI green + Production verification mandatory for every PR.
6. **Persistence**: Use and extend Homedir's storage; avoid external services unless explicit.
7. **Documentation**: Bilingual official structure (English canon, Spanish mirror).

## 🛠️ Architecture and Stack
- **Backend (Main)**: Quarkus (Java 21) in `quarkus-app/`.
- **Backend (Core)**: Go-based event processing.
- **Tools**: Python scripts in `scripts/` (knowledge, testing, automation).

## 🛠️ Operational Workflow (SDLC)
- **Branching**: `feat/*`, `fix/*`, `hotfix/*`, `docs/*`, `chore/*`.
- **Commits**: Conventional Commits only.
- **Native Build**: Use Podman in `quarkus-app/` for native image validation before pushing critical UI/Auth fixes.
- **Rollout**:
  - Stage 1: New resource hidden/no-consumption.
  - Stage 2: Progressive integration.
  - Stage 3: Legacy removal after production validation.

## 📂 Shared Workspace & Handoff (Rule #29)
The following files must be kept consistent at every checkpoint:
- `LATEST.txt`: Current version/milestone.
- `HANDOFF.md`: Context for the next agent/session.
- `state.json`: Machine-readable state.
- `SESSION-LOG.md`: Log of actions and outcomes.
- `DECISIONS.md`: Durable architectural or policy decisions.

## 🧪 Quick Test Commands
| Target | Command | Script |
|---|---|---|
| Java Fast | `mvn test -DskipITs=true` | `scripts/test-fast.sh` |
| Java Full | `mvn verify -Pcoverage` | `scripts/test-all.sh` |
| Python | `pytest tests/` | - |
