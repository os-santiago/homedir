import os
import shutil
import sys
import types
import uuid
from html.parser import HTMLParser
from pathlib import Path
import unittest

REPO_ROOT = Path(__file__).resolve().parents[1]


class NormalizeAndChunkTest(unittest.TestCase):
    def setUp(self) -> None:
        super().setUp()
        self._cwd = os.getcwd()
        self._temp_root = REPO_ROOT / ".tmp" / "pytest-work"
        self._temp_root.mkdir(parents=True, exist_ok=True)
        self._workdir = self._temp_root / f"normalize-{uuid.uuid4().hex}"
        self._workdir.mkdir(parents=True, exist_ok=True)
        self.addCleanup(self._cleanup_tempdir)
        os.chdir(self._workdir)

        self._repo_in_path = str(REPO_ROOT)
        sys.path.insert(0, self._repo_in_path)
        self.addCleanup(self._restore_sys_path)

        self._module_stubs = {}
        self.addCleanup(self._restore_module_stubs)

        def stub_module(name: str, module) -> None:
            if name not in self._module_stubs:
                self._module_stubs[name] = sys.modules.get(name)
            sys.modules[name] = module

        bs4_module = types.ModuleType("bs4")

        class _MiniSoup:
            def __init__(self, html: str, parser: str):  # pragma: no cover - simple stub
                self._html = html

            def find_all(self, *args, **kwargs):  # pragma: no cover - simple stub
                return []

            def get_text(self, separator="\n", strip=False):
                extractor = _HTMLTextExtractor(strip=strip)
                extractor.feed(self._html)
                text = separator.join(extractor.chunks)
                return text.strip() if strip else text

        class _HTMLTextExtractor(HTMLParser):
            def __init__(self, strip: bool):
                super().__init__()
                self._strip = strip
                self.chunks = []

            def handle_data(self, data: str) -> None:  # pragma: no cover - simple stub
                if self._strip:
                    data = data.strip()
                if data:
                    self.chunks.append(data)

        bs4_module.BeautifulSoup = _MiniSoup
        stub_module("bs4", bs4_module)

        self.cache_root = Path("cache-root")
        self.raw_dir = self.cache_root / "raw"
        (self.raw_dir / "docs").mkdir(parents=True)

        self.chunk_root = Path("chunks-root")
        self.addCleanup(lambda: os.environ.pop("NAVIA_CACHE_DIR", None))
        self.addCleanup(lambda: os.environ.pop("NAVIA_CHUNK_DIR", None))

        os.environ["NAVIA_CACHE_DIR"] = str(self.cache_root)
        os.environ["NAVIA_CHUNK_DIR"] = str(self.chunk_root)

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

    def test_raw_html_dump_created(self) -> None:
        from scripts import normalize_and_chunk

        html_path = self.raw_dir / "docs" / "pagina.html"
        html_content = "<html><body><h1>Demo</h1><p>Contenido</p></body></html>"
        html_path.write_text(html_content, encoding="utf-8")

        normalize_and_chunk.main()

        dump_path = self.chunk_root / "raw" / "docs" / "pagina.txt"
        self.assertTrue(dump_path.exists(), "Se esperaba la exportación del HTML en chunks/raw")
        self.assertEqual(dump_path.read_text(encoding="utf-8"), html_content)


if __name__ == "__main__":  # pragma: no cover - manual execution
    unittest.main()
