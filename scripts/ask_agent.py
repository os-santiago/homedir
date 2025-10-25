"""Herramienta interactiva para consultar Navia mediante ElevenLabs.

Esta versión utiliza el SDK oficial de ElevenLabs (`elevenlabs.client` y
`elevenlabs.conversational_ai.conversation`) para mantener una conversación en
modo texto con el agente configurado durante el proceso de ingestión del MVP.

Se conservan las funcionalidades necesarias para validar el flujo de extremo a
extremo del MVP:

* Escenarios guiados para verificar cobertura (`--scenario`).
* Preguntas puntuales reutilizando el contexto de conversación (`--question`).
* Modo interactivo para continuar explorando hasta encontrar el contenido
  deseado.
* Resolución de metadatos de los documentos referenciados para recuperar el
  material original desde el caché local.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import sys
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

from elevenlabs.client import ElevenLabs
from elevenlabs.conversational_ai.conversation import (
    AudioInterface,
    Conversation,
    ConversationInitiationData,
)

AGENT_PATH = Path("./.navia/agent.json")
DOC_MAP_PATH = Path("./.navia/documents.json")
DEFAULT_TIMEOUT = 30.0


class TextOnlyAudioInterface(AudioInterface):
    """Implementación mínima de ``AudioInterface`` para modo texto."""

    def start(self, input_callback):  # type: ignore[override]
        self._input_callback = input_callback  # Guardamos la referencia por compatibilidad.

    def stop(self):  # type: ignore[override]
        pass

    def output(self, audio: bytes):  # type: ignore[override]
        # En modo texto no se reproduce audio, pero el SDK enviará llamadas.
        pass

    def interrupt(self):  # type: ignore[override]
        pass


class ResponseCollector:
    """Coordina la entrega de respuestas del agente desde callbacks."""

    def __init__(self, loop: asyncio.AbstractEventLoop) -> None:
        self._loop = loop
        self._pending: Optional[asyncio.Future[str]] = None
        self.last_agent_response: Optional[str] = None

    def prepare(self) -> asyncio.Future[str]:
        future: asyncio.Future[str] = self._loop.create_future()
        self._pending = future
        self.last_agent_response = None
        return future

    def handle_agent_response(self, response: str) -> None:
        self.last_agent_response = response
        if self._pending and not self._pending.done():
            self._loop.call_soon_threadsafe(self._pending.set_result, response)


def _threadsafe_printer(loop: asyncio.AbstractEventLoop, prefix: str):
    def _print(message: str) -> None:
        loop.call_soon_threadsafe(print, f"{prefix}{message}")

    return _print


def load_agent_id() -> str:
    agent_id = os.getenv("AGENT_ID")
    if agent_id:
        return agent_id
    if not AGENT_PATH.exists():
        raise SystemExit(
            "No se encontró ./.navia/agent.json ni la variable de entorno AGENT_ID. "
            "Ejecuta scripts/create_agent.sh para crear el agente y vuelve a intentar."
        )
    try:
        agent_payload = json.loads(AGENT_PATH.read_text())
    except json.JSONDecodeError as exc:
        raise SystemExit(
            "No se pudo leer ./.navia/agent.json. Vuelve a crear el agente."  # noqa: TRY003
        ) from exc
    agent_id = agent_payload.get("id") or agent_payload.get("agent_id")
    if not agent_id:
        raise SystemExit("El archivo ./.navia/agent.json no contiene un agent_id válido.")
    return agent_id


def load_local_doc_map() -> Dict[str, Dict]:
    if not DOC_MAP_PATH.exists():
        return {}
    try:
        data = json.loads(DOC_MAP_PATH.read_text())
    except json.JSONDecodeError:
        return {}
    return data if isinstance(data, dict) else {}


def convert_model_to_dict(model: object) -> Dict:
    if model is None:
        return {}
    if isinstance(model, dict):
        return model
    if hasattr(model, "model_dump"):
        return getattr(model, "model_dump")(exclude_none=True)
    if hasattr(model, "dict"):
        return getattr(model, "dict")(exclude_none=True)  # type: ignore[no-any-return]
    return {}


async def resolve_doc_metadata(
    client: ElevenLabs,
    agent_id: str,
    doc_ids: Iterable[str],
) -> Dict[str, Dict]:
    mapping: Dict[str, Dict] = {}
    local_map = load_local_doc_map()

    for doc_id in doc_ids:
        if not doc_id or doc_id in mapping:
            continue
        metadata = local_map.get(doc_id)
        if not metadata:
            try:
                document = await asyncio.to_thread(
                    client.conversational_ai.knowledge_base.documents.get,
                    doc_id,
                    agent_id=agent_id,
                )
            except Exception:
                metadata = {}
            else:
                metadata = convert_model_to_dict(getattr(document, "metadata", {}))
                metadata.setdefault("name", getattr(document, "name", None))
                metadata.setdefault("source_url", getattr(document, "url", None))
                metadata.setdefault("doc_id", getattr(document, "id", None))
                metadata.setdefault("extracted_inner_html", getattr(document, "extracted_inner_html", None))
        if metadata:
            mapping[doc_id] = metadata
    return mapping


def build_navigation_hint(metadata: Dict, *, fallback: str) -> str:
    if not isinstance(metadata, dict):
        return fallback

    url = metadata.get("source_url") or metadata.get("source")
    anchor = metadata.get("anchor") or metadata.get("anchor_id") or metadata.get("fragment")
    base_url = url.split("#", 1)[0] if isinstance(url, str) else None
    formatted_anchor: Optional[str] = None
    if base_url and anchor not in (None, ""):
        formatted_anchor = str(anchor)
        if formatted_anchor.startswith("#"):
            formatted_anchor = formatted_anchor[1:]
        url = f"{base_url}#{formatted_anchor}"
    else:
        formatted_anchor = None

    path = metadata.get("source_path") or metadata.get("path")
    chunk_index = metadata.get("chunk_index")
    chunk_id = metadata.get("chunk_id")
    title = metadata.get("title") or metadata.get("title_guess") or metadata.get("name")

    parts: List[str] = []
    if title:
        parts.append(str(title))
    if url:
        parts.append(f"Visita {url}")
    if path:
        parts.append(f"Cache local: {path}")
    if chunk_index not in (None, ""):
        parts.append(f"Chunk #{chunk_index}")
    if chunk_id not in (None, ""):
        parts.append(f"Chunk id: {chunk_id}")
    if not parts:
        return fallback

    if base_url:
        command = f"Render desde cache: python scripts/render_from_cache.py {base_url}"
        if formatted_anchor:
            command = f"{command} {formatted_anchor}"
        parts.append(command)

    return " — ".join(parts)


async def wait_for_conversation_id(conversation: Conversation, timeout: float = 15.0) -> str:
    elapsed = 0.0
    interval = 0.1
    while elapsed < timeout:
        conv_id = getattr(conversation, "_conversation_id", None)
        if isinstance(conv_id, str) and conv_id:
            return conv_id
        await asyncio.sleep(interval)
        elapsed += interval
    raise TimeoutError("No se pudo obtener el identificador de la conversación en el tiempo esperado.")


async def fetch_conversation_state(
    client: ElevenLabs,
    conversation_id: str,
    attempts: int = 10,
    delay: float = 1.0,
):
    last = None
    for _ in range(attempts):
        try:
            data = await asyncio.to_thread(
                client.conversational_ai.conversations.get,
                conversation_id=conversation_id,
            )
        except Exception:
            data = None
        if data is not None:
            last = data
            transcript = list(getattr(data, "transcript", []) or [])
            if transcript and str(transcript[-1].role) == "agent":
                return data
        await asyncio.sleep(delay)
    return last


def extract_doc_references(transcript: Sequence) -> List[Tuple[str, Optional[str]]]:
    references: List[Tuple[str, Optional[str]]] = []
    for entry in transcript:
        rag_info = getattr(entry, "rag_retrieval_info", None)
        if rag_info:
            chunks = getattr(rag_info, "chunks", []) or []
            for chunk in chunks:
                doc_id = getattr(chunk, "document_id", None)
                chunk_id = getattr(chunk, "chunk_id", None)
                if doc_id:
                    references.append((doc_id, chunk_id))
    return references


def latest_agent_message(transcript: Sequence) -> Optional[str]:
    for entry in reversed(transcript):
        if str(getattr(entry, "role", "")) == "agent":
            message = getattr(entry, "message", None)
            if isinstance(message, str) and message.strip():
                return message
    return None


async def display_references(
    client: ElevenLabs,
    agent_id: str,
    transcript: Sequence,
    doc_refs: Sequence[Tuple[str, Optional[str]]],
) -> None:
    doc_ids = [doc_id for doc_id, _ in doc_refs if doc_id]
    if not doc_ids:
        print("\n(Sin referencias disponibles. Revisa la configuración del agente RAG.)")
        return

    metadata_map = await resolve_doc_metadata(client, agent_id, doc_ids)
    if metadata_map:
        print("\nRutas sugeridas:")
        seen: set[str] = set()
        for doc_id, chunk_id in doc_refs:
            if not doc_id or doc_id in seen:
                continue
            seen.add(doc_id)
            metadata = dict(metadata_map.get(doc_id, {}))
            if chunk_id and "chunk_id" not in metadata:
                metadata["chunk_id"] = chunk_id
            hint = build_navigation_hint(metadata, fallback=f"{doc_id}{f' (chunk {chunk_id})' if chunk_id else ''}")
            print("-", hint)
    else:
        print("\nRutas sugeridas:")
        for doc_id, chunk_id in doc_refs:
            label = f"{doc_id}{f' (chunk {chunk_id})' if chunk_id else ''}"
            print("-", label)


async def ask_and_display(
    conversation: Conversation,
    collector: ResponseCollector,
    client: ElevenLabs,
    agent_id: str,
    conversation_id: str,
    question: str,
    *,
    timeout: float = DEFAULT_TIMEOUT,
) -> None:
    print(f"\n📝 Pregunta: {question}")
    response_future = collector.prepare()
    try:
        conversation.send_user_message(question)
    except RuntimeError as exc:
        raise SystemExit(f"No se pudo enviar la pregunta: {exc}") from exc

    try:
        await asyncio.wait_for(response_future, timeout=timeout)
    except asyncio.TimeoutError:
        print("No se recibió una respuesta de texto en el tiempo esperado.")

    conversation_state = await fetch_conversation_state(client, conversation_id)
    transcript = list(getattr(conversation_state, "transcript", []) or []) if conversation_state else []
    answer = latest_agent_message(transcript) or collector.last_agent_response or "(sin respuesta)"

    print("\n=== RESPUESTA DEL AGENTE ===\n", answer)

    doc_refs = extract_doc_references(transcript)
    await display_references(client, agent_id, transcript, doc_refs)


async def run_interactive(
    conversation: Conversation,
    collector: ResponseCollector,
    client: ElevenLabs,
    agent_id: str,
    conversation_id: str,
    *,
    timeout: float,
) -> None:
    print("\n💬 Modo interactivo. Escribe 'salir' para terminar la conversación.")
    while True:
        try:
            question = await asyncio.to_thread(input, "\nTu pregunta: ")
        except (EOFError, KeyboardInterrupt):
            print("\nInteracción cancelada.")
            return
        if question.strip().lower() in {"", "salir", "exit", "quit"}:
            print("\nSesión finalizada por la persona usuaria.")
            return
        await ask_and_display(
            conversation,
            collector,
            client,
            agent_id,
            conversation_id,
            question,
            timeout=timeout,
        )


async def run_scenario(
    conversation: Conversation,
    collector: ResponseCollector,
    client: ElevenLabs,
    agent_id: str,
    conversation_id: str,
    questions: Sequence[str],
    *,
    timeout: float,
) -> None:
    for idx, question in enumerate(questions, start=1):
        await ask_and_display(
            conversation,
            collector,
            client,
            agent_id,
            conversation_id,
            question,
            timeout=timeout,
        )


DEFAULT_SCENARIO = [
    "¿Qué información tienes disponible en tu base de conocimiento?",
    "¿Puedes contarme sobre los productos que tienes registrados?",
    "¿Cuáles son las políticas principales que manejas?",
    "¿Hay alguna documentación técnica disponible?",
]


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Consulta Navia utilizando el agente ElevenLabs con contexto persistente",
    )
    parser.add_argument(
        "--question",
        help="Realiza una única pregunta y muestra la respuesta",
    )
    parser.add_argument(
        "--scenario",
        action="store_true",
        help="Ejecuta las preguntas de verificación predefinidas",
    )
    parser.add_argument(
        "--no-interactive",
        action="store_true",
        help="Evita entrar en modo interactivo tras el escenario o pregunta única",
    )
    parser.add_argument(
        "--user-id",
        help="Identificador de usuario para registrar la sesión en ElevenLabs",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=DEFAULT_TIMEOUT,
        help="Tiempo máximo de espera (segundos) para recibir cada respuesta",
    )
    return parser.parse_args(argv)


async def main_async(args: argparse.Namespace) -> None:
    agent_id = load_agent_id()
    api_key = os.getenv("ELEVENLABS_API_KEY")

    client = ElevenLabs(api_key=api_key)
    loop = asyncio.get_running_loop()
    collector = ResponseCollector(loop)

    conversation = Conversation(
        client,
        agent_id,
        requires_auth=bool(api_key),
        audio_interface=TextOnlyAudioInterface(),
        config=ConversationInitiationData(
            conversation_config_override={"textOnly": True},
            user_id=args.user_id,
        ),
        callback_agent_response=collector.handle_agent_response,
        callback_user_transcript=_threadsafe_printer(loop, "👤 Usuario: "),
        callback_latency_measurement=_threadsafe_printer(loop, "⏱️ Latencia: "),
    )

    conversation.start_session()
    try:
        conversation_id = await wait_for_conversation_id(conversation)
    except Exception:
        conversation.end_session()
        raise

    print(f"Conversación creada con id: {conversation_id}")

    try:
        if args.question:
            await ask_and_display(
                conversation,
                collector,
                client,
                agent_id,
                conversation_id,
                args.question,
                timeout=args.timeout,
            )

        if args.scenario:
            await run_scenario(
                conversation,
                collector,
                client,
                agent_id,
                conversation_id,
                DEFAULT_SCENARIO,
                timeout=args.timeout,
            )

        should_enter_interactive = (
            not args.no_interactive
            and (args.scenario or not args.question)
        )
        if should_enter_interactive:
            await run_interactive(
                conversation,
                collector,
                client,
                agent_id,
                conversation_id,
                timeout=args.timeout,
            )
    finally:
        conversation.end_session()
        try:
            ended_id = await asyncio.to_thread(conversation.wait_for_session_end)
        except Exception:
            ended_id = conversation_id
        if ended_id:
            print(f"ID de conversación: {ended_id}")


def main(argv: Sequence[str] | None = None) -> None:
    args = parse_args(argv or sys.argv[1:])
    try:
        asyncio.run(main_async(args))
    except KeyboardInterrupt:
        print("\nSesión interrumpida por la persona usuaria.")


if __name__ == "__main__":
    main()
