import asyncio
import contextlib
import io
import sys
import types
import unittest
from pathlib import Path
from unittest import mock

REPO_ROOT = Path(__file__).resolve().parents[1]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

if "elevenlabs" not in sys.modules:
    elevenlabs_pkg = types.ModuleType("elevenlabs")
    sys.modules["elevenlabs"] = elevenlabs_pkg
    client_pkg = types.ModuleType("elevenlabs.client")

    class _DummyElevenLabs:  # pragma: no cover - simple stub
        def __init__(self, *args, **kwargs):
            pass

    client_pkg.ElevenLabs = _DummyElevenLabs
    sys.modules["elevenlabs.client"] = client_pkg

    conv_pkg = types.ModuleType("elevenlabs.conversational_ai")
    conversation_pkg = types.ModuleType("elevenlabs.conversational_ai.conversation")

    class _DummyAudioInterface:  # pragma: no cover - stub
        pass

    class _DummyConversation:  # pragma: no cover - stub
        def __init__(self, *args, **kwargs):
            pass

    class _DummyConversationInitiationData:  # pragma: no cover - stub
        def __init__(self, *args, **kwargs):
            pass

    conversation_pkg.AudioInterface = _DummyAudioInterface
    conversation_pkg.Conversation = _DummyConversation
    conversation_pkg.ConversationInitiationData = _DummyConversationInitiationData

    sys.modules["elevenlabs.conversational_ai"] = conv_pkg
    sys.modules["elevenlabs.conversational_ai.conversation"] = conversation_pkg

from scripts.ask_agent import PromptAwarePrinter, diagnose_agent_context


class DummyLoop:
    def call_soon_threadsafe(self, callback, *args):  # pragma: no cover - simple proxy
        callback(*args)


class PromptAwarePrinterTest(unittest.TestCase):
    def test_buffers_messages_until_prompt_finishes(self) -> None:
        loop = DummyLoop()
        printer = PromptAwarePrinter(loop)
        latency_printer = printer.make_printer("⏱️ Latencia: ")

        with io.StringIO() as buf, contextlib.redirect_stdout(buf):
            printer.prompt_started()
            latency_printer("170")
            self.assertEqual(buf.getvalue(), "")
            printer.prompt_finished()
            output = buf.getvalue()

        self.assertIn("⏱️ Latencia: 170", output)

    def test_flush_buffer_prints_pending_lines(self) -> None:
        loop = DummyLoop()
        printer = PromptAwarePrinter(loop)
        latency_printer = printer.make_printer("⏱️ Latencia: ")

        with io.StringIO() as buf, contextlib.redirect_stdout(buf):
            printer.prompt_started()
            latency_printer("171")
            printer.flush_buffer()
            output = buf.getvalue()

        self.assertIn("⏱️ Latencia: 171", output)


class DiagnoseAgentContextTest(unittest.TestCase):
    def test_warns_when_remote_documents_missing(self) -> None:
        async def _run() -> str:
            client = types.SimpleNamespace(
                conversational_ai=types.SimpleNamespace(
                    knowledge_base=types.SimpleNamespace(
                        documents=types.SimpleNamespace(list=lambda **kwargs: {"total": 0})
                    )
                )
            )
            with io.StringIO() as buf, contextlib.redirect_stdout(buf):
                with mock.patch("scripts.ask_agent.load_local_doc_map", return_value={}):
                    await diagnose_agent_context(client, "agent-x", {"agent_id": "agent-x"})
                return buf.getvalue()

        output = asyncio.run(_run())
        self.assertIn("No se encontraron documentos cacheados", output)
        self.assertIn("La base de conocimiento del agente no tiene documentos disponibles", output)


if __name__ == "__main__":  # pragma: no cover - manual execution
    unittest.main()
