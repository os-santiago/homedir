import glob
import json
import os
import time
from pathlib import Path

try:
    from elevenlabs.client import ElevenLabs
    from elevenlabs.core.api_error import ApiError
except ModuleNotFoundError as exc:  # pragma: no cover - dependency error path
    raise SystemExit(
        "No se encontró la dependencia 'elevenlabs'. "
        "Instálala con 'pip install elevenlabs' y vuelve a intentar."
    ) from exc

API = os.environ.get("ELEVENLABS_BASE_URL", "https://api.elevenlabs.io")
KEY = os.environ.get("ELEVENLABS_API_KEY")
if not KEY:
    raise SystemExit(
        "No se encontró la variable de entorno ELEVENLABS_API_KEY. "
        "Configúrala con tu API key de ElevenLabs antes de ejecutar este script."
    )
AGENT_PATH = Path("./.navia/agent.json")
DOC_MAP_PATH = Path("./.navia/documents.json")

if not AGENT_PATH.exists():
    raise SystemExit("No se encontró ./.navia/agent.json. Ejecuta scripts/create_agent.sh primero.")

try:
    agent_payload = json.loads(AGENT_PATH.read_text())
except json.JSONDecodeError as exc:
    raise SystemExit("No se pudo leer ./.navia/agent.json. Vuelve a crear el agente.") from exc

AGENT = agent_payload.get("id") or agent_payload.get("agent_id")
if not AGENT:
    raise SystemExit("El archivo ./.navia/agent.json no contiene un agent_id válido.")


def _make_client() -> ElevenLabs:
    client_kwargs = {"api_key": KEY}
    if API and API != "https://api.elevenlabs.io":
        client_kwargs["base_url"] = API
    return ElevenLabs(**client_kwargs)


def _format_error(detail) -> str:
    if detail is None:
        return ""
    if isinstance(detail, str):
        return detail
    try:
        return json.dumps(detail, ensure_ascii=False)
    except (TypeError, ValueError):  # pragma: no cover - defensive
        return str(detail)


def create_doc(client: ElevenLabs, agent_id: str, meta, text: str, index: int):
    meta = meta or {}
    name = meta.get("title_guess", f"Navia Chunk {index}")
    filename = f"chunk-{index}.txt"
    try:
        response = client.conversational_ai.add_to_knowledge_base(
            agent_id=agent_id,
            name=name,
            file=(filename, text.encode("utf-8"), "text/plain"),
        )
    except ApiError as exc:
        detail = _format_error(getattr(exc, "body", None))
        status = getattr(exc, "status_code", None)
        raise SystemExit(
            f"Error al subir el chunk {index}: {status or 'desconocido'} → {detail or exc}"
        ) from exc
    return response


def compute_rag(client: ElevenLabs, doc_id: str):
    try:
        return client.conversational_ai.knowledge_base.document.compute_rag_index(
            documentation_id=doc_id,
            model="multilingual_e5_large_instruct",
        )
    except ApiError as exc:
        detail = _format_error(getattr(exc, "body", None))
        status = getattr(exc, "status_code", None)
        raise SystemExit(
            f"Error al construir el índice RAG para {doc_id}: {status or 'desconocido'} → {detail or exc}"
        ) from exc


def main() -> None:
    files = glob.glob("./.navia/chunks/*.json")
    if not files:
        raise SystemExit("No se encontraron chunks en ./.navia/chunks. Ejecuta scripts/normalize_and_chunk.py primero.")

    client = _make_client()

    if DOC_MAP_PATH.exists():
        try:
            doc_map = json.loads(DOC_MAP_PATH.read_text())
        except json.JSONDecodeError:
            doc_map = {}
    else:
        doc_map = {}

    uploaded_ids = []

    for index, file_path in enumerate(files, start=1):
        data = json.loads(Path(file_path).read_text())
        response = create_doc(client, AGENT, data.get("meta"), data["text"], index)
        doc_id = getattr(response, "id", None)
        if doc_id is None and isinstance(response, dict):  # pragma: no cover - backwards compatibility
            doc_id = response.get("id") or response.get("document_id")
        if doc_id:
            metadata = dict(data.get("meta", {}))
            metadata.setdefault("doc_id", doc_id)
            if "source" in metadata and "source_url" not in metadata:
                metadata["source_url"] = metadata["source"]
            doc_map[doc_id] = metadata
            compute_rag(client, doc_id)
            uploaded_ids.append(doc_id)
        if index % 25 == 0:
            time.sleep(1)

    DOC_MAP_PATH.write_text(json.dumps(doc_map, ensure_ascii=False, indent=2))
    if uploaded_ids:
        print(f"Subidos {len(uploaded_ids)} chunks al agente {AGENT}")
    else:
        print("No se subieron documentos nuevos. Revisa los chunks generados.")


if __name__ == "__main__":
    main()