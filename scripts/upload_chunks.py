import glob
import json
import os
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

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


def _knowledge_base_documents(client: ElevenLabs):
    knowledge_base = getattr(client.conversational_ai, "knowledge_base", None)
    documents = getattr(knowledge_base, "documents", None)
    if documents is None:
        raise SystemExit(
            "El SDK de ElevenLabs no expone la API de documentos de la base de conocimiento. "
            "Actualiza la librería o verifica los cambios del SDK."
        )
    return documents


def create_doc(client: ElevenLabs, agent_id: str, meta, text: str, index: int):
    meta = meta or {}
    name = meta.get("title_guess") or meta.get("name") or f"Navia Chunk {index}"
    documents = _knowledge_base_documents(client)
    create_method = getattr(documents, "create_from_text", None)
    if not callable(create_method):
        raise SystemExit(
            "El SDK de ElevenLabs no soporta 'create_from_text'. "
            "Verifica la versión instalada o ajusta el script al nuevo método."
        )
    try:
        response = create_method(name=name, text=text, agent_id=agent_id)
    except TypeError:
        response = create_method(name=name, text=text)
    except ApiError as exc:
        detail = _format_error(getattr(exc, "body", None))
        status = getattr(exc, "status_code", None)
        raise SystemExit(
            f"Error al subir el chunk {index}: {status or 'desconocido'} → {detail or exc}"
        ) from exc
    return response


def compute_rag(client: ElevenLabs, doc_id: str, *, agent_id: str):
    documents = _knowledge_base_documents(client)
    compute_method = getattr(documents, "compute_rag_index", None)
    if not callable(compute_method):  # pragma: no cover - depende del SDK
        return None
    try:
        return compute_method(
            documentation_id=doc_id,
            agent_id=agent_id,
            model="multilingual_e5_large_instruct",
        )
    except TypeError:
        return compute_method(
            documentation_id=doc_id,
            model="multilingual_e5_large_instruct",
        )
    except ApiError as exc:
        detail = _format_error(getattr(exc, "body", None))
        status = getattr(exc, "status_code", None)
        raise SystemExit(
            f"Error al construir el índice RAG para {doc_id}: {status or 'desconocido'} → {detail or exc}"
        ) from exc


def _http_request(url: str, *, method: str, payload):
    data = json.dumps(payload).encode("utf-8")
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "xi-api-key": KEY,
    }
    request = Request(url, data=data, headers=headers, method=method)
    with urlopen(request) as response:
        body = response.read().decode("utf-8", "replace")
        if not body:
            return None
        try:
            return json.loads(body)
        except json.JSONDecodeError:
            return body


def _patch_agent_documents(agent_id: str, doc_ids):
    base_url = (API or "https://api.elevenlabs.io").rstrip("/")
    doc_ids = list(doc_ids)
    attempts = [
        ("PATCH", f"{base_url}/v1/conversational-ai/agents/{agent_id}", {"knowledge_base": doc_ids}),
        ("PATCH", f"{base_url}/v1/convai/agents/{agent_id}", {"knowledge_base": doc_ids}),
        (
            "POST",
            f"{base_url}/v1/convai/agents/{agent_id}/knowledge-base",
            {"document_ids": doc_ids},
        ),
        (
            "PATCH",
            f"{base_url}/v1/convai/agents/{agent_id}/knowledge-base",
            {"document_ids": doc_ids},
        ),
    ]
    last_error = None
    for method, url, payload in attempts:
        try:
            return _http_request(url, method=method, payload=payload)
        except HTTPError as exc:  # pragma: no cover - depende de la API externa
            detail = exc.read().decode("utf-8", "replace")
            last_error = (url, exc.code, detail or exc.reason)
            if exc.code not in {404, 405}:
                raise SystemExit(
                    f"Error HTTP al asignar documentos al agente {agent_id}: {exc.code} → {detail or exc.reason}"
                ) from exc
        except URLError as exc:  # pragma: no cover - depende de la red del usuario
            raise SystemExit(
                f"No se pudo conectar a {url}: {exc.reason if hasattr(exc, 'reason') else exc}"
            ) from exc
    if last_error is not None:
        url, code, detail = last_error
        raise SystemExit(
            "No se pudo asignar la base de conocimiento al agente después de probar"
            f" múltiples endpoints. Último intento {url} → {code}: {detail}"
        )
    return None


def assign_documents(client: ElevenLabs, agent_id: str, doc_ids):
    if not doc_ids:
        return None
    try:
        agents_api = getattr(getattr(client.conversational_ai, "agents", None), "update", None)
    except ImportError:  # pragma: no cover - depende del SDK
        agents_api = None
    if callable(agents_api):
        try:
            return agents_api(agent_id=agent_id, knowledge_base=list(doc_ids))
        except ApiError as exc:
            detail = _format_error(getattr(exc, "body", None))
            status = getattr(exc, "status_code", None)
            raise SystemExit(
                f"Error al asignar documentos al agente {agent_id}: {status or 'desconocido'} → {detail or exc}"
            ) from exc
        except ImportError:  # pragma: no cover - depende del SDK
            pass
    # Fallback a petición HTTP directa si el SDK falla o no expone el método
    return _patch_agent_documents(agent_id, doc_ids)


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
        if not doc_id:
            continue
        metadata = dict(data.get("meta", {}))
        metadata.setdefault("doc_id", doc_id)
        metadata.setdefault(
            "name",
            metadata.get("title_guess")
            or metadata.get("name")
            or f"Navia Chunk {index}",
        )
        if "source" in metadata and "source_url" not in metadata:
            metadata["source_url"] = metadata["source"]
        doc_map[doc_id] = metadata
        compute_rag(client, doc_id, agent_id=AGENT)
        uploaded_ids.append(doc_id)
        if index % 25 == 0:
            time.sleep(1)

    DOC_MAP_PATH.write_text(json.dumps(doc_map, ensure_ascii=False, indent=2))

    if uploaded_ids:
        assign_documents(client, AGENT, uploaded_ids)
        print(f"Subidos {len(uploaded_ids)} chunks al agente {AGENT}")
    else:
        print("No se subieron documentos nuevos. Revisa los chunks generados.")


if __name__ == "__main__":
    main()
