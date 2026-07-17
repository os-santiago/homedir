# Autonomous Decisions - Arquitectura Correcta

**Fecha**: 2026-07-16  
**Versión**: 2.0 (Corregida)

## 🎯 Separación de Responsabilidades

### **Principio Fundamental**

> **Autonomous Decisions** es parte del **AI SDLC (platform/)**, NO de la aplicación **HomeDir (quarkus-app/)**

```
AI SDLC (platform/)
  └─ Software Factory que CONSTRUYE código
     ├─ Toma decisiones autónomas
     ├─ Ejecuta SCC agent
     ├─ Mantiene estado en JSON
     └─ ⭐ GENERA código para homedir

HomeDir (quarkus-app/)
  └─ Aplicación PRODUCIDA por AI SDLC
     ├─ API REST de negocio
     ├─ UI web para usuarios
     └─ Dashboard de observabilidad (solo LEE estado del AI SDLC)
```

## 📂 Estructura de Archivos Correcta

```
platform/                                    # ← AI SDLC vive aquí
├── scripts/
│   ├── homedir-sdlc-worker.sh              # Worker principal
│   │   └─ log_autonomous_decision()        # ✅ Función para log
│   │
│   └── sdlc-log-autonomous-decision.sh     # ✅ Helper script
│
├── prompts/
│   ├── scc-autonomous-decision-guidelines.md   # ✅ Guías
│   └── example-autonomous-decision-*.md        # ✅ Ejemplos
│
└── state/ (runtime: /var/lib/homedir-sdlc/)
    ├── autonomous-decisions/                # ✅ JSON files generados
    │   ├── decision-1016-performance-67890.json
    │   ├── decision-1234-refactoring-12345.json
    │   └── ...
    ├── issues/
    ├── prs/
    └── run-summaries/

quarkus-app/                                 # ← HomeDir app vive aquí
└── src/main/java/.../sdlc/
    ├── SdlcObservabilityService.java        # ✅ Solo LEE JSON de platform/
    │   ├─ autonomousDecisions()             # Lee archivos JSON
    │   ├─ autonomousDecisionsForIssue()     # Filtra por issue
    │   └─ autonomousDecisionStats()         # Calcula stats
    │
    ├── SdlcApiResource.java                 # ✅ Solo EXPONE API REST
    │   ├─ GET /api/sdlc/autonomous-decisions
    │   ├─ GET /api/sdlc/autonomous-decisions/issue/{id}
    │   └─ GET /api/sdlc/autonomous-decisions/stats
    │
    ├── SdlcDashboardResource.java           # ✅ Renderiza dashboard UI
    │
    └── (NO modelo AutonomousDecision)       # ❌ Eliminado
```

## 🔄 Flujo de Datos

```
1. WORKER (platform/) toma decisión autónoma
   └─> log_autonomous_decision() 
       └─> Escribe: /var/lib/homedir-sdlc/autonomous-decisions/decision-*.json

2. DASHBOARD (quarkus-app/) muestra observabilidad
   └─> SdlcObservabilityService lee JSON files
       └─> SdlcApiResource expone via REST API
           └─> Dashboard UI renderiza en navegador
```

## 📄 Formato de Archivo JSON

**Generado por**: `platform/scripts/sdlc-log-autonomous-decision.sh`

**Ubicación**: `/var/lib/homedir-sdlc/autonomous-decisions/decision-{issue}-{category}-{timestamp}.json`

**Estructura**:
```json
{
  "id": "decision-1016-performance-67890",
  "issueNumber": 1016,
  "prNumber": 1300,
  "category": "PERFORMANCE",
  "decision": "Consolidated CSS files (49KB + 54KB → 70KB)",
  "rationale": "Reduces HTTP requests from 2 to 1, removes 150 duplicates",
  "pattern": "Single CSS file per feature (existing codebase pattern)",
  "reversibility": "Yes - original files preserved in git history",
  "confidence": "HIGH",
  "timestamp": "2026-07-16T18:30:00Z",
  "needsReview": false,
  "metadata": {
    "worker": "homedir-sdlc-worker",
    "workerVersion": "1.0.0"
  }
}
```

## 🔧 Componentes por Capa

### **Layer 1: AI SDLC Worker (platform/)**

**Responsabilidad**: Tomar y registrar decisiones autónomas

**Archivos**:
1. ✅ `platform/scripts/homedir-sdlc-worker.sh`
   - Función `log_autonomous_decision()`
   - Integrado en flujo de SCC execution

2. ✅ `platform/scripts/sdlc-log-autonomous-decision.sh`
   - Script helper standalone
   - Puede llamarse manualmente para testing

3. ✅ `platform/prompts/scc-autonomous-decision-guidelines.md`
   - Guías para worker AI
   - Decision matrix
   - Best practices

**No tiene**:
- ❌ Código Java
- ❌ Tests unitarios JUnit
- ❌ Clases o modelos

**Testing**:
```bash
# Test manual
./platform/scripts/sdlc-log-autonomous-decision.sh \
  1016 \
  "PERFORMANCE" \
  "Consolidated CSS" \
  "Reduces HTTP requests" \
  "Single file pattern" \
  "Yes" \
  "HIGH" \
  1300

# Verifica archivo creado
ls -l /var/lib/homedir-sdlc/autonomous-decisions/
cat /var/lib/homedir-sdlc/autonomous-decisions/decision-1016-*.json
```

### **Layer 2: Observability Service (quarkus-app/)**

**Responsabilidad**: Leer y exponer datos de decisiones

**Archivos**:
1. ✅ `SdlcObservabilityService.java`
   ```java
   // Lee archivos JSON directamente
   public List<Map<String, Object>> autonomousDecisions() {
     return readJsonLines(
       stateDir().resolve("autonomous-decisions"), 
       500
     );
   }
   ```

2. ✅ `SdlcApiResource.java`
   ```java
   @GET
   @Path("autonomous-decisions")
   public Response autonomousDecisions() {
     return read(service.autonomousDecisions());
   }
   ```

**No tiene**:
- ❌ Modelo `AutonomousDecision.java` (eliminado)
- ❌ Lógica de negocio de decisiones
- ❌ Generación de archivos JSON

**Testing**:
```java
@Test
void autonomousDecisionsReadsFromPlatformState() {
  // Setup: Create test JSON file in state dir
  // Test: Call API endpoint
  // Verify: Returns parsed JSON data
  given()
    .when()
    .get("/api/sdlc/autonomous-decisions")
    .then()
    .statusCode(200);
}
```

### **Layer 3: Dashboard UI (quarkus-app/)**

**Responsabilidad**: Visualizar decisiones en navegador

**Archivos**:
1. ✅ `templates/sdlc/dashboard/index.qute.html`
   - Nueva sección "Autonomous Decisions"

2. ✅ `dashboard-v2.js`
   - Fetch API REST
   - Render decision cards
   - Show statistics

3. ✅ `dashboard.css`
   - Estilos para cards
   - Color coding por categoría

**No tiene**:
- ❌ Lógica de decisión
- ❌ Acceso directo a filesystem
- ❌ Generación de JSON

## 🧪 Testing Strategy

### **Platform (Bash Scripts)**

```bash
# 1. Test helper script
cd platform/scripts
./sdlc-log-autonomous-decision.sh \
  1234 "REFACTORING" "Test decision" "Test rationale" \
  "Test pattern" "Yes" "MEDIUM"

# 2. Verificar JSON creado
cat /var/lib/homedir-sdlc/autonomous-decisions/decision-1234-*.json

# 3. Test en worker (integration)
# Trigger worker con issue real
# Verificar que log_autonomous_decision() se llama
# Verificar archivo JSON generado
```

### **Quarkus App (Java/REST)**

```bash
# 1. Unit tests
cd quarkus-app
./mvnw test -Dtest=SdlcApiResourceTest#autonomousDecisions*

# 2. Integration test
# Start app
./mvnw quarkus:dev

# Create test JSON
mkdir -p /tmp/homedir-sdlc/autonomous-decisions
echo '{"id":"test",...}' > /tmp/homedir-sdlc/autonomous-decisions/test.json

# Test API
curl http://localhost:8080/api/sdlc/autonomous-decisions

# 3. UI test
# Open: http://localhost:8080/sdlc/dashboard
# Navigate to "Autonomous Decisions" tab
# Verify decision cards render correctly
```

## 📊 Data Flow Examples

### **Example 1: Worker Logs Decision**

```bash
# Worker script ejecuta:
log_autonomous_decision \
  1016 \
  "PERFORMANCE" \
  "Consolidated CSS files" \
  "Reduces HTTP requests, -32% size" \
  "Single file per feature" \
  "Yes" \
  "HIGH" \
  1300

# Genera:
/var/lib/homedir-sdlc/autonomous-decisions/decision-1016-performance-67890.json
```

### **Example 2: Dashboard Reads and Displays**

```javascript
// 1. Frontend fetch
fetch('/api/sdlc/autonomous-decisions')

// 2. Backend (SdlcApiResource)
@GET /api/sdlc/autonomous-decisions
  └─> service.autonomousDecisions()

// 3. Service (SdlcObservabilityService)
autonomousDecisions()
  └─> readJsonLines(path, 500)
      └─> Files.list(autonomous-decisions/)
          └─> mapper.readValue(line, MAP)

// 4. Returns to frontend
[
  { id: "decision-1016-...", category: "PERFORMANCE", ... },
  { id: "decision-1234-...", category: "REFACTORING", ... }
]

// 5. Frontend renders
<decision-card category="PERFORMANCE">...</decision-card>
```

## 🎯 Key Principles

### ✅ **DO**

1. **Worker genera JSON**
   - `log_autonomous_decision()` en bash
   - Escribe en `platform/state/autonomous-decisions/`

2. **Dashboard solo lee**
   - `SdlcObservabilityService` lee JSON
   - `SdlcApiResource` expone vía REST
   - UI renderiza datos

3. **Separación clara**
   - Platform = Producer (genera decisiones)
   - QuarkusApp = Consumer (muestra decisiones)

### ❌ **DON'T**

1. **No modelos Java en quarkus-app**
   - ❌ No `AutonomousDecision.java`
   - ❌ No lógica de decisión en Java
   - ❌ No generación de JSON en Quarkus

2. **No lógica de decisión en dashboard**
   - ❌ Dashboard no decide
   - ❌ Dashboard no modifica JSON
   - ❌ Dashboard solo observa

3. **No mezclar capas**
   - ❌ Worker no llama API REST
   - ❌ Quarkus no ejecuta worker
   - ❌ UI no escribe filesystem

## 📁 Final File Count

### **Platform/ (AI SDLC)**
- ✅ `scripts/homedir-sdlc-worker.sh` (modificado)
- ✅ `scripts/sdlc-log-autonomous-decision.sh` (nuevo)
- ✅ `prompts/scc-autonomous-decision-guidelines.md` (nuevo)
- ✅ `prompts/example-autonomous-decision-css-consolidation.md` (nuevo)

**Total Platform**: 2 nuevos archivos + 1 modificado

### **QuarkusApp/ (HomeDir App)**
- ✅ `SdlcObservabilityService.java` (modificado: +3 métodos)
- ✅ `SdlcApiResource.java` (modificado: +3 endpoints)
- ✅ `SdlcApiResourceTest.java` (modificado: +2 tests)
- ✅ `index.qute.html` (modificado: +1 section)
- ✅ `dashboard-v2.js` (modificado: +3 functions)
- ✅ `dashboard.css` (modificado: +decision styles)
- ❌ `AutonomousDecision.java` (ELIMINADO)
- ❌ `AutonomousDecisionTest.java` (ELIMINADO)

**Total QuarkusApp**: 6 modificados, 2 eliminados, 0 nuevos

## 🎓 Lessons Learned

### **Por qué esta separación es importante**

1. **Single Responsibility**
   - Platform: genera código y toma decisiones
   - QuarkusApp: provee valor de negocio

2. **Testability**
   - Platform: tests bash con filesystem
   - QuarkusApp: tests Java con mocks

3. **Deployment Independence**
   - Platform: se actualiza worker en VPS
   - QuarkusApp: se deploya app principal

4. **Clear Boundaries**
   - Si algo "observa" el SDLC → quarkus-app
   - Si algo "ejecuta" el SDLC → platform

---

**Status**: ✅ Arquitectura Corregida  
**Version**: 2.0  
**Last Updated**: 2026-07-16
