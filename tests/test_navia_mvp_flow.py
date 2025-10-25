import contextlib
import io
import json
import os
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest import mock


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
        callback_latency_measurement,
    ):
        self._client = client
        self._callback_agent_response = callback_agent_response
        self._callback_user = callback_user_transcript
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
        self._tempdir = tempfile.TemporaryDirectory()
        self.addCleanup(self._tempdir.cleanup)
        os.chdir(self._tempdir.name)

        # Ensure the repository is importable after changing directories.
        self._repo_in_path = str(REPO_ROOT)
        sys.path.insert(0, self._repo_in_path)
        self.addCleanup(self._restore_sys_path)

        os.environ["ELEVENLABS_API_KEY"] = "test-key"
        self.addCleanup(lambda: os.environ.pop("ELEVENLABS_API_KEY", None))

        self._original_requests = sys.modules.get("requests")
        requests_stub = types.ModuleType("requests")

        class HTTPError(Exception):
            def __init__(self, *args, response=None, **kwargs):  # pragma: no cover - defensive stub
                super().__init__(*args)
                self.response = response

        def _unpatched(*args, **kwargs):  # pragma: no cover - defensive
            raise AssertionError("requests fue invocado sin ser parcheado en la prueba")

        requests_stub.HTTPError = HTTPError
        requests_stub.post = _unpatched
        requests_stub.get = _unpatched
        requests_stub.request = _unpatched
        sys.modules["requests"] = requests_stub
        self.addCleanup(self._restore_requests_module)

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

    def _restore_sys_path(self) -> None:
        try:
            sys.path.remove(self._repo_in_path)
        except ValueError:  # pragma: no cover - defensive
            pass

    def _restore_requests_module(self) -> None:
        if self._original_requests is None:
            sys.modules.pop("requests", None)
        else:
            sys.modules["requests"] = self._original_requests

    def test_end_to_end_flow_uses_rag_metadata(self) -> None:
        from scripts import upload_chunks

        def post_side_effect(url, **kwargs):
            if url.endswith("/v1/convai/knowledge-base"):
                return types.SimpleNamespace(status_code=200, json=lambda: {"id": "doc-1"}, raise_for_status=lambda: None)
            if url.endswith("/v1/convai/knowledge-base/doc-1/rag-index"):
                return types.SimpleNamespace(status_code=200, json=lambda: {"status": "ok"}, raise_for_status=lambda: None)
            raise AssertionError(f"Unexpected POST url: {url}")

        with mock.patch("scripts.upload_chunks.requests.post", side_effect=post_side_effect):
            upload_chunks.main()

        doc_map = json.loads(Path(".navia/documents.json").read_text())
        self.assertIn("doc-1", doc_map)
        self.assertEqual(doc_map["doc-1"]["source_url"], "http://localhost:8080/agenda")

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
        self.assertIn("📄 Documentos cacheados disponibles: 1", output)
        self.assertIn("🧠 Documentos registrados en el agente: 1", output)


if __name__ == "__main__":  # pragma: no cover - manual execution
    unittest.main()
