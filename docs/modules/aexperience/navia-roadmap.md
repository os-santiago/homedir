# 🌊 Navia – Roadmap de la Experiencia Aumentada en Homedir

## 🧠 Contexto

Navia es el prototipo de **experiencia aumentada** de Homedir. Evoluciona la navegación tradicional hacia un modelo guiado por intención, combinando una consola conversacional con un marco de contenido que reacciona sin necesidad de clics. La primera entrega (Pilar 1) se integra como módulo experimental dentro de Homedir y se activa mediante un *feature flag* controlado por el usuario.

---

## 🎯 Objetivo del prototipo (Pilar 1)

- **Layout:** Consola de Navia (1/3 del ancho) a la izquierda y *frame* web (2/3) a la derecha, con opción de alternar a distribución vertical (consola arriba, frame abajo).
- **Interacción:** El usuario conversa parcial o textualmente (p.ej. “Navia, estoy buscando el evento ‘Test event’”).
- **Comportamiento:** Sin clics. Navia interpreta la intención y actualiza el *frame* con la página o fragmento correspondiente; si no existe, entrega alternativas.
- **Datos:** La fuente principal es un **caché estático** de páginas y fragmentos precomputados. Cambios desde el panel de administración disparan invalidaciones y reconstrucciones.
- **Aislamiento:** Código en `/navia/*`, empaquetado en `modules/aexperience/navia`. Feature flag `feature.experimental.navia` controla toda la experiencia.

---

## 🗺️ Iteraciones (sprints cortos)

| Iteración | Meta | Alcance | Criterios de aceptación | Entregables |
|-----------|------|---------|-------------------------|-------------|
| **I0 – Foundation** | Estructura del módulo y *flags* | Crear módulo `modules/aexperience/navia`, ruta `/navia` con layout 1:3 (horizontal) y alternancia vertical; botón “🧠 Activar experiencia aumentada (experimental)” en la barra; feature flag `feature.experimental.navia` que desactiva completamente el módulo | Con el flag en `false`, ningún impacto en la app; con `true`, `/navia` es accesible y aislado; el *toggle* de orientación funciona | Skeleton UI + flag + rutas + README |
| **I1 – Caché estático** | Fuente de verdad rápida | Crawler/compilador que genera `site-cache` (JSON + fragmentos HTML): `/pages`, `/fragments`, `/routes` y `search-index` (BM25 básico); endpoint de administración para invalidar/actualizar | Construcción del caché < X s en entorno dev; admin pulsa “Actualizar” y el caché se refresca; el *frame* puede cargar una página desde caché por `routeId` | Job de *build* de caché + API `GET /navia/cache/*` + admin `POST /navia/cache/rebuild` |
| **I2 – Intérprete de intención (text first)** | Del mensaje a la acción | Parser de intención (reglas + coincidencia por *keywords*) para intents iniciales: `open_page(title/slug)`, `search(query)`, `open_event(name)`; resolución contra índice del caché; render en el *frame* sin clics | “Buscar evento ‘X’” abre la vista del evento si existe; si no, devuelve *top-5* similares; el *frame* cambia sin interacción adicional | Parser + resolutores contra caché |
| **I3 – Sugerencias y accesos rápidos** | Proactividad mínima viable | En la consola, chips de sugerencias (páginas comunes, recientes, frecuentes); en cada respuesta, CTA “ir a Agenda / Oradores”; *dock* liviano (recientes/frecuentes) solo dentro de `/navia` | Las sugerencias aparecen según consulta o contexto; al pulsar una sugerencia el *frame* cambia de vista | Sugeridor + ranking simple (frecuencia/recencia) |
| **I4 – ElevenLabs (voz y conversación)** | Multimodal y tiempo real | Integrar ElevenLabs: STT (entrada por voz) y TTS (respuesta hablada); modo “Hablar con Navia”; latencia baja usando modelos “Flash/RT” | El usuario dice “busca ‘Test event’” y Navia responde con voz + actualiza el *frame*; transcripción visible en la consola | Integración STT/TTS de ElevenLabs + control UI de micrófono |
| **I5 – RAG ligero sobre caché** | Responder con fragmentos | Q&A de fragmentos: para preguntas tipo “¿cuándo es la keynote?”, Navia extrae el fragmento (del caché) y lo inyecta en el *frame* (modo “respuesta focalizada”); si requiere razonamiento adicional, usar LLM vía ElevenLabs Agents | Preguntas sobre contenidos del sitio responden sin navegar toda la página; el *frame* muestra solo el bloque relevante | Extractor de fragmentos + resaltado en el *frame* |
| **I6 – Sincronía Admin→Caché** | Flujo de actualización | Cuando admin crea/edita contenido, evento dispara reconstrucción selectiva (por ruta/entidad) y *warm up* del caché; Navia consume la nueva versión en segundos | Cambios de admin se reflejan en `/navia` sin redeploy ni reinicios | Webhooks/cola + invalidación granular |
| **I7 – Métricas & Calidad** | Validación de valor | Telemetría: tasa de éxito (intent→navegación), tiempo a destino, abandono, uso de voz vs texto; panel básico para el equipo | Reporte muestra mejora de tiempo a destino respecto a la UI tradicional en tareas definidas | Instrumentación OTel + dashboard inicial |
| **I8 – Endurecimiento & RC** | Estabilizar o retirar | Endurecimiento de errores, *timeouts*, fallbacks y tests e2e; decidir: promover a beta estable o desactivar el feature | Con el feature activado, no rompe flujos base; con el feature apagado, el sistema es indiferente | Checklist de promoción/rollback |

---

## 🏗️ Diseño técnico (resumen)

### Aislamiento y *feature flags*
- Ruta dedicada: `/navia/*` (servicio aislado, no reemplaza la web actual).
- Paquetes: `modules/aexperience/navia/**` para UI, caché, intents, integraciones y telemetría.
- Feature flag `feature.experimental.navia` en configuración y toggle en la UI principal.

### Layout (I0)
- Distribución horizontal por defecto: consola 1/3 (izquierda) + *frame* 2/3 (derecha).
- Botón en la barra para alternar a layout vertical (consola arriba 1/3 + *frame* abajo 2/3).
- Historial de interacción y sugerencias dentro de la consola.

### Caché estático (I1)
- Pipeline que recorre rutas públicas y protegidas (según permisos) y genera:
  - `pages/{routeId}.html` (render SSR/estático).
  - `fragments/{routeId}#{anchor}.html` (bloques semánticos).
  - `index.json` con metadatos (título, alias, etiquetas, embeddings opcionales).
  - Índice de búsqueda BM25 en JSON; vectorial opcional más adelante.
- API: `GET /navia/cache/page?routeId=…`, `GET /navia/cache/search?q=…`.
- Administración: `POST /navia/cache/rebuild` (total o `?routeId=…`) que empuja el caché a KV/almacenamiento (Redis/S3/PG).

### Intérprete de intención (I2)
- Reglas iniciales rápidas y confiables:
  - `open_event("Test event")` → *match* por título/slug/sinónimos en el índice.
  - `open_page("Agenda")` → *match* por alias/etiquetas/grafo de navegación.
  - `search("…")` → lista de candidatos con puntuación.
- Acción: publicar un evento UI hacia `/navia/frame` para cambiar la URL renderizada o inyectar HTML desde el caché sin clics.

### Integración ElevenLabs (I4)
- STT/TTS para entrada y respuesta por voz (Web Mic API + *streaming* a ElevenLabs).
- Usar modelos de baja latencia (“Flash/real-time”) para experiencia fluida.
- Agents Platform de ElevenLabs como hub conversacional, conectando LLM personalizado si se requiere razonamiento/RAG.
- TTS/ASR estándar disponible si no se usa Agents.

### RAG ligero (I5)
- *Retriever* sobre el caché (BM25 + reglas; embeddings locales opcionales).
- Modos de respuesta:
  - **Página:** llevar el *frame* a la ruta completa.
  - **Fragmento:** inyectar el bloque HTML exacto con resaltado.

### Sincronía Admin→Caché (I6)
- Webhook/cola desde el panel admin (crear/editar) → tarea `cache-rebuild` selectiva.
- Invalidación por `routeId` o entidad (evento X impacta `event/X`, `agenda/X`).

### Telemetría (I7)
- Medir intent→acción, tiempo a destino, reintentos, fallbacks a UI tradicional, uso de voz vs texto.
- Privacidad: logs sin PII; IDs *hash* de rutas.

### Estructura sugerida del repo
```
homedir/
└─ modules/
   └─ aexperience/
      └─ navia/
         ├─ ui/          # consola + frame + layout + toggle
         ├─ cache/       # builder + índice + APIs
         ├─ intents/     # parser + resolutores (open_page, search, open_event)
         ├─ elevenlabs/  # stt/tts + agents client + config
         ├─ telemetry/   # hooks de observabilidad
         ├─ README.md
         └─ manifest.yaml
```

---

## 🧪 Casos de uso del MVP

1. **Buscar evento**  
   “Navia, estoy buscando el evento ‘Test event’”.  
   - Si existe: el *frame* navega a `/events/test-event` desde el caché; la consola confirma y sugiere accesos (Agenda, Oradores).  
   - Si no existe: “No encontré ese evento. ¿Te refieres a…?” (lista de 5). Al elegir, el *frame* se actualiza.

2. **Abrir sección**  
   “Llévame a la Agenda” → el *frame* carga `event/{id}#agenda` (fragmento).

3. **Consulta focalizada**  
   “¿Cuándo es la keynote?” → Navia devuelve el fragmento del caché y lo muestra resaltado en el *frame*.

---

## ⚠️ Riesgos y mitigaciones

| Riesgo | Mitigación |
|--------|------------|
| Latencia de IA (voz) | Usar modelos “Flash/RT” y *streaming* continuo con ElevenLabs. |
| Cobertura del caché | Cron de reconstrucción + rebuild on-change; fallback a *fetch* en vivo si no hay caché. |
| ACL/roles | Resolver visibilidad de rutas en el build (segmentar caché por rol). |
| Acoplamiento involuntario | Mantener APIs/eventos contractuales; sin *patches* directos al núcleo. |
| Mantenimiento | Excluir Navia del build principal (`--exclude navia`); módulos y estilos encapsulados. |

---

## 📊 Checklist de promoción / rollback (I8)

**Promover a beta estable si:**
- ≥70% de intents resueltos sin error.
- ≥30% de reducción en tiempo a destino vs UI tradicional en 5 tareas de referencia.
- NPS de la experiencia experimental ≥ NPS base.

**Rollback:**
- Apagar el *feature flag* → `/navia` desaparece y no quedan componentes residuales en *runtime*.

---

## 🔮 Visión

Navia representa el salto de la navegación manual al descubrimiento inteligente. Una vez estabilizado el Pilar 1, la plataforma quedará lista para profundizar en personalización, colaboración y experiencias multimodales extendidas sobre la misma base.

> **Del clic al diálogo.**  
> **De la navegación al descubrimiento.**  
> **De la interfaz estática a la experiencia viva.**

---

**Navia** – *La experiencia aumentada de Homedir donde el usuario no busca el valor: lo encuentra.*
