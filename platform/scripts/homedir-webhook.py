#!/usr/bin/env python3
import json
import os
import subprocess
from datetime import datetime
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = int(os.environ.get("WEBHOOK_PORT", "9000"))
LOGFILE = os.environ.get("WEBHOOK_LOGFILE", "/var/log/homedir-webhook.log")
UPDATE_SCRIPT = os.environ.get("UPDATE_SCRIPT", "/usr/local/bin/homedir-update.sh")


def log_line(msg: str) -> None:
    stamp = datetime.utcnow().isoformat(timespec="seconds") + "Z"
    with open(LOGFILE, "a", encoding="utf-8") as fh:
        fh.write(f"{stamp} {msg}\n")


def extract_tag(payload: dict) -> str | None:
    """Support push and build-complete payloads from Quay; ignore `latest`."""
    tags: list[str] = []
    repo = payload.get("repository") or {}
    if isinstance(repo, dict):
        tags.extend((repo.get("docker_tags") or []) + (repo.get("updated_tags") or []))
    if not tags:
        tags.extend((payload.get("docker_tags") or []) + (payload.get("updated_tags") or []))
    for t in tags:
        if t and t != "latest":
            return t
    return tags[0] if tags else None


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        return  # silence default logging

    def do_POST(self):
        length = int(self.headers.get("content-length", 0))
        body = self.rfile.read(length)
        tag = None
        err = None
        try:
            payload = json.loads(body)
            tag = extract_tag(payload)
        except Exception as exc:  # noqa: BLE001
            err = str(exc)
        safe_body = body.decode(errors="replace")
        log_line(
            f"received webhook from {self.client_address[0]} tag={tag} bytes={len(body)} error={err} body={safe_body}"
        )
        if tag:
            result = subprocess.run(
                [UPDATE_SCRIPT, tag],
                check=False,
                capture_output=True,
                text=True,
            )
            log_line(
                f"update.sh tag={tag} exit={result.returncode} stdout={result.stdout.strip()} stderr={result.stderr.strip()}"
            )
        else:
            log_line("skipping update.sh because tag is missing")
        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        self.end_headers()
        self.wfile.write(f"received tag={tag}\n".encode())


def main():
    server = HTTPServer(("0.0.0.0", PORT), Handler)
    log_line(f"webhook server starting on 0.0.0.0:{PORT}")
    server.serve_forever()


if __name__ == "__main__":
    main()
