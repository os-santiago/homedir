import glob
import json
import os
import time
from pathlib import Path

import requests

API = os.environ.get("ELEVENLABS_BASE_URL", "https://api.elevenlabs.io")
KEY = os.environ["ELEVENLABS_API_KEY"]
AGENT_PATH = Path("./.navia/agent.json")

if not AGENT_PATH.exists():
    raise SystemExit("No se encontró ./.navia/agent.json. Ejecuta scripts/create_agent.sh primero.")

AGENT = json.loads(AGENT_PATH.read_text())["id"]
HEADERS = {"xi-api-key": KEY, "Content-Type": "application/json"}


def create_doc(agent_id: str, meta: dict, text: str) -> dict:
    url = f"{API}/v1/agents/{agent_id}/knowledge-base/documents"
    payload = {"title": meta.get("title_guess", "Navia Chunk"), "metadata": meta, "text": text}
    response = requests.post(url, headers=HEADERS, json=payload, timeout=60)
    response.raise_for_status()
    return response.json()


def compute_rag(agent_id: str, doc_id: str) -> dict:
    url = f"{API}/v1/agents/{agent_id}/knowledge-base/documents/{doc_id}/rag-index"
    response = requests.post(url, headers=HEADERS, timeout=60)
    response.raise_for_status()
    return response.json()


def main() -> None:
    files = glob.glob("./.navia/chunks/*.json")
    if not files:
        raise SystemExit("No se encontraron chunks en ./.navia/chunks. Ejecuta scripts/normalize_and_chunk.py primero.")

    for index, file_path in enumerate(files, start=1):
        data = json.loads(Path(file_path).read_text())
        doc = create_doc(AGENT, data["meta"], data["text"])
        compute_rag(AGENT, doc["id"])
        if index % 25 == 0:
            time.sleep(1)

    print(f"Subidos {len(files)} chunks al agente {AGENT}")


if __name__ == "__main__":
    main()
