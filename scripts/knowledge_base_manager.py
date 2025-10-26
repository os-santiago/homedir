"""Utility script for managing the ElevenLabs conversational AI knowledge base."""
from __future__ import annotations

import copy
import json
import os
from collections.abc import Iterable
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from elevenlabs.client import ElevenLabs

DEFAULT_BASE_URL = os.getenv("ELEVENLABS_BASE_URL", "https://api.elevenlabs.io")
AGENT_PATH = Path("./.navia/agent.json")
DOC_MAP_PATH = Path("./.navia/documents.json")


class KnowledgeBaseManager:
    """Cliente para gestionar la base de conocimiento y el prompt del agente."""

    def __init__(self, api_key: Optional[str] = None) -> None:
        self.api_key = api_key or os.getenv("ELEVENLABS_API_KEY")
        if not self.api_key:
            raise ValueError(
                "La API key de ElevenLabs no está configurada. Define ELEVENLABS_API_KEY"
                " en el entorno o pásala explícitamente al constructor."
            )
        self.client = ElevenLabs(api_key=self.api_key)
        self.base_url = (os.getenv("ELEVENLABS_BASE_URL") or DEFAULT_BASE_URL).rstrip("/")
        self._agent_payload_cache: Optional[Dict[str, object]] = None
        self._doc_map_cache: Optional[Dict[str, Dict[str, object]]] = None

    # ------------------------------------------------------------------
    # Utilidades de configuración del agente
    # ------------------------------------------------------------------
    def _load_agent_payload_local(self) -> Dict[str, object]:
        if self._agent_payload_cache is not None:
            return copy.deepcopy(self._agent_payload_cache)
        if not AGENT_PATH.exists():
            self._agent_payload_cache = {}
            return {}
        try:
            payload = json.loads(AGENT_PATH.read_text())
        except json.JSONDecodeError:
            payload = {}
        if isinstance(payload, dict):
            self._agent_payload_cache = payload
        else:
            self._agent_payload_cache = {}
        return copy.deepcopy(self._agent_payload_cache)

    def _save_agent_payload(self, payload: Dict[str, object]) -> None:
        data = copy.deepcopy(payload)
        self._agent_payload_cache = data
        AGENT_PATH.parent.mkdir(parents=True, exist_ok=True)
        AGENT_PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2))

    def _extract_agent_id_from_payload(self, payload: Dict[str, object]) -> Optional[str]:
        for key in ("id", "agent_id", "agentId"):
            value = payload.get(key)
            if isinstance(value, str) and value.strip():
                return value
        return None

    def _resolve_agent_id(self) -> Optional[str]:
        agent_id = os.getenv("AGENT_ID")
        if agent_id:
            return agent_id
        payload = self._load_agent_payload_local()
        return self._extract_agent_id_from_payload(payload)

    def _convert_model_to_dict(self, model: object) -> Dict[str, object]:
        if model is None:
            return {}
        if isinstance(model, dict):
            return {k: v for k, v in model.items()}
        if hasattr(model, "model_dump"):
            try:
                dumped = model.model_dump(exclude_none=True)
            except TypeError:
                dumped = model.model_dump()
            return self._convert_model_to_dict(dumped)
        if hasattr(model, "dict"):
            try:
                dumped = model.dict()
            except TypeError:
                dumped = model.dict(exclude_none=True)
            return self._convert_model_to_dict(dumped)
        if hasattr(model, "__dict__"):
            return {
                key: value
                for key, value in vars(model).items()
                if not key.startswith("__")
            }
        return {}

    def _http_request(self, method: str, path: str, payload: Optional[Dict[str, object]] = None):
        url = f"{self.base_url}{path}"
        headers = {
            "Accept": "application/json",
            "xi-api-key": self.api_key,
        }
        data: Optional[bytes] = None
        if payload is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(payload).encode("utf-8")
        request = Request(url, data=data, headers=headers, method=method)
        with urlopen(request) as response:
            body = response.read().decode("utf-8", "replace")
        if not body:
            return {}
        try:
            return json.loads(body)
        except json.JSONDecodeError:
            return {"raw": body}

    def _fetch_agent_remote(self, agent_id: str) -> Optional[Dict[str, object]]:
        try:
            agents_client = getattr(self.client.conversational_ai, "agents", None)
        except ImportError as exc:  # pragma: no cover - depends on SDK internals
            print(
                "⚠️ No se pudo importar el cliente de agentes de ElevenLabs; se usará"
                f" el fallback HTTP: {exc}"
            )
            agents_client = None
        if agents_client:
            for method_name in ("get", "retrieve", "fetch", "get_agent"):
                method = getattr(agents_client, method_name, None)
                if not callable(method):
                    continue
                method_variants: Sequence[Tuple[Sequence, Dict[str, object]]] = (
                    ((), {"agent_id": agent_id}),
                    ((agent_id,), {}),
                )
                for args, kwargs in method_variants:
                    try:
                        response = method(*args, **kwargs)
                    except TypeError:
                        continue
                    except Exception as exc:  # pragma: no cover - SDK runtime issue
                        print(f"❌ Error obteniendo la configuración del agente: {exc}")
                        return None
                    if response is not None:
                        return self._convert_model_to_dict(response)
        # Fallback HTTP
        for endpoint in (
            f"/v1/convai/agents/{agent_id}",
            f"/v1/conversational-ai/agents/{agent_id}",
        ):
            try:
                payload = self._http_request("GET", endpoint)
            except HTTPError as exc:
                if exc.code in {404, 405}:  # pragma: no cover - defensive
                    continue
                detail = exc.read().decode("utf-8", "replace")
                print(
                    "❌ Error HTTP al recuperar el agente"
                    f" {agent_id}: {exc.code} → {detail or exc.reason}"
                )
                return None
            except URLError as exc:  # pragma: no cover - network specific
                print(f"❌ No se pudo conectar a ElevenLabs: {getattr(exc, 'reason', exc)}")
                return None
            if payload:
                return self._convert_model_to_dict(payload)
        return None

    def _fetch_agent_payload(self, agent_id: str) -> Optional[Dict[str, object]]:
        payload = self._load_agent_payload_local()
        stored_id = self._extract_agent_id_from_payload(payload)
        if payload and stored_id == agent_id:
            current = payload
        else:
            current = {}

        remote_payload = self._fetch_agent_remote(agent_id)
        if remote_payload:
            current = remote_payload
            # guardamos para futuras ejecuciones
            current.setdefault("agent_id", agent_id)
            self._save_agent_payload(current)
        if not current:
            return None
        return copy.deepcopy(current)

    def _extract_prompt_text(self, payload: Dict[str, object]) -> str:
        prompt_text = ""
        conversation_config = payload.get("conversation_config") or payload.get("conversationConfig")
        agent_section: Optional[Dict[str, object]] = None
        if isinstance(conversation_config, dict):
            agent_section = conversation_config.get("agent")
        if agent_section is None and isinstance(payload.get("agent"), dict):
            agent_section = payload.get("agent")  # type: ignore[assignment]
        prompt_section: Optional[Dict[str, object]] = None
        if isinstance(agent_section, dict):
            prompt_section = agent_section.get("prompt")
            if not isinstance(prompt_section, dict):
                prompt_section = None
        if prompt_section is None and isinstance(payload.get("prompt"), dict):
            prompt_section = payload.get("prompt")  # type: ignore[assignment]
        if isinstance(prompt_section, dict):
            for key in ("prompt", "instructions", "text"):
                value = prompt_section.get(key)
                if isinstance(value, str) and value.strip():
                    prompt_text = value
                    break
        if not prompt_text and isinstance(agent_section, dict):
            for key in ("prompt", "instructions", "text"):
                value = agent_section.get(key)
                if isinstance(value, str) and value.strip():
                    prompt_text = value
                    break
        if not prompt_text:
            value = payload.get("prompt")
            if isinstance(value, str):
                prompt_text = value
        return prompt_text or ""

    def _set_prompt_text(self, payload: Dict[str, object], prompt_text: str, *, agent_id: str) -> Dict[str, object]:
        updated = copy.deepcopy(payload)
        updated.setdefault("agent_id", agent_id)
        conversation_config = updated.get("conversation_config")
        if not isinstance(conversation_config, dict):
            conversation_config = {}
            updated["conversation_config"] = conversation_config
        agent_section = conversation_config.get("agent")
        if not isinstance(agent_section, dict):
            agent_section = {}
            conversation_config["agent"] = agent_section
        prompt_section = agent_section.get("prompt")
        if not isinstance(prompt_section, dict):
            prompt_section = {}
            agent_section["prompt"] = prompt_section
        prompt_section["prompt"] = prompt_text
        return updated

    def _update_agent_prompt_remote(self, agent_id: str, new_prompt: str) -> bool:
        agents_client = getattr(self.client.conversational_ai, "agents", None)
        if agents_client:
            for method_name in ("update", "partial_update", "patch"):
                method = getattr(agents_client, method_name, None)
                if not callable(method):
                    continue
                sdk_payloads = (
                    {"agent_id": agent_id, "conversation_config": {"agent": {"prompt": {"prompt": new_prompt}}}},
                    {"agent_id": agent_id, "prompt": {"prompt": new_prompt}},
                )
                for kwargs in sdk_payloads:
                    try:
                        method(**kwargs)
                    except TypeError:
                        continue
                    except Exception as exc:  # pragma: no cover - SDK runtime issue
                        print(f"❌ Error actualizando el prompt mediante el SDK: {exc}")
                        break
                    else:
                        return True
        payload = {"conversation_config": {"agent": {"prompt": {"prompt": new_prompt}}}}
        for endpoint in (
            f"/v1/convai/agents/{agent_id}",
            f"/v1/conversational-ai/agents/{agent_id}",
        ):
            try:
                self._http_request("PATCH", endpoint, payload)
            except HTTPError as exc:
                detail = exc.read().decode("utf-8", "replace")
                if exc.code in {404, 405}:  # pragma: no cover - compatibility fallback
                    continue
                print(
                    "❌ Error HTTP al actualizar el prompt"
                    f" {agent_id}: {exc.code} → {detail or exc.reason}"
                )
                return False
            except URLError as exc:  # pragma: no cover - network dependent
                print(f"❌ No se pudo conectar a ElevenLabs para actualizar el prompt: {getattr(exc, 'reason', exc)}")
                return False
            else:
                return True
        print("❌ No se pudo actualizar el prompt del agente. Verifica el SDK o la API de ElevenLabs.")
        return False

    # ------------------------------------------------------------------
    # Manejo de documentos
    # ------------------------------------------------------------------
    def _load_doc_map(self) -> Dict[str, Dict[str, object]]:
        if self._doc_map_cache is not None:
            return copy.deepcopy(self._doc_map_cache)
        if not DOC_MAP_PATH.exists():
            self._doc_map_cache = {}
            return {}
        try:
            data = json.loads(DOC_MAP_PATH.read_text())
        except json.JSONDecodeError:
            data = {}
        if isinstance(data, dict):
            normalized: Dict[str, Dict[str, object]] = {}
            for key, value in data.items():
                if isinstance(value, dict):
                    normalized[key] = value
            self._doc_map_cache = normalized
        else:
            self._doc_map_cache = {}
        return copy.deepcopy(self._doc_map_cache)

    def _local_document_metadata(self, document_id: str) -> Dict[str, object]:
        doc_map = self._load_doc_map()
        metadata = doc_map.get(document_id, {})
        if isinstance(metadata, dict):
            return {k: v for k, v in metadata.items()}
        return {}

    def _get_document_payload(self, document_id: str, agent_id: str) -> Optional[Dict[str, object]]:
        kb_client = getattr(getattr(self.client.conversational_ai, "knowledge_base", None), "documents", None)
        if kb_client:
            for method_name in ("get", "retrieve", "get_document"):
                method = getattr(kb_client, method_name, None)
                if not callable(method):
                    continue
                call_patterns: Sequence[Tuple[Sequence, Dict[str, object]]] = (
                    ((document_id,), {"agent_id": agent_id}),
                    ((), {"document_id": document_id, "agent_id": agent_id}),
                    ((), {"knowledge_base_document_id": document_id, "agent_id": agent_id}),
                    ((document_id,), {}),
                )
                for args, kwargs in call_patterns:
                    try:
                        response = method(*args, **kwargs)
                    except TypeError:
                        continue
                    except Exception as exc:  # pragma: no cover - SDK runtime issue
                        print(f"❌ Error obteniendo el documento {document_id}: {exc}")
                        return None
                    if response is not None:
                        return self._convert_model_to_dict(response)
        print(
            "⚠️ No fue posible recuperar el documento"
            f" {document_id} mediante el SDK. Se usará la metadata local si está disponible."
        )
        return None

    def _merge_metadata(
        self,
        document_payload: Optional[Dict[str, object]],
        *,
        document_id: str,
    ) -> Dict[str, object]:
        metadata: Dict[str, object] = {}
        if document_payload:
            metadata.update(self._convert_model_to_dict(document_payload.get("metadata")))
            for key in ("name", "type", "language"):
                if key not in metadata and isinstance(document_payload.get(key), str):
                    metadata[key] = document_payload[key]  # type: ignore[index]
            if "source_url" not in metadata:
                source_url = document_payload.get("source_url") or document_payload.get("url")
                if isinstance(source_url, str):
                    metadata["source_url"] = source_url
        local_metadata = self._local_document_metadata(document_id)
        for key, value in local_metadata.items():
            metadata.setdefault(key, value)
        metadata.setdefault("doc_id", document_id)
        if "name" not in metadata:
            metadata["name"] = f"Documento {document_id}"
        return metadata

    def _extract_document_text(
        self,
        document_payload: Optional[Dict[str, object]],
        metadata: Dict[str, object],
    ) -> Optional[str]:
        candidates: List[str] = []
        if document_payload:
            for key in (
                "extracted_inner_html",
                "content",
                "text",
                "body",
                "raw_text",
                "extracted_text",
            ):
                value = document_payload.get(key)
                if isinstance(value, str) and value.strip():
                    candidates.append(value.strip())
            chunks = document_payload.get("chunks")
            if isinstance(chunks, list):
                chunk_texts: List[str] = []
                for chunk in chunks:
                    chunk_dict = self._convert_model_to_dict(chunk)
                    text_value = chunk_dict.get("text") or chunk_dict.get("content")
                    if isinstance(text_value, str) and text_value.strip():
                        chunk_texts.append(text_value.strip())
                if chunk_texts:
                    candidates.append("\n".join(chunk_texts))
        for key in (
            "content",
            "extracted_inner_html",
            "summary",
            "description",
            "text",
        ):
            value = metadata.get(key)
            if isinstance(value, str) and value.strip():
                candidates.append(value.strip())
        for candidate in candidates:
            if candidate.strip():
                return candidate.strip()
        descriptive_parts = []
        for label, field in (("Nombre", "name"), ("Fuente", "source_url"), ("ID", "doc_id")):
            value = metadata.get(field)
            if isinstance(value, str) and value.strip():
                descriptive_parts.append(f"{label}: {value.strip()}")
        if descriptive_parts:
            return "\n".join(descriptive_parts)
        return None

    def _format_document_section(self, metadata: Dict[str, object], content: str) -> str:
        title = metadata.get("name")
        if not isinstance(title, str) or not title.strip():
            title = f"Documento {metadata.get('doc_id', 'sin ID')}"
        title = title.strip()
        source = metadata.get("source_url")
        header = f"{title} (ID: {metadata.get('doc_id')})"
        if isinstance(source, str) and source.strip():
            header += f" — Fuente: {source.strip()}"
        return f"{header}\n{content.strip()}"

    def _append_documents_to_prompt(self, document_ids: Sequence[str]) -> bool:
        if not document_ids:
            print("⚠️ No se proporcionaron documentos para agregar al prompt.")
            return False
        agent_id = self._resolve_agent_id()
        if not agent_id:
            print(
                "❌ No se pudo determinar el agente configurado. Define AGENT_ID o crea el agente con scripts/create_agent.sh."
            )
            return False
        agent_payload = self._fetch_agent_payload(agent_id)
        if agent_payload is None:
            print("❌ No se pudo obtener la configuración actual del agente.")
            return False
        current_prompt = self._extract_prompt_text(agent_payload)
        prompt_lower = current_prompt.lower()
        sections: List[str] = []
        for document_id in document_ids:
            if not isinstance(document_id, str) or not document_id.strip():
                continue
            if document_id.lower() in prompt_lower:
                print(f"⚠️ El documento {document_id} ya parece estar presente en el prompt. Se omite.")
                continue
            document_payload = self._get_document_payload(document_id, agent_id)
            metadata = self._merge_metadata(document_payload, document_id=document_id)
            content = self._extract_document_text(document_payload, metadata)
            if not content:
                print(f"⚠️ No se pudo extraer contenido útil del documento {document_id}. Se omite.")
                continue
            sections.append(self._format_document_section(metadata, content))
        if not sections:
            print("⚠️ No se encontraron contenidos nuevos para agregar al prompt.")
            return False
        if current_prompt.strip():
            if "documentos de referencia" in current_prompt.lower():
                new_prompt = current_prompt.rstrip() + "\n\n" + "\n\n".join(sections)
            else:
                new_prompt = (
                    current_prompt.rstrip()
                    + "\n\n---\nDocumentos de referencia:\n"
                    + "\n\n".join(sections)
                )
        else:
            new_prompt = "Documentos de referencia:\n" + "\n\n".join(sections)
        if not self._update_agent_prompt_remote(agent_id, new_prompt):
            return False
        updated_payload = self._set_prompt_text(agent_payload, new_prompt, agent_id=agent_id)
        self._save_agent_payload(updated_payload)
        print(f"✅ Se agregaron {len(sections)} documento(s) al prompt del agente {agent_id}.")
        return True

    # ------------------------------------------------------------------
    # Operaciones públicas
    # ------------------------------------------------------------------
    def list_all_documents(self, *, verbose: bool = True) -> List[Dict[str, object]]:
        """Lista todos los documentos en la base de conocimiento."""
        documents: List[Dict[str, object]] = []
        try:
            knowledge_base = getattr(self.client.conversational_ai, "knowledge_base", None)
            list_method = getattr(knowledge_base, "list", None)
            response = None
            if callable(list_method):
                try:
                    response = list_method()
                except TypeError:
                    agent_id = self._resolve_agent_id()
                    if agent_id:
                        try:
                            response = list_method(agent_id=agent_id)
                        except TypeError:
                            response = list_method()
            if response is not None:
                payload = self._convert_model_to_dict(response)
                raw_documents = payload.get("documents") or payload.get("items") or []
                for entry in raw_documents if isinstance(raw_documents, Iterable) else []:
                    entry_dict = self._convert_model_to_dict(entry)
                    metadata = self._convert_model_to_dict(entry_dict.get("metadata"))
                    doc_info = {
                        "id": entry_dict.get("id") or metadata.get("doc_id"),
                        "name": entry_dict.get("name") or metadata.get("name"),
                        "type": entry_dict.get("type") or metadata.get("type"),
                        "size": metadata.get("size_bytes") or entry_dict.get("size_bytes"),
                    }
                    if doc_info["id"]:
                        documents.append(doc_info)
        except Exception as exc:  # pylint: disable=broad-except
            print(f"❌ Error listando documentos: {exc}")

        if not documents:
            # Fallback a metadata local
            doc_map = self._load_doc_map()
            for doc_id, metadata in doc_map.items():
                documents.append(
                    {
                        "id": doc_id,
                        "name": metadata.get("name"),
                        "type": metadata.get("type"),
                        "size": metadata.get("size_bytes"),
                    }
                )

        if verbose:
            print(f"📄 Encontrados {len(documents)} documentos:")
            for doc in documents:
                name = doc.get("name") or "(sin nombre)"
                doc_type = doc.get("type") or "desconocido"
                size = doc.get("size") or 0
                print(
                    f"  - {name} (ID: {doc['id']}) - Tipo: {doc_type}"
                    f" - Tamaño: {size} bytes"
                )

        return documents

    def delete_document(self, document_id: str) -> bool:
        """Elimina un documento específico."""
        try:
            knowledge_base = self.client.conversational_ai.knowledge_base

            # SDKs anteriores exponían el método delete directamente en
            # `knowledge_base`. Las versiones recientes lo movieron a
            # `knowledge_base.documents.delete`. Intentamos ambas opciones de
            # forma segura para mantener compatibilidad hacia atrás.
            delete_method = getattr(knowledge_base, "delete", None)

            if not callable(delete_method):
                documents_client = getattr(knowledge_base, "documents", None)
                delete_method = getattr(documents_client, "delete", None)

            if not callable(delete_method):
                raise AttributeError(
                    "El SDK de ElevenLabs no expone un método de borrado compatible."
                )

            # Algunas versiones aceptan el id como argumento posicional y otras
            # requieren palabras clave. Probamos ambos enfoques antes de fallar.
            try:
                delete_method(document_id)
            except TypeError:
                for key in ("document_id", "knowledge_base_document_id", "id"):
                    try:
                        delete_method(**{key: document_id})
                        break
                    except TypeError:
                        continue
                else:
                    raise

            print(f"\u2705 Documento eliminado: {document_id}")
            return True
        except Exception as exc:  # pylint: disable=broad-except
            print(f"\u274c Error eliminando documento {document_id}: {exc}")
            return False

    def delete_multiple_documents(self, document_ids: List[str]) -> Dict[str, List[str]]:
        """Elimina múltiples documentos."""
        results: Dict[str, List[str]] = {"success": [], "failed": []}

        print(f"\U0001F5D1\ufe0f Eliminando {len(document_ids)} documentos...")

        for doc_id in document_ids:
            if self.delete_document(doc_id):
                results["success"].append(doc_id)
            else:
                results["failed"].append(doc_id)

        print(f"\u2705 Eliminados: {len(results['success'])}")
        print(f"\u274c Fallidos: {len(results['failed'])}")

        return results

    def clear_all_knowledge_base(self, confirm: bool = False) -> Dict[str, List[str]]:
        """Elimina todos los documentos de la base de conocimiento."""
        if not confirm:
            print("\u26a0\ufe0f ADVERTENCIA: Esto eliminará TODOS los documentos.")
            confirmation = input("Escribe 'CONFIRMAR' para continuar: ")
            if confirmation != "CONFIRMAR":
                print("\u274c Operación cancelada")
                return {"success": [], "failed": []}

        print("\U0001F9F9 Limpiando toda la base de conocimiento...")

        documents = self.list_all_documents()

        if not documents:
            print("\u2705 La base de conocimiento ya está vacía")
            return {"success": [], "failed": []}

        document_ids = [doc["id"] for doc in documents if doc.get("id")]
        results = self.delete_multiple_documents(document_ids)

        print("\U0001F389 ¡Limpieza completa!")
        return results

    def delete_by_type(self, doc_type: str) -> Dict[str, List[str]]:
        """Elimina documentos por tipo (file, url, text)."""
        documents = self.list_all_documents()
        filtered_docs = [doc for doc in documents if doc.get("type") == doc_type]

        if not filtered_docs:
            print(f"\U0001F4ED No se encontraron documentos del tipo '{doc_type}'")
            return {"success": [], "failed": []}

        print(
            f"\U0001F3AF Eliminando {len(filtered_docs)} documentos del tipo '{doc_type}'..."
        )

        document_ids = [doc["id"] for doc in filtered_docs if doc.get("id")]
        return self.delete_multiple_documents(document_ids)

    def delete_by_name_pattern(self, pattern: str) -> Dict[str, List[str]]:
        """Elimina documentos que contengan un patrón en el nombre."""
        documents = self.list_all_documents()
        filtered_docs = []
        for doc in documents:
            name = doc.get("name")
            if isinstance(name, str) and pattern.lower() in name.lower():
                filtered_docs.append(doc)

        if not filtered_docs:
            print(
                f"\U0001F4ED No se encontraron documentos que contengan '{pattern}'"
            )
            return {"success": [], "failed": []}

        print(
            f"\U0001F3AF Eliminando {len(filtered_docs)} documentos que contengan '{pattern}'..."
        )

        document_ids = [doc["id"] for doc in filtered_docs if doc.get("id")]
        return self.delete_multiple_documents(document_ids)

    def add_document_to_prompt(self, document_id: str) -> bool:
        """Agrega un documento específico al prompt del agente."""
        document_id = (document_id or "").strip()
        if not document_id:
            print("⚠️ Debes proporcionar un ID de documento válido.")
            return False
        return self._append_documents_to_prompt([document_id])

    def add_all_documents_to_prompt(self) -> bool:
        """Agrega todos los documentos disponibles al prompt del agente."""
        documents = self.list_all_documents(verbose=False)
        document_ids = [doc.get("id") for doc in documents if doc.get("id")]
        if not document_ids:
            # Fallback a metadata local si el listado remoto no devolvió nada.
            document_ids = list(self._load_doc_map().keys())
        if not document_ids:
            print("⚠️ No se encontraron documentos disponibles para agregar al prompt.")
            return False
        return self._append_documents_to_prompt(document_ids)


def main() -> None:
    """Punto de entrada para la utilidad de gestión."""
    try:
        manager = KnowledgeBaseManager()
    except ValueError as exc:
        print(f"\u274c {exc}")
        return

    print("\U0001F680 Cliente de Gestión de Knowledge Base")
    print("=" * 50)

    while True:
        print("\n\U0001F4CB Opciones disponibles:")
        print("1. Listar todos los documentos")
        print("2. Eliminar documento específico")
        print("3. Limpiar toda la base de conocimiento")
        print("4. Eliminar por tipo (file/url/text)")
        print("5. Eliminar por patrón de nombre")
        print("6. Agregar un documento al prompt del agente")
        print("7. Agregar todos los documentos al prompt del agente")
        print("8. Salir")

        choice = input("\nSelecciona una opción (1-8): ").strip()

        if choice == "1":
            manager.list_all_documents()
        elif choice == "2":
            doc_id = input("Ingresa el ID del documento a eliminar: ").strip()
            if doc_id:
                manager.delete_document(doc_id)
        elif choice == "3":
            manager.clear_all_knowledge_base()
        elif choice == "4":
            doc_type = input("Ingresa el tipo (file/url/text): ").strip()
            if doc_type in {"file", "url", "text"}:
                manager.delete_by_type(doc_type)
            else:
                print("\u274c Tipo inválido. Usa: file, url o text")
        elif choice == "5":
            pattern = input("Ingresa el patrón a buscar en nombres: ").strip()
            if pattern:
                manager.delete_by_name_pattern(pattern)
        elif choice == "6":
            doc_id = input("Ingresa el ID del documento a agregar al prompt: ").strip()
            if doc_id:
                manager.add_document_to_prompt(doc_id)
        elif choice == "7":
            manager.add_all_documents_to_prompt()
        elif choice == "8":
            print("\U0001F44B ¡Hasta luego!")
            break
        else:
            print("\u274c Opción inválida")


if __name__ == "__main__":
    main()
