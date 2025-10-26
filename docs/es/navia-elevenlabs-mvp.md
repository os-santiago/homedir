# Navia × ElevenLabs — MVP local

Este documento describe cómo reproducir el MVP que valida la integración entre el cache de Navia y los Agents de ElevenLabs utilizando RAG para respuestas con trazabilidad.

## 1. Preparación

1. Levanta EventFlow (o el sitio a indexar) en `http://localhost:8080`.
2. Copia `.env.example` a `.env` y completa `ELEVENLABS_API_KEY`.
3. Crea y activa un entorno virtual de Python, luego instala dependencias mínimas:

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install beautifulsoup4 requests elevenlabs
```

> Nota: todos los comandos se asumen desde la raíz del repositorio.

## 2. Cache del sitio

```bash
mkdir -p ./.navia/site-cache/raw
wget \
  --recursive --page-requisites --html-extension \
  --convert-links --no-parent --adjust-extension \
  --directory-prefix=./.navia/site-cache/raw \
  http://localhost:8080/
```

El resultado es una copia navegable en `./.navia/site-cache/raw`.

## 3. Normalización y chunking

Convierte los HTML cacheados a texto, conserva metadatos relevantes y crea chunks reutilizables por RAG.

```bash
python scripts/normalize_and_chunk.py
```

El script genera archivos JSON en `./.navia/chunks/` con el texto y metadatos necesarios (`source_url`, `doc_id`, etc.).

## 4. Crear el agente de ElevenLabs

```bash
ELEVENLABS_API_KEY=... scripts/create_agent.sh
```

El comando crea un agente en modo texto con RAG habilitado, instruido para buscar el contenido solicitado dentro de los chunks recuperados, corroborar que coincide y responder únicamente con evidencias recuperadas del índice señalando la URL (`source_url`) correspondiente. El resultado se guarda en `./.navia/agent.json`.

## 5. Subir chunks y construir el índice RAG

```bash
python scripts/upload_chunks.py
```

Cada chunk se carga como documento en la base de conocimiento del agente y se indexa inmediatamente para RAG.

### Alternativa: subir URLs directamente

Si tienes un archivo `urls.txt` generado con `scripts/crawl_urls.py`, puedes cargar cada página sin pasar por el pipeline de _chunking_:

```bash
python scripts/upload_urls.py urls.txt
```

El script descarga cada URL, compone un documento que incluye tanto el texto extraído como la dirección original y lo sube al agente.

## 6. Consultar al agente

```bash
python scripts/ask_agent.py
```

El flujo crea una conversación, envía la pregunta y muestra tanto la respuesta como las URLs de origen de los documentos consultados. Si la API no devuelve referencias directas, el script realiza un mapeo `doc_id → metadata` para preservar la trazabilidad.

## 7. Renderizar HTML confiable

Para presentar contenido sin alucinaciones, renderiza el HTML directamente desde el cache local:

```bash
python scripts/render_from_cache.py http://localhost:8080/ruta/del/recurso
```

Opcionalmente añade un `anchor` como segundo argumento para devolver solo un fragmento.

## 8. Métricas de validación

- % de consultas con al menos una fuente válida.
- Latencia p50 de las consultas (RAG agrega ~500 ms).
- Exactitud de la URL/fragmento devuelto en un set de pruebas canónicas.

Con estos pasos dispones de un MVP funcional que demuestra la cadena completa: cache local → normalización → ingestión ElevenLabs → consulta con RAG → renderizado confiable desde el cache.
