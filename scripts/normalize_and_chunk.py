import json
import hashlib
import os
import pathlib
from typing import Iterable

from bs4 import BeautifulSoup

RAW_CACHE = pathlib.Path(os.environ.get("NAVIA_CACHE_DIR", "./.navia/site-cache")) / "raw"
CHUNK_DIR = pathlib.Path(os.environ.get("NAVIA_CHUNK_DIR", "./.navia/chunks"))
CHUNK_DIR.mkdir(parents=True, exist_ok=True)
CHUNK_RAW_DIR = CHUNK_DIR / "raw"
CHUNK_RAW_DIR.mkdir(parents=True, exist_ok=True)


def clean_text(html: str) -> str:
    """Return a lightly normalised text version of the HTML document."""
    soup = BeautifulSoup(html, "html.parser")
    # Preserve original anchors so we can map chunks back to fragments later on.
    for tag in soup.find_all(True):
        if "id" in tag.attrs:
            tag["data-anchor-id"] = tag["id"]
    return soup.get_text("\n", strip=True)


def chunk_text(text: str, max_len: int = 1200) -> Iterable[str]:
    """Yield chunks of roughly ``max_len`` tokens (approximated with words)."""
    words = text.split()
    for index in range(0, len(words), max_len):
        yield " ".join(words[index : index + max_len])


def main() -> None:
    records = []
    if not RAW_CACHE.exists():
        raise SystemExit(f'No se encontró el cache en {RAW_CACHE}. Ejecuta el scrape primero.')

    base_url = os.environ.get("NAVIA_SITE_BASE", "http://localhost:8080")

    for path in RAW_CACHE.rglob("*.html"):
        url_suffix = '/' + str(path.relative_to(RAW_CACHE)).replace('\\', '/')
        url = base_url.rstrip('/') + url_suffix
        html = path.read_text(errors="ignore")
        # Guardar una copia del HTML original junto al chunk normalizado.
        relative_path = path.relative_to(RAW_CACHE)
        raw_dump_path = (CHUNK_RAW_DIR / relative_path).with_suffix(".txt")
        raw_dump_path.parent.mkdir(parents=True, exist_ok=True)
        raw_dump_path.write_text(html, encoding="utf-8", errors="ignore")
        text = clean_text(html)
        if len(text) < 40:
            continue

        for chunk_index, chunk in enumerate(chunk_text(text)):
            doc_id = hashlib.md5((url + str(chunk_index)).encode()).hexdigest()
            metadata = {
                "doc_id": doc_id,
                "source_url": url,
                "source_path": str(path),
                "chunk_index": chunk_index,
                "title_guess": path.stem.replace("-", " ").title(),
            }
            out_path = CHUNK_DIR / f"{doc_id}.json"
            out_path.write_text(
                json.dumps(
                    {
                        "meta": metadata,
                        "content": f"{url}\n{html}",
                        "text": chunk,
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            records.append(metadata)

    print(f"Chunks creados: {len(records)} → {CHUNK_DIR}")


if __name__ == "__main__":
    main()
