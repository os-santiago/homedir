import contextlib
import io
import json
import os
import sys
import types
import unittest
from pathlib import Path
from unittest import mock
import shutil
import uuid


REPO_ROOT = Path(__file__).resolve().parents[1]


class DummyDocuments:
    def __init__(self, doc_map):
        self._doc_map = doc_map

    def list(self, *, agent_id, page_size=50):  # pragma: no cover - trivial delegation
        return {"total": len(self._doc_map), "documents": [{"id": doc_id} for doc_id in self._doc_map]}

    def get(self, doc_id, *, agent_id):
        meta = dict(self._doc_map.get(doc_id, {}))
        return types.SimpleNamespace(
            metadata=meta,
            name=meta.get("title_guess"),
            url=meta.get("source_url"),
            id=doc_id,
            extracted_inner_html=meta.get("extracted_inner_html"),
        )


class DummyConversationStore:
    def __init__(self):
        self.transcript = []

    def get(self, *, conversation_id):
        return types.SimpleNamespace(transcript=self.transcript)


class DummyClient:
    def __init__(self, doc_map):
        self.doc_map = doc_map
        self.conversational_ai = types.SimpleNamespace(
            knowledge_base=types.SimpleNamespace(documents=DummyDocuments(doc_map)),
            conversations=DummyConversationStore(),
        )


class DummyConversation:
    def __init__(
        self,
        client,
        agent_id,
        requires_auth,
        audio_interface,
        config,
        callback_agent_response,
        callback_user_transcript,
        callback_latency_measurement=None,
    ):
        self._client = client
        self._callback_agent_response = callback_agent_response
        self._callback_user = callback_user_transcript
        if callback_latency_measurement is None:
            callback_latency_measurement = lambda *_args, **_kwargs: None  # pragma: no cover - default noop
        self._callback_latency = callback_latency_measurement
        self._conversation_id = "conv-1"
        self._agent_id = agent_id
        self._ended = False

    def start_session(self):  # pragma: no cover - trivial
        pass

    def end_session(self):  # pragma: no cover - trivial
        self._ended = True

    def wait_for_session_end(self):  # pragma: no cover - deterministic value
        return self._conversation_id

    def send_user_message(self, message):
        self._callback_user(message)
        self._callback_latency("170")
        answer = "Puedes encontrarlo en la agenda."
        self._callback_agent_response(answer)
        chunk = types.SimpleNamespace(document_id="doc-1", chunk_id="chunk-123")
        rag_info = types.SimpleNamespace(chunks=[chunk])
        transcript = [
            types.SimpleNamespace(role="user", message=message),
            types.SimpleNamespace(role="agent", message=answer, rag_retrieval_info=rag_info),
        ]
        self._client.conversational_ai.conversations.transcript = transcript

    def start_session_with_conversation(self):  # pragma: no cover - compatibility shim
        self.start_session()


class NaviaMVPFlowTest(unittest.TestCase):
    def setUp(self) -> None:
        super().setUp()
        self._cwd = os.getcwd()
        self._temp_root = REPO_ROOT / ".tmp" / "pytest-work"
        self._temp_root.mkdir(parents=True, exist_ok=True)
        self._workdir = self._temp_root / f"navia-{uuid.uuid4().hex}"
        self._workdir.mkdir(parents=True, exist_ok=True)
        self.addCleanup(self._cleanup_tempdir)
        os.chdir(self._workdir)

        # Ensure the repository is importable after changing directories.
        self._repo_in_path = str(REPO_ROOT)
        sys.path.insert(0, self._repo_in_path)
        self.addCleanup(self._restore_sys_path)

        self._module_stubs = {}
        self.addCleanup(self._restore_module_stubs)

        def stub_module(name: str, module) -> None:
            if name not in self._module_stubs:
                self._module_stubs[name] = sys.modules.get(name)
            sys.modules[name] = module

        elevenlabs_module = types.ModuleType("elevenlabs")
        client_module = types.ModuleType("elevenlabs.client")
        core_module = types.ModuleType("elevenlabs.core")
        api_error_module = types.ModuleType("elevenlabs.core.api_error")
        conversational_ai_module = types.ModuleType("elevenlabs.conversational_ai")
        conversation_module = types.ModuleType("elevenlabs.conversational_ai.conversation")

        class _DummyApiError(Exception):
            def __init__(self, *, status_code=None, body=None, headers=None):
                super().__init__("dummy")
                self.status_code = status_code
                self.body = body
                self.headers = headers

        class _PlaceholderClient:
            def __init__(self, *args, **kwargs):
                self.args = args
                self.kwargs = kwargs

        class _PlaceholderAudioInterface:
            def __init__(self, *args, **kwargs):
                self.args = args
                self.kwargs = kwargs

        class _PlaceholderConversation:
            def __init__(self, *args, **kwargs):
                self.args = args
                self.kwargs = kwargs

        class _PlaceholderConversationInitiationData:
            def __init__(self, *args, **kwargs):
                self.args = args
                self.kwargs = kwargs

        client_module.ElevenLabs = _PlaceholderClient
        api_error_module.ApiError = _DummyApiError
        conversation_module.AudioInterface = _PlaceholderAudioInterface
        conversation_module.Conversation = _PlaceholderConversation
        conversation_module.ConversationInitiationData = _PlaceholderConversationInitiationData

        elevenlabs_module.conversational_ai = conversational_ai_module
        conversational_ai_module.conversation = conversation_module

        stub_module("elevenlabs", elevenlabs_module)
        stub_module("elevenlabs.client", client_module)
        stub_module("elevenlabs.core", core_module)
        stub_module("elevenlabs.core.api_error", api_error_module)
        stub_module("elevenlabs.conversational_ai", conversational_ai_module)
        stub_module("elevenlabs.conversational_ai.conversation", conversation_module)

        os.environ["ELEVENLABS_API_KEY"] = "test-key"
        self.addCleanup(lambda: os.environ.pop("ELEVENLABS_API_KEY", None))

        Path(".navia/chunks").mkdir(parents=True)
        Path(".navia").mkdir(exist_ok=True)

        agent_payload = {"agent_id": "agent-123"}
        Path(".navia/agent.json").write_text(json.dumps(agent_payload))

        chunk_payload = {
            "text": "Contenido de ejemplo para Navia.",
            "meta": {
                "title_guess": "Agenda principal",
                "source_url": "http://localhost:8080/agenda",
                "source_path": "site-cache/raw/agenda.html",
                "chunk_index": 1,
            },
        }
        Path(".navia/chunks/chunk-1.json").write_text(json.dumps(chunk_payload))

    def tearDown(self) -> None:
        os.chdir(self._cwd)
        super().tearDown()

    def _cleanup_tempdir(self) -> None:
        shutil.rmtree(self._workdir, ignore_errors=True)

    def _restore_sys_path(self) -> None:
        try:
            sys.path.remove(self._repo_in_path)
        except ValueError:  # pragma: no cover - defensive
            pass

    def _restore_module_stubs(self) -> None:
        for name, original in self._module_stubs.items():
            if original is None:
                sys.modules.pop(name, None)
            else:
                sys.modules[name] = original

    def test_end_to_end_flow_uses_rag_metadata(self) -> None:
        from scripts import upload_chunks

        class DummyUploadSDK:
            def __init__(self):
                self.uploads = []
                self.agent_id = "agent-123"
                self.agent_updates = []
                self.documents_store = {}

                self.conversational_ai = types.SimpleNamespace(
                    knowledge_base=types.SimpleNamespace(
                        documents=types.SimpleNamespace(
                            create_from_text=self._create_from_text,
                            get=self._documents_get,
                        ),
                    ),
                    agents=types.SimpleNamespace(update=self._agents_update),
                )

            def _create_from_text(self, *, name=None, text=None):
                doc_id = f"doc-{len(self.uploads) + 1}"
                self.uploads.append({
                    "name": name,
                    "text": text,
                })
                self.documents_store[doc_id] = {"id": doc_id, "name": name, "type": "file"}
                return types.SimpleNamespace(id=doc_id, name=name)

            def _agents_update(self, *, agent_id=None, knowledge_base=None):
                if agent_id != self.agent_id:
                    raise AssertionError("Agent ID inesperado al actualizar")
                payload = {
                    "agent_id": agent_id,
                    "knowledge_base": list(knowledge_base or []),
                }
                self.agent_updates.append(payload)
                return types.SimpleNamespace(agent_id=agent_id, knowledge_base=payload["knowledge_base"])

            def _documents_get(self, documentation_id, *, agent_id=None):
                data = self.documents_store.get(documentation_id)
                if data is None:
                    raise AssertionError(f"Documento desconocido: {documentation_id}")
                return types.SimpleNamespace(**data)

        dummy_sdk = DummyUploadSDK()

        with mock.patch("scripts.upload_chunks.ElevenLabs", return_value=dummy_sdk):
            upload_chunks.main()

        doc_map = json.loads(Path(".navia/documents.json").read_text())
        self.assertIn("doc-1", doc_map)
        self.assertEqual(doc_map["doc-1"]["source_url"], "http://localhost:8080/agenda")
        self.assertEqual(doc_map["doc-1"]["name"], "Agenda principal")
        self.assertEqual(len(dummy_sdk.agent_updates), 1)
        assigned_docs = dummy_sdk.agent_updates[0]["knowledge_base"]
        self.assertEqual(assigned_docs, ["doc-1"])

        from scripts import ask_agent

        dummy_client = DummyClient(doc_map)

        with (
            mock.patch("scripts.ask_agent.ElevenLabs", return_value=dummy_client),
            mock.patch("scripts.ask_agent.Conversation", DummyConversation),
            mock.patch("scripts.ask_agent.wait_for_conversation_id", return_value="conv-1"),
            io.StringIO() as buf,
            contextlib.redirect_stdout(buf),
        ):
            ask_agent.main(["--question", "¿Dónde está el contenido?"])
            output = buf.getvalue()

        self.assertIn("=== RESPUESTA DEL AGENTE ===", output)
        self.assertIn("Puedes encontrarlo en la agenda.", output)
        self.assertIn("Rutas sugeridas", output)
        self.assertIn("http://localhost:8080/agenda", output)
        self.assertIn("🧠 Documentos registrados en el agente: 1", output)


if __name__ == "__main__":  # pragma: no cover - manual execution
    unittest.main()
