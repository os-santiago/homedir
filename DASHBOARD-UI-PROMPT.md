# Dashboard UI de Observabilidad para HomeDir AI SDLC

## Contexto

El sistema AI SDLC está operando con ~98% de autonomía, procesando issues desde admission hasta deployment automático. Sin embargo, actualmente no existe una interfaz visual para:
- Monitorear el estado en tiempo real del pipeline
- Detectar anomalías y cuellos de botella
- Auditar el historial de decisiones autónomas
- Visualizar métricas de performance y autonomía

**Necesidad**: Dashboard web centralizado que proporcione observabilidad completa del sistema AI SDLC.

---

## Problema Statement

**Como**: DevOps/Platform Engineer  
**Necesito**: Un dashboard visual de observabilidad del AI SDLC  
**Para**: Monitorear salud del sistema, detectar anomalías y auditar decisiones autónomas sin necesidad de SSH al VPS o revisar logs manualmente

**Pain points actuales**:
1. ❌ No hay visibilidad en tiempo real del estado del worker
2. ❌ Detección de anomalías requiere revisar logs manualmente
3. ❌ No hay métricas históricas de performance del SDLC
4. ❌ Difícil auditar decisiones del sistema (por qué un issue fue rechazado, etc.)
5. ❌ No hay alertas visuales de componentes degradados

---

## Acceptance Criteria

### 1. Vista de Estado del Sistema (Real-time)
- [ ] Muestra estado actual del worker (running/idle/error)
- [ ] Heartbeat timestamp y latencia
- [ ] Estado de componentes críticos:
  - [ ] SCC availability
  - [ ] GitHub API rate limits
  - [ ] VPS resources (CPU, memoria, disco)
  - [ ] Webhook handler status
- [ ] Indicadores visuales (verde/amarillo/rojo) para cada componente

### 2. Pipeline Flow Visualization
- [ ] Diagrama de flujo visual del SDLC pipeline:
  ```
  [Issue Created] → [Admission Review] → [Accepted/Rejected] → 
  [Queued] → [SCC Processing] → [PR Created] → [CI Checks] → 
  [Auto-merge] → [Deployed] → [Issue Closed]
  ```
- [ ] Cada etapa muestra:
  - [ ] Issues/PRs actualmente en esa etapa
  - [ ] Tiempo promedio en etapa
  - [ ] Anomalías (stuck > X tiempo)
- [ ] Click en etapa para ver detalles

### 3. Active Issues & PRs Dashboard
- [ ] Lista de issues activos con:
  - [ ] Número, título, labels
  - [ ] Estado actual en pipeline
  - [ ] Tiempo en estado actual
  - [ ] Estimación de tiempo restante
  - [ ] Link a GitHub
- [ ] Lista de PRs activos con:
  - [ ] Número, título, checks status
  - [ ] Auto-merge enabled/disabled
  - [ ] Estado de checks (passing/failing/pending)
  - [ ] Link a GitHub

### 4. Métricas Históricas
- [ ] **Autonomía Metrics**:
  - [ ] % de issues procesados sin intervención manual
  - [ ] % de PRs auto-merged exitosamente
  - [ ] Tasa de admisión (accepted/rejected/needs-human)
- [ ] **Performance Metrics**:
  - [ ] Tiempo promedio: Issue → PR created
  - [ ] Tiempo promedio: PR created → Merged
  - [ ] Tiempo promedio: End-to-end (Issue → Closed)
  - [ ] SCC processing time por complejidad
- [ ] **Throughput Metrics**:
  - [ ] Issues procesados por día/semana
  - [ ] PRs creados por día/semana
  - [ ] PRs merged por día/semana
- [ ] Gráficas de tendencias (últimos 7/30/90 días)

### 5. Anomaly Detection Dashboard
- [ ] Lista de anomalías detectadas:
  - [ ] Issues stuck en admission > 10 min
  - [ ] PRs con checks failing > 3 intentos
  - [ ] SCC timeouts
  - [ ] Worker sin heartbeat > 5 min
  - [ ] GitHub API rate limit warnings
- [ ] Cada anomalía muestra:
  - [ ] Timestamp de detección
  - [ ] Severidad (critical/warning/info)
  - [ ] Descripción del problema
  - [ ] Suggested action
  - [ ] Link a logs relevantes

### 6. Audit Trail Viewer
- [ ] Búsqueda de eventos del SDLC:
  - [ ] Por issue number
  - [ ] Por PR number
  - [ ] Por fecha/rango
  - [ ] Por tipo de evento
- [ ] Timeline visual de eventos para un issue/PR:
  - [ ] Admission decision (accepted/rejected + reasoning)
  - [ ] SCC execution (prompt, duration, exit code)
  - [ ] PR creation
  - [ ] Check results
  - [ ] Auto-merge attempts
  - [ ] Deployment verification
- [ ] Cada evento muestra:
  - [ ] Timestamp
  - [ ] Actor (worker/webhook/manual)
  - [ ] Decisión tomada
  - [ ] Reasoning/context
  - [ ] Links a artifacts (logs, PRs, commits)

### 7. Configuration & Control Panel
- [ ] Ver configuración actual del worker:
  - [ ] Environment variables
  - [ ] Labels configurados
  - [ ] Timeouts configurados
- [ ] Controles operacionales:
  - [ ] Pause/Resume worker (safe shutdown)
  - [ ] Trigger manual reconciliation
  - [ ] Clear stale locks
  - [ ] View heartbeat history

---

## Technical Requirements

### Stack Propuesto
**Backend**:
- Framework: **Node.js/Express** o **Quarkus** (reutilizar stack existente)
- API REST para datos del SDLC
- WebSocket para updates en tiempo real

**Frontend**:
- Framework: **React** + **TypeScript**
- UI Library: **Tailwind CSS** + **shadcn/ui** (retro theme compatible)
- Charts: **Recharts** o **Chart.js**
- Real-time: **Socket.io-client** o **SSE**

**Data Sources**:
- Worker heartbeat file: `/var/lib/homedir-sdlc/heartbeat.json`
- Worker logs: `/var/log/homedir-sdlc-worker.log`
- Issue states: `/var/lib/homedir-sdlc/issues/*.json`
- PR states: `/var/lib/homedir-sdlc/prs/*.json`
- Run summaries: `/var/lib/homedir-sdlc/run-summaries/*.jsonl`
- GitHub API: Para datos en tiempo real de issues/PRs

### API Endpoints (Mínimos)

```typescript
GET  /api/sdlc/status          // Current system status
GET  /api/sdlc/heartbeat       // Latest heartbeat
GET  /api/sdlc/pipeline        // Pipeline state summary
GET  /api/sdlc/issues          // Active issues
GET  /api/sdlc/prs             // Active PRs
GET  /api/sdlc/metrics         // Historical metrics
GET  /api/sdlc/anomalies       // Detected anomalies
GET  /api/sdlc/audit/:id       // Audit trail for issue/PR
POST /api/sdlc/control/pause   // Pause worker
POST /api/sdlc/control/resume  // Resume worker
WS   /api/sdlc/stream          // WebSocket for real-time updates
```

### Data Models

```typescript
interface SystemStatus {
  worker: {
    state: 'running' | 'idle' | 'error' | 'paused';
    lastHeartbeat: Date;
    heartbeatAge: number; // seconds
  };
  components: {
    scc: { available: boolean; version: string };
    github: { rateLimit: { remaining: number; reset: Date } };
    vps: { cpu: number; memory: number; disk: number };
    webhook: { listening: boolean; port: number };
  };
}

interface PipelineStage {
  name: string;
  count: number;          // items currently in stage
  avgDuration: number;    // seconds
  issues: Issue[];
}

interface Issue {
  number: number;
  title: string;
  state: 'admission' | 'queued' | 'running' | 'pr-open' | 'merged' | 'closed';
  labels: string[];
  createdAt: Date;
  currentStateAt: Date;
  estimatedCompletion?: Date;
  githubUrl: string;
}

interface Anomaly {
  id: string;
  timestamp: Date;
  severity: 'critical' | 'warning' | 'info';
  type: 'stuck-issue' | 'failing-checks' | 'timeout' | 'worker-down' | 'rate-limit';
  description: string;
  suggestedAction: string;
  affectedResource: { type: 'issue' | 'pr'; number: number };
}

interface AuditEvent {
  timestamp: Date;
  issueNumber?: number;
  prNumber?: number;
  eventType: 'admission' | 'scc-execution' | 'pr-created' | 'checks' | 'merge' | 'deployment';
  actor: 'worker' | 'webhook' | 'manual';
  decision: string;
  reasoning?: string;
  metadata: Record<string, any>;
}
```

### Security Considerations
- [ ] Dashboard accesible solo via VPN o localhost (no public)
- [ ] Authentication requerida (reutilizar auth de Quarkus app)
- [ ] Rate limiting en API endpoints
- [ ] Validación de inputs en control endpoints
- [ ] Logs de acciones administrativas (pause/resume)

---

## UI/UX Design Guidelines

### Layout
```
+------------------------------------------------------------------+
| Header: [HomeDir AI SDLC Dashboard] [Status: ●] [Last sync: 2s] |
+------------------------------------------------------------------+
| Sidebar:                 | Main Content Area                      |
| - Overview              |                                        |
| - Pipeline Flow         | +------------------------------------+ |
| - Active Items          | | Selected View Content              | |
| - Metrics               | |                                    | |
| - Anomalies             | |                                    | |
| - Audit Trail           | |                                    | |
| - Configuration         | +------------------------------------+ |
+------------------------------------------------------------------+
| Footer: [VPS: vh01] [Worker: v1.0] [API: healthy]               |
+------------------------------------------------------------------+
```

### Color Scheme (Retro Theme Compatible)
- **Success/Healthy**: Green (#10b981)
- **Warning**: Yellow (#f59e0b)
- **Error/Critical**: Red (#ef4444)
- **Info**: Blue (#3b82f6)
- **Background**: Match retro theme dark background
- **Accent**: Purple (#8b5cf6) for highlights

### Real-time Updates
- Auto-refresh every 3 seconds (configurable)
- WebSocket notifications para eventos críticos
- Visual pulse indicator cuando hay updates
- Toast notifications para anomalías nuevas

---

## Implementation Phases

### Phase 1: Core Backend API (P0)
- [ ] API endpoints para status, heartbeat, issues, PRs
- [ ] Parser para worker state files
- [ ] GitHub API integration para datos en tiempo real
- [ ] Basic health checks

### Phase 2: Frontend Shell (P0)
- [ ] React app setup con routing
- [ ] Layout básico con sidebar navigation
- [ ] Overview dashboard con system status
- [ ] Active issues/PRs list views

### Phase 3: Metrics & Visualization (P1)
- [ ] Historical metrics calculation
- [ ] Charts para trends
- [ ] Performance metrics dashboard
- [ ] Autonomía metrics tracking

### Phase 4: Anomaly Detection (P1)
- [ ] Anomaly detection logic
- [ ] Anomalies dashboard
- [ ] Alert notifications
- [ ] Suggested actions

### Phase 5: Audit Trail (P1)
- [ ] Audit event aggregation
- [ ] Timeline visualization
- [ ] Search & filter functionality
- [ ] Event detail views

### Phase 6: Control Panel (P2)
- [ ] Configuration viewer
- [ ] Worker control endpoints
- [ ] Admin actions logging
- [ ] Safety confirmations

---

## Success Metrics

**Adoption**:
- Dashboard consultado al menos 1x/día por equipo
- Reducción de SSH sessions al VPS en 80%

**Effectiveness**:
- Anomalías detectadas visualmente en <30s
- 100% de incidents auditables via dashboard
- Tiempo de diagnóstico de problemas: -70%

**System Health**:
- 0 blind spots en pipeline (100% visibility)
- Alertas automáticas para degradaciones

---

## Non-Functional Requirements

- **Performance**: Dashboard carga en <2s, API responses <500ms
- **Scalability**: Soporta 1000+ issues históricos sin degradación
- **Availability**: 99.9% uptime (colocated con Quarkus app)
- **Maintainability**: Code coverage >80%, documented APIs
- **Accessibility**: WCAG 2.1 Level AA compliance

---

## Out of Scope (V1)

- ❌ Modificación de issues/PRs desde dashboard (usar GitHub)
- ❌ Re-trigger de SCC execution manualmente
- ❌ Historical log viewer completo (solo summaries)
- ❌ Multi-repository support (solo `os-santiago/homedir`)
- ❌ Mobile app / responsive design (desktop-first)

---

## References

### Existing Infrastructure
- Worker script: `platform/scripts/homedir-sdlc-worker.sh`
- State directory: `/var/lib/homedir-sdlc/`
- Quarkus app: `quarkus-app/`
- Retro theme: `quarkus-app/src/main/resources/META-INF/resources/css/retro-theme.css`

### Similar Dashboards (Inspiration)
- GitHub Actions dashboard
- Jenkins Blue Ocean
- GitLab CI/CD pipelines
- Argo CD UI
- Grafana dashboards

### Technologies to Evaluate
- **Streaming**: Server-Sent Events (SSE) vs WebSockets
- **State Management**: React Context vs Zustand vs Redux
- **Backend**: Quarkus REST vs standalone Express server
- **Deployment**: Same VPS vs separate container

---

## Priority Justification

**Priority**: **P0** (High Priority)

**Justification**:
- Sistema AI SDLC está en producción procesando issues reales
- Sin dashboard, debugging requiere SSH manual y log parsing
- Detección de anomalías es reactiva en lugar de proactiva
- Auditoría de decisiones autónomas es crítica para confianza
- Costo de implementación: ~2-3 días, ROI inmediato

**Dependencies**:
- ✅ Worker script estable y en producción
- ✅ State files bien estructurados
- ✅ Quarkus app desplegada
- ❌ Requiere: API REST en Quarkus o Node.js service

---

## Delivery

### Entregables
1. Backend API funcional con endpoints documentados
2. Frontend React app con vistas core
3. Deployment guide para VPS
4. API documentation (OpenAPI/Swagger)
5. Screenshots del dashboard funcional

### Definition of Done
- [ ] Dashboard accesible via `https://homedir.dev/sdlc/dashboard`
- [ ] Muestra datos en tiempo real del worker
- [ ] Al menos 3 views funcionando (overview, issues, metrics)
- [ ] API documentada con ejemplos
- [ ] Tests: >70% coverage
- [ ] Deployed en VPS y funcionando 24/7

---

**Estimación**: 3-5 días (1 dev full-time)  
**Labels**: `enhancement`, `priority:P0`, `ai-sdlc`, `dashboard`, `ready-to-implement`  
**Milestone**: Q3 2026 - AI SDLC Observability  
**Assignee**: AI SDLC (autonomous implementation)

---

🤖 _This specification was generated to enable autonomous implementation by the HomeDir AI SDLC system._
