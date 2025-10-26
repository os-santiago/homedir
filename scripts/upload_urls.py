#!/usr/bin/env python3
"""Crea documentos de base de conocimiento a partir de URLs listadas."""
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path
from typing import Iterable, List, Optional, Tuple

import requests
from bs4 import BeautifulSoup
from requests import Response

# Reutilizamos la lógica de autenticación y carga de documentos de upload_chunks.
try:  # pragma: no cover - import dinámico para ejecución directa
    import upload_chunks  # type: ignore
except ModuleNotFoundError:  # pragma: no cover - permite `python scripts/...`
    sys.path.append(str(Path(__file__).resolve().parent))
    import upload_chunks  # type: ignore

AGENT = upload_chunks.AGENT
DOC_MAP_PATH = upload_chunks.DOC_MAP_PATH
create_doc = upload_chunks.create_doc
_make_client = upload_chunks._make_client
compute_rag = upload_chunks.compute_rag
assign_documents = upload_chunks.assign_documents

USER_AGENT = "NaviaKnowledgeUploader/0.1"
DEFAULT_TIMEOUT = 10
DEFAULT_SLEEP = 0.5


def _read_urls(path: Path) -> List[str]:
    """Lee URLs desde un archivo ignorando comentarios y líneas vacías."""
    urls: List[str] = []
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        urls.append(line)
    return urls


def _fetch_url(url: str, *, timeout: int) -> Response:
    """Descarga el contenido de una URL con cabeceras consistentes."""
    response = requests.get(
        url,
        headers={"User-Agent": USER_AGENT},
        timeout=timeout,
    )
    response.raise_for_status()
    return response


def _extract_text(html: str) -> Tuple[Optional[str], str]:
    """Normaliza HTML a texto plano conservando el título."""
    soup = BeautifulSoup(html, "html.parser")
    for element in soup(["script", "style", "noscript", "template"]):
        element.decompose()
    title = soup.title.string.strip() if soup.title and soup.title.string else None
    text = soup.get_text(separator="\n", strip=True)
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    normalized = "\n".join(lines)
    return title, normalized


def _compose_document(title: Optional[str], body: str, url: str) -> str:
    """Construye el texto final a subir, asegurando que incluya la URL."""
    sections: List[str] = [f"URL: {url}"]
    if title:
        sections.append(f"Título: {title}")
    sections.append(body)
    sections.append(f"Fuente: {url}")
    return "\n\n".join(section for section in sections if section)


def _load_doc_map() -> dict:
    if not DOC_MAP_PATH.exists():
        return {}
    try:
        return json.loads(DOC_MAP_PATH.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {}


def _save_doc_map(doc_map: dict) -> None:
    DOC_MAP_PATH.write_text(
        json.dumps(doc_map, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def _already_uploaded(doc_map: dict, url: str) -> bool:
    for metadata in doc_map.values():
        if isinstance(metadata, dict) and metadata.get("source_url") == url:
            return True
    return False


def upload_from_urls(urls: Iterable[str], *, timeout: int, sleep: float, force: bool) -> None:
    """Sube documentos al agente a partir de URLs."""
    urls = list(urls)
    if not urls:
        print("⚠️ No se encontraron URLs para procesar.")
        return

    client = _make_client()
    doc_map = _load_doc_map()
    uploaded_ids: List[str] = []
    success_index = 0

    for url in urls:
        if not force and _already_uploaded(doc_map, url):
            print(f"⏭️  Omitiendo {url}: ya existe en documents.json")
            continue
        print(f"➡️  Procesando {url}")
        try:
            response = _fetch_url(url, timeout=timeout)
        except requests.RequestException as exc:
            print(f"❌ Error al descargar {url}: {exc}")
            continue

        content_type = response.headers.get("Content-Type", "")
        if "text" not in content_type and "json" not in content_type:
            print(f"⚠️  Contenido no textual en {url} ({content_type}). Se omite.")
            continue

        title, body = _extract_text(response.text)
        if not body:
            print(f"⚠️  {url} no devolvió texto utilizable. Se omite.")
            continue

        document_text = _compose_document(title, body, url)
        metadata = {
            "source_url": url,
            "type": "url",
            "title_guess": title,
            "name": title or url,
        }

        success_index += 1
        try:
            response = create_doc(
                client,
                AGENT,
                metadata,
                document_text,
                success_index,
            )
        except SystemExit:
            raise
        except Exception as exc:  # pragma: no cover - depende del SDK
            print(f"❌ Error inesperado al crear documento para {url}: {exc}")
            continue

        doc_id = getattr(response, "id", None)
        if doc_id is None and isinstance(response, dict):
            doc_id = response.get("id") or response.get("document_id")
        if not doc_id:
            print(f"⚠️  El SDK no devolvió un ID para {url}. Se omite.")
            continue

        metadata["doc_id"] = doc_id
        doc_map[doc_id] = metadata
        compute_rag(client, doc_id, agent_id=AGENT)
        uploaded_ids.append(doc_id)

        if sleep > 0:
            time.sleep(sleep)

    if uploaded_ids:
        _save_doc_map(doc_map)
        assign_documents(client, AGENT, uploaded_ids)
        print(f"✅ Subidos {len(uploaded_ids)} documentos nuevos al agente {AGENT}.")
    else:
        print("ℹ️ No se subieron documentos nuevos.")


def main(argv: Optional[List[str]] = None) -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Crea documentos en la base de conocimiento del agente activo a partir "
            "de URLs listadas en un archivo."
        )
    )
    parser.add_argument(
        "urls_file",
        nargs="?",
        default="urls.txt",
        help="Archivo con la lista de URLs (una por línea).",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=DEFAULT_TIMEOUT,
        help="Timeout en segundos para descargar cada URL (por defecto: %(default)s).",
    )
    parser.add_argument(
        "--sleep",
        type=float,
        default=DEFAULT_SLEEP,
        help=(
            "Tiempo en segundos para esperar entre documentos subidos. "
            "Usa 0 para deshabilitar."
        ),
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Sube siempre los documentos aunque la URL ya exista en documents.json.",
    )

    args = parser.parse_args(argv)
    urls_path = Path(args.urls_file)
    if not urls_path.exists():
        raise SystemExit(f"No se encontró el archivo de URLs: {urls_path}")

    urls = _read_urls(urls_path)
    upload_from_urls(urls, timeout=args.timeout, sleep=args.sleep, force=args.force)


if __name__ == "__main__":
    main()
