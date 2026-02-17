#!/usr/bin/env python3
import json
import os
import re
import subprocess
from datetime import datetime
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = int(os.environ.get("WEBHOOK_PORT", "9000"))
LOGFILE = os.environ.get("WEBHOOK_LOGFILE", "/var/log/homedir-webhook.log")
UPDATE_SCRIPT = os.environ.get("UPDATE_SCRIPT", "/usr/local/bin/homedir-update.sh")
STATUS_CMD = os.environ.get(
    "STATUS_CMD",
    "podman ps --filter name=homedir --format '{{.Image}} {{.Status}}'",
)
LOG_LINES = int(os.environ.get("WEBHOOK_LOG_LINES", "80"))
# access is controlled at the network level for now (firewall/ip allowlist)
# in the future it would be nice to add a shared secret token here, something like
# WEBHOOK_SECRET = os.environ.get("WEBHOOK_SECRET", "") and then check it in do_POST
# against a X-Webhook-Token header sent by quay — just return 403 if it doesn't match
# cheers from ñuñork city


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
    return None


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        return  # silence default logging

    def do_GET(self):
        status_out = ""
        status_err = None
        try:
            result = subprocess.run(
                STATUS_CMD,
                shell=True,
                check=False,
                capture_output=True,
                text=True,
            )
            status_out = result.stdout.strip() or "(no container found)"
            if result.returncode != 0:
                status_err = result.stderr.strip()
        except Exception as exc:  # noqa: BLE001
            status_err = str(exc)

        logs_text = "(log file not found)"
        try:
            with open(LOGFILE, "r", encoding="utf-8", errors="replace") as fh:
                lines = fh.readlines()
                logs_text = "".join(lines[-LOG_LINES:]) if lines else "(log is empty)"
        except FileNotFoundError:
            pass
        except Exception as exc:  # noqa: BLE001
            logs_text = f"(error reading log: {exc})"

        body = [
            f"status: {status_out}",
        ]
        if status_err:
            body.append(f"status_error: {status_err}")
        body.append("")
        body.append(f"last {LOG_LINES} log lines from {LOGFILE}:")
        body.append(logs_text)
        response = "\n".join(body)

        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.end_headers()
        self.wfile.write(response.encode("utf-8"))

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

        # validate tag to avoid passing unsafe values to the shell — rewrite later
        if tag and not re.fullmatch(r'[\w.\-]+', tag):
            log_line(f"rejected unsafe tag value: {tag}")
            tag = None

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