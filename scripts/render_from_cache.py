import os
import pathlib
import sys
from typing import Optional

CACHE_DIR = pathlib.Path(os.environ.get("NAVIA_CACHE_DIR", "./.navia/site-cache"))
RAW_DIR = CACHE_DIR / "raw"


def page_path_from_url(url: str) -> pathlib.Path:
    base = os.environ.get("NAVIA_SITE_BASE", "http://localhost:8080")
    if not url.startswith(base):
        raise ValueError(f"La URL '{url}' no pertenece a la base configurada {base}.")
    suffix = url[len(base) :]
    raw_path = RAW_DIR / suffix.lstrip("/")
    if raw_path.is_dir():
        raw_path = raw_path / "index.html"
    return raw_path


def extract_fragment(html: str, anchor: Optional[str]) -> str:
    if not anchor:
        return html
    marker = f'id="{anchor}"'
    if marker not in html:
        return html
    _, _, rest = html.partition(marker)
    snippet = rest.split("</", 1)[0]
    return f"<div id=\"{anchor}\">{snippet}</div>"


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit("Uso: python scripts/render_from_cache.py <url> [anchor]")

    url = sys.argv[1]
    anchor = sys.argv[2] if len(sys.argv) > 2 else None

    path = page_path_from_url(url)
    if not path.exists():
        raise SystemExit(f"No se encontró la página cacheada en {path}.")

    html = path.read_text(errors="ignore")
    fragment = extract_fragment(html, anchor)

    print(f"<!-- Origen: {url} -->")
    print(fragment)


if __name__ == "__main__":
    main()
