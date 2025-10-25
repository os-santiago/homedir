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


class DummyResponse:
    def __init__(self, *, status_code=200, json_data=None, text="", headers=None):
        self.status_code = status_code
        self._json_data = json_data if json_data is not None else {}
        self.text = text
        self.content = text.encode("utf-8")
        self.headers = headers or {}

    @property
    def ok(self) -> bool:  # pragma: no cover - trivial
        return self.status_code < 400

    def raise_for_status(self) -> None:
        if self.status_code >= 400:
            import requests

            raise requests.HTTPError(response=self)

    def json(self):  # pragma: no cover - simple passthrough
        return self._json_data


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
            def __init__(self, *args, response=None, **kwargs):
                super().__init__(*args)
                self.response = response

        def _unpatched_factory(name):
            def _unpatched(*args, **kwargs):
                raise AssertionError(f"requests.{name} was called before being patched in the test")

            return _unpatched

        requests_stub.HTTPError = HTTPError
        requests_stub.post = _unpatched_factory("post")
        requests_stub.get = _unpatched_factory("get")
        requests_stub.request = _unpatched_factory("request")
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
                return DummyResponse(json_data={"id": "doc-1"})
            if url.endswith("/v1/convai/knowledge-base/doc-1/rag-index"):
                return DummyResponse(json_data={"status": "ok"})
            raise AssertionError(f"Unexpected POST url: {url}")

        with mock.patch("scripts.upload_chunks.requests.post", side_effect=post_side_effect):
            upload_chunks.main()

        doc_map = json.loads(Path(".navia/documents.json").read_text())
        self.assertIn("doc-1", doc_map)
        self.assertEqual(doc_map["doc-1"]["source_url"], "http://localhost:8080/agenda")

        from scripts import ask_agent

        def request_side_effect(method, url, **kwargs):
            if url.endswith("/v1/convai/agents/agent-123/conversations/create"):
                return DummyResponse(status_code=405, json_data={"detail": "use shared endpoint"})
            if url.endswith("/v1/convai/conversations/create"):
                return DummyResponse(json_data={"conversation": {"id": "conv-1"}})
            if url.endswith("/v1/convai/agents/agent-123/conversations/conv-1/messages"):
                return DummyResponse(status_code=405)
            if url.endswith("/v1/convai/conversations/conv-1/messages/create"):
                return DummyResponse(json_data={"id": "msg-1"})
            if url.endswith("/v1/convai/agents/agent-123/conversations/conv-1") or url.endswith(
                "/v1/convai/conversations/conv-1"
            ):
                payload = {
                    "conversation": {
                        "id": "conv-1",
                        "messages": [
                            {"role": "user", "content": "¿Dónde está el contenido?"},
                            {
                                "role": "assistant",
                                "content": "Puedes encontrarlo en la agenda.",
                                "documents": [{"id": "doc-1"}],
                            },
                        ],
                    },
                    "rag_references": [
                        {
                            "document": {
                                "id": "doc-1",
                                "metadata": doc_map["doc-1"],
                            }
                        }
                    ],
                }
                return DummyResponse(json_data=payload)
            raise AssertionError(f"Unexpected {method} request to {url}")

        def get_side_effect(url, **kwargs):
            if url.endswith("/v1/convai/knowledge-base/documents/doc-1"):
                return DummyResponse(json_data={"metadata": doc_map["doc-1"]})
            raise AssertionError(f"Unexpected GET url: {url}")

        with (
            mock.patch("scripts.ask_agent.requests.request", side_effect=request_side_effect),
            mock.patch("scripts.ask_agent.requests.get", side_effect=get_side_effect),
            mock.patch("builtins.input", return_value="¿Dónde está el contenido?"),
            mock.patch("time.sleep", return_value=None),
            io.StringIO() as buf,
            contextlib.redirect_stdout(buf),
        ):
            ask_agent.main()
            output = buf.getvalue()

        self.assertIn("=== RESPUESTA DEL AGENTE ===", output)
        self.assertIn("Puedes encontrarlo en la agenda.", output)
        self.assertIn("Rutas sugeridas", output)
        self.assertIn("http://localhost:8080/agenda", output)


if __name__ == "__main__":  # pragma: no cover - manual execution
    unittest.main()
