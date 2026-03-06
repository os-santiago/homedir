#!/usr/bin/env python3
from __future__ import annotations

import hmac
import hashlib
import json
import os
import re
import subprocess
from datetime import datetime
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = int(os.environ.get("WEBHOOK_PORT", "9000"))
BIND_ADDRESS = os.environ.get("WEBHOOK_BIND_ADDRESS", "127.0.0.1")
LOGFILE = os.environ.get("WEBHOOK_LOGFILE", "/var/log/homedir-webhook.log")
UPDATE_SCRIPT = os.environ.get("UPDATE_SCRIPT", "/usr/local/bin/homedir-update.sh")
ALERT_SCRIPT = os.environ.get("ALERT_SCRIPT", "/usr/local/bin/homedir-discord-alert.sh")
WEBHOOK_SHARED_SECRET = os.environ.get("WEBHOOK_SHARED_SECRET", "")
WEBHOOK_REQUIRE_SIGNATURE = (
    os.environ.get("WEBHOOK_REQUIRE_SIGNATURE", "true").strip().lower() == "true"
)
WEBHOOK_STATUS_TOKEN = os.environ.get("WEBHOOK_STATUS_TOKEN", "")
MAX_BODY_BYTES = int(os.environ.get("WEBHOOK_MAX_BODY_BYTES", "1048576"))
STATUS_CMD = os.environ.get(
    "STATUS_CMD",
    "podman ps --filter name=homedir --format '{{.Image}} {{.Status}}'",
)
TAG_PATTERN = re.compile(r"[\w.\-]+")


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


def send_alert(severity: str, title: str, message: str, details: str = "") -> None:
    if not ALERT_SCRIPT:
        return
    try:
        subprocess.run(
            [ALERT_SCRIPT, severity, title, message, details],
            check=False,
            capture_output=True,
            text=True,
        )
    except Exception:  # noqa: BLE001
        pass


def safe_remote(handler: BaseHTTPRequestHandler) -> str:
    return handler.client_address[0] if handler.client_address else "unknown"


def parse_bearer_token(value: str | None) -> str:
    if not value:
        return ""
    parts = value.strip().split(" ", 1)
    if len(parts) == 2 and parts[0].lower() == "bearer":
        return parts[1].strip()
    return ""


def is_status_authorized(handler: BaseHTTPRequestHandler) -> bool:
    if not WEBHOOK_STATUS_TOKEN:
        return False
    token = parse_bearer_token(handler.headers.get("Authorization"))
    if not token:
        token = (handler.headers.get("X-Webhook-Status-Token") or "").strip()
    return bool(token and hmac.compare_digest(token, WEBHOOK_STATUS_TOKEN))


def verify_signature(body: bytes, signature_header: str | None) -> bool:
    if not WEBHOOK_SHARED_SECRET:
        return False
    if not signature_header:
        return False
    candidate = signature_header.strip()
    algo = "sha256"
    hex_digest = candidate
    if "=" in candidate:
        prefix, value = candidate.split("=", 1)
        algo = prefix.strip().lower()
        hex_digest = value.strip()
    if algo not in {"sha1", "sha256"} or not hex_digest:
        return False
    digestmod = hashlib.sha256 if algo == "sha256" else hashlib.sha1
    expected = hmac.new(WEBHOOK_SHARED_SECRET.encode("utf-8"), body, digestmod).hexdigest()
    return hmac.compare_digest(expected, hex_digest)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        return  # silence default logging

    def do_GET(self):
        if not is_status_authorized(self):
            self.send_response(403)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.end_headers()
            self.wfile.write(b"forbidden\n")
            log_line(f"status denied remote={safe_remote(self)}")
            return

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

        payload = {
            "service": "homedir-webhook",
            "timestamp_utc": datetime.utcnow().isoformat(timespec="seconds") + "Z",
            "container_status": status_out,
            "status_error": status_err or "",
            "update_script": UPDATE_SCRIPT,
            "max_body_bytes": MAX_BODY_BYTES,
            "signature_required": WEBHOOK_REQUIRE_SIGNATURE,
        }

        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.end_headers()
        self.wfile.write((json.dumps(payload) + "\n").encode("utf-8"))
        log_line(f"status served remote={safe_remote(self)}")

    def do_POST(self):
        length = int(self.headers.get("content-length", 0))
        if length > MAX_BODY_BYTES:
            self.send_response(413)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"payload too large\n")
            log_line(f"rejected webhook payload too large remote={safe_remote(self)} bytes={length}")
            return

        body = self.rfile.read(length)
        signature_valid = verify_signature(body, self.headers.get("X-Quay-Signature"))
        if WEBHOOK_REQUIRE_SIGNATURE and not signature_valid:
            self.send_response(401)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"invalid signature\n")
            log_line(
                f"rejected webhook invalid signature remote={safe_remote(self)} bytes={len(body)}"
            )
            send_alert(
                "WARN",
                "Webhook request rejected",
                "Request signature validation failed",
                f"remote={safe_remote(self)}",
            )
            return

        tag = None
        err = None
        try:
            payload = json.loads(body)
            tag = extract_tag(payload)
        except Exception as exc:  # noqa: BLE001
            err = str(exc)
            self.send_response(400)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"invalid json\n")
            log_line(f"rejected webhook invalid json remote={safe_remote(self)} bytes={len(body)}")
            return

        if tag and not TAG_PATTERN.fullmatch(tag):
            log_line(f"rejected webhook invalid tag remote={safe_remote(self)} tag={tag}")
            send_alert(
                "WARN",
                "Webhook payload rejected",
                "Received an invalid tag format from webhook payload",
                f"tag={tag}",
            )
            tag = None

        log_line(
            f"received webhook remote={safe_remote(self)} tag={tag} bytes={len(body)} signature_valid={signature_valid} error={err}"
        )
        if tag:
            env = os.environ.copy()
            env["DEPLOY_TRIGGER"] = "webhook"
            result = subprocess.run(
                [UPDATE_SCRIPT, tag],
                check=False,
                capture_output=True,
                text=True,
                env=env,
            )
            log_line(
                f"update.sh tag={tag} exit={result.returncode} stdout={result.stdout.strip()} stderr={result.stderr.strip()}"
            )
            if result.returncode != 0:
                send_alert(
                    "FAIL",
                    "Webhook deploy execution failed",
                    "homedir-update.sh exited with non-zero status",
                    f"tag={tag} exit={result.returncode}",
            )
        else:
            log_line("skipping update.sh because tag is missing")
            send_alert(
                "WARN",
                "Webhook payload missing deploy tag",
                "Webhook request did not include a valid tag",
                f"remote={safe_remote(self)}",
            )
        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        self.end_headers()
        self.wfile.write(b"ok\n")


def main():
    if WEBHOOK_REQUIRE_SIGNATURE and not WEBHOOK_SHARED_SECRET:
        log_line("WARNING: webhook signature required but WEBHOOK_SHARED_SECRET is not set")
    if not WEBHOOK_STATUS_TOKEN:
        log_line("WARNING: WEBHOOK_STATUS_TOKEN is not set; GET status endpoint is disabled")
    server = HTTPServer((BIND_ADDRESS, PORT), Handler)
    log_line(f"webhook server starting on {BIND_ADDRESS}:{PORT}")
    server.serve_forever()


if __name__ == "__main__":
    main()
