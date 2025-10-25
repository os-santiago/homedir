import glob
import json
import os
import time
from pathlib import Path
from typing import List, Sequence

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


def _resolve_create_from_text(client: ElevenLabs):
    kb_api = getattr(client.conversational_ai, "knowledge_base", None)
    if kb_api is None:
        raise SystemExit(
            "La versión del SDK de ElevenLabs instalada no expone la API de knowledge base. "
            "Actualiza la dependencia 'elevenlabs' e inténtalo de nuevo."
        )

    create_fn = getattr(kb_api, "create_from_text", None)
    if create_fn is not None:
        return create_fn

    documents_api = getattr(kb_api, "documents", None)
    if documents_api is not None:
        create_fn = getattr(documents_api, "create_from_text", None)
        if create_fn is not None:
            return create_fn

    raise SystemExit(
        "La versión del SDK de ElevenLabs instalada no permite crear documentos desde texto. "
        "Actualiza la dependencia 'elevenlabs' e inténtalo de nuevo."
    )


def create_doc(client: ElevenLabs, meta, text: str, index: int):
    meta = meta or {}
    name = meta.get("title_guess", f"Navia Chunk {index}")
    create_from_text = _resolve_create_from_text(client)
    try:
        response = create_from_text(
            name=name,
            text=text,
        )
    except ApiError as exc:
        detail = _format_error(getattr(exc, "body", None))
        status = getattr(exc, "status_code", None)
        raise SystemExit(
            f"Error al subir el chunk {index}: {status or 'desconocido'} → {detail or exc}"
        ) from exc
    return response


def assign_documents_to_agent(
    client: ElevenLabs, agent_id: str, document_ids: Sequence[str]
) -> int:
    agents_api = getattr(client.conversational_ai, "agents", None)
    if agents_api is None or not hasattr(agents_api, "update"):
        raise SystemExit(
            "La versión del SDK de ElevenLabs instalada no permite asignar documentos al agente. "
            "Actualiza la dependencia 'elevenlabs' e inténtalo de nuevo."
        )

    unique_ids = []
    seen = set()
    for doc_id in document_ids:
        if not doc_id or doc_id in seen:
            continue
        unique_ids.append(doc_id)
        seen.add(doc_id)

    if not unique_ids:
        return 0

    try:
        agents_api.update(agent_id=agent_id, knowledge_base=unique_ids)
    except ApiError as exc:
        detail = _format_error(getattr(exc, "body", None))
        status = getattr(exc, "status_code", None)
        raise SystemExit(
            f"Error al asignar documentos al agente {agent_id}: "
            f"{status or 'desconocido'} → {detail or exc}"
        ) from exc

    return len(unique_ids)


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

    uploaded_ids: List[str] = []

    for index, file_path in enumerate(files, start=1):
        data = json.loads(Path(file_path).read_text())
        meta = data.get("meta") or {}
        name_guess = meta.get("title_guess", f"Navia Chunk {index}")
        response = create_doc(client, meta, data["text"], index)
        doc_id = getattr(response, "id", None)
        if doc_id is None and isinstance(response, dict):  # pragma: no cover - backwards compatibility
            doc_id = response.get("id") or response.get("document_id")
        if doc_id:
            metadata = dict(meta)
            metadata.setdefault("doc_id", doc_id)
            metadata.setdefault("name", getattr(response, "name", None) or name_guess)
            if "source" in metadata and "source_url" not in metadata:
                metadata["source_url"] = metadata["source"]
            doc_map[doc_id] = metadata
            uploaded_ids.append(doc_id)
        if index % 25 == 0:
            time.sleep(1)

    DOC_MAP_PATH.write_text(json.dumps(doc_map, ensure_ascii=False, indent=2))
    if uploaded_ids:
        print(f"Subidos {len(uploaded_ids)} chunks al agente {AGENT}")
        assigned = assign_documents_to_agent(client, AGENT, doc_map.keys())
        if assigned:
            print(
                f"Asignados {assigned} documentos al agente {AGENT}"
                f" (incluidos {len(uploaded_ids)} nuevos)"
            )
    else:
        print("No se subieron documentos nuevos. Revisa los chunks generados.")


if __name__ == "__main__":
    main()
