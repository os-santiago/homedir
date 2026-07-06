#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import hmac
import json
import os
import shlex
import subprocess
import shutil
import tempfile
from dataclasses import dataclass
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


def env_or_file(name: str, default: str = "") -> str:
    value = os.environ.get(name, "")
    if value:
        return value
    file_path = os.environ.get(f"{name}_FILE", "").strip()
    if not file_path:
        return default
    try:
        with open(file_path, "r", encoding="utf-8") as fh:
            return fh.read().strip()
    except OSError:
        return default


PORT = int(os.environ.get("GITHUB_WEBHOOK_PORT", "9001"))
BIND_ADDRESS = os.environ.get("GITHUB_WEBHOOK_BIND_ADDRESS", "127.0.0.1")
LOGFILE = os.environ.get("GITHUB_WEBHOOK_LOGFILE", "/var/log/homedir-github-webhook.log")
ALERT_SCRIPT = os.environ.get("ALERT_SCRIPT", "/usr/local/bin/homedir-discord-alert.sh")
OPENCLAW_MONITOR_COMMAND = os.environ.get("OPENCLAW_GITHUB_MONITOR_COMMAND", "").strip()
SDLC_HOOK_ENABLED = os.environ.get("HOMEDIR_SDLC_GITHUB_HOOK_ENABLED", "true").strip().lower() == "true"
SDLC_HOOK_LABEL = os.environ.get("HOMEDIR_SDLC_GITHUB_HOOK_LABEL", "ready-to-implement").strip() or "ready-to-implement"
SDLC_QUEUE_LABEL = os.environ.get("HOMEDIR_SDLC_QUEUE_LABEL", "scc-queued").strip() or "scc-queued"
SDLC_REJECTED_LABEL = os.environ.get("HOMEDIR_SDLC_REJECTED_LABEL", "scc-rejected").strip() or "scc-rejected"
SDLC_UNAUTHORIZED_LABEL = (
    os.environ.get("HOMEDIR_SDLC_UNAUTHORIZED_LABEL", "scc-rejected:unauthorized-labeler").strip()
    or "scc-rejected:unauthorized-labeler"
)
SDLC_ADMISSION_REVIEW_LABEL = (
    os.environ.get("HOMEDIR_SDLC_ADMISSION_REVIEW_LABEL", "scc-admission-review").strip()
    or "scc-admission-review"
)
SDLC_ACCEPTED_LABEL = os.environ.get("HOMEDIR_SDLC_ACCEPTED_LABEL", "scc-accepted").strip() or "scc-accepted"
SDLC_AUTHORIZED_LABELERS = {
    login.strip().casefold()
    for login in os.environ.get("HOMEDIR_SDLC_AUTHORIZED_LABELERS", "scanalesespinoza").split(",")
    if login.strip()
}
SDLC_HOOK_COMMAND = os.environ.get("HOMEDIR_SDLC_GITHUB_HOOK_COMMAND", "").strip()
SDLC_HOOK_TIMEOUT_SECONDS = int(os.environ.get("HOMEDIR_SDLC_GITHUB_HOOK_TIMEOUT_SECONDS", "20"))
SDLC_EVENT_COMMAND = os.environ.get("HOMEDIR_SDLC_GITHUB_EVENT_COMMAND", "").strip()
ALERT_TIMEOUT_SECONDS = int(os.environ.get("GITHUB_WEBHOOK_ALERT_TIMEOUT_SECONDS", "10"))
OPENCLAW_HOOK_TIMEOUT_SECONDS = int(os.environ.get("OPENCLAW_GITHUB_MONITOR_TIMEOUT_SECONDS", "10"))
DISCORD_TARGET = os.environ.get("GITHUB_WEBHOOK_DISCORD_TARGET", "community").strip().casefold() or "community"
TARGET_BRANCH = os.environ.get("GITHUB_WEBHOOK_TARGET_BRANCH", "main").strip().casefold() or "main"
WOS_REVIEW_LABEL = os.environ.get("GITHUB_WEBHOOK_WOS_REVIEW_LABEL", "wos-review").strip().casefold() or "wos-review"
WOS_REVIEW_COMMAND = (
    os.environ.get(
        "GITHUB_WEBHOOK_WOS_REVIEW_COMMAND",
        "{claude} --allow-dangerously-skip-permissions -p {prompt}",
    ).strip()
    or "{claude} --allow-dangerously-skip-permissions -p {prompt}"
)
WOS_REVIEW_DISCORD_TARGET = (
    os.environ.get("GITHUB_WEBHOOK_WOS_REVIEW_DISCORD_TARGET", "openclaw").strip().casefold()
    or "openclaw"
)
WOS_REVIEW_PROMPT_MAX_CHARS = int(os.environ.get("GITHUB_WEBHOOK_WOS_REVIEW_PROMPT_MAX_CHARS", "6000"))
WEBHOOK_SHARED_SECRET = env_or_file("GITHUB_WEBHOOK_SHARED_SECRET", "")
WEBHOOK_REQUIRE_SIGNATURE = (
    os.environ.get("GITHUB_WEBHOOK_REQUIRE_SIGNATURE", "true").strip().lower() == "true"
)
WEBHOOK_STATUS_TOKEN = env_or_file("GITHUB_WEBHOOK_STATUS_TOKEN", "")
MAX_BODY_BYTES = int(os.environ.get("GITHUB_WEBHOOK_MAX_BODY_BYTES", "1048576"))
ALLOWED_REPOSITORIES = {
    repo.strip().casefold()
    for repo in os.environ.get("GITHUB_WEBHOOK_ALLOWED_REPOSITORIES", "").split(",")
    if repo.strip()
}


@dataclass(frozen=True)
class GitHubEvent:
    event: str
    action: str
    repo: str
    number: int | None
    title: str
    url: str
    summary: str
    details: str
    severity: str

    def to_dict(self) -> dict[str, object]:
        return {
            "event": self.event,
            "action": self.action,
            "repo": self.repo,
            "number": self.number,
            "title": self.title,
            "url": self.url,
            "summary": self.summary,
            "details": self.details,
            "severity": self.severity,
        }


def log_line(msg: str) -> None:
    stamp = utc_timestamp()
    with open(LOGFILE, "a", encoding="utf-8") as fh:
        fh.write(f"{stamp} {msg}\n")


def utc_timestamp() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


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


def verify_signature(body: bytes, signature_256: str | None, signature_1: str | None) -> bool:
    if not WEBHOOK_SHARED_SECRET:
        return False
    candidate = (signature_256 or signature_1 or "").strip()
    if not candidate:
        return False
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


def repo_is_allowed(repo: str) -> bool:
    return not ALLOWED_REPOSITORIES or repo.casefold() in ALLOWED_REPOSITORIES


def _claude_executable() -> str:
    candidates = [
        Path.home() / ".local" / "bin" / "claude",
        Path.home() / ".local" / "bin" / "claude.exe",
    ]
    for candidate in candidates:
        if candidate.is_file():
            return str(candidate)
    candidate = shutil.which("claude") or shutil.which("claude.exe")
    return candidate or "claude"


def _label_names(source: object) -> list[str]:
    labels: list[str] = []
    if not isinstance(source, list):
        return labels
    for item in source:
        if not isinstance(item, dict):
            continue
        name = str(item.get("name") or "").strip()
        if name:
            labels.append(name)
    return labels


def _payload_labels(payload: dict[str, object], object_key: str) -> list[str]:
    labels: list[str] = []
    obj = payload.get(object_key) if isinstance(payload.get(object_key), dict) else {}
    labels.extend(_label_names(obj.get("labels")))
    labels.extend(_label_names(payload.get("labels")))
    label = payload.get("label") if isinstance(payload.get("label"), dict) else {}
    label_name = str(label.get("name") or "").strip()
    if label_name:
        labels.append(label_name)
    deduped: list[str] = []
    seen: set[str] = set()
    for name in labels:
        key = name.casefold()
        if key in seen:
            continue
        seen.add(key)
        deduped.append(name)
    return deduped


def _has_label(payload: dict[str, object], object_key: str, wanted_label: str) -> bool:
    wanted = wanted_label.casefold()
    return any(label.casefold() == wanted for label in _payload_labels(payload, object_key))


def _sender_login(payload: dict[str, object]) -> str:
    sender = payload.get("sender") if isinstance(payload.get("sender"), dict) else {}
    return str(sender.get("login") or "").strip()


def _is_authorized_sdlc_labeler(login: str) -> bool:
    return bool(login and login.casefold() in SDLC_AUTHORIZED_LABELERS)


def _trim_text(value: object, max_chars: int) -> str:
    text = str(value or "").strip()
    if len(text) <= max_chars:
        return text
    return text[: max_chars - 3] + "..."


def _github_object_context(
    event_name: str,
    payload: dict[str, object],
) -> tuple[str, dict[str, object]] | None:
    repository = payload.get("repository") if isinstance(payload.get("repository"), dict) else {}
    repo = _repo_full_name(repository)
    if not repo_is_allowed(repo):
        return None

    if event_name == "issues":
        issue = payload.get("issue") if isinstance(payload.get("issue"), dict) else {}
        number = _int(issue.get("number"))
        title = str(issue.get("title") or "").strip()
        return "issue", {
            "kind": "issue",
            "event": event_name,
            "action": str(payload.get("action") or "").strip().casefold(),
            "repo": repo,
            "number": number,
            "title": title,
            "body": _trim_text(issue.get("body"), 4000),
            "state": str(issue.get("state") or "").strip().casefold(),
            "html_url": str(issue.get("html_url") or "").strip(),
            "labels": _payload_labels(payload, "issue"),
            "author": str((issue.get("user") or {}).get("login") if isinstance(issue.get("user"), dict) else "").strip(),
        }

    if event_name == "pull_request":
        pr = payload.get("pull_request") if isinstance(payload.get("pull_request"), dict) else {}
        number = _int(pr.get("number"))
        title = str(pr.get("title") or "").strip()
        base = pr.get("base") if isinstance(pr.get("base"), dict) else {}
        head = pr.get("head") if isinstance(pr.get("head"), dict) else {}
        return "pull_request", {
            "kind": "pull_request",
            "event": event_name,
            "action": str(payload.get("action") or "").strip().casefold(),
            "repo": repo,
            "number": number,
            "title": title,
            "body": _trim_text(pr.get("body"), 4000),
            "state": str(pr.get("state") or "").strip().casefold(),
            "html_url": str(pr.get("html_url") or "").strip(),
            "labels": _payload_labels(payload, "pull_request"),
            "author": str((pr.get("user") or {}).get("login") if isinstance(pr.get("user"), dict) else "").strip(),
            "merged": bool(pr.get("merged")),
            "draft": bool(pr.get("draft")),
            "base_ref": str(base.get("ref") or "").strip(),
            "head_ref": str(head.get("ref") or "").strip(),
        }

    return None


def _build_wos_review_prompt(context: dict[str, object]) -> str:
    object_json = json.dumps(context, ensure_ascii=False, indent=2, sort_keys=True)
    prompt = (
        "You are Claude acting as the WOS delegation bridge for OpenClaw.\n"
        f"The GitHub object was labeled `{WOS_REVIEW_LABEL}`.\n"
        "Your job is to take the object definition below and delegate the work to WOS, "
        "the local orchestration layer that routes work to the right agent.\n"
        "Return a concise delegation plan, including the exact WOS-facing next action if one is obvious.\n"
        "Do not mention API keys, webhook internals, or implementation details.\n\n"
        "Object definition:\n"
        f"{object_json}"
    )
    if len(prompt) <= WOS_REVIEW_PROMPT_MAX_CHARS:
        return prompt
    return (
        prompt[: max(WOS_REVIEW_PROMPT_MAX_CHARS - 3, 0)]
        + "..."
        if WOS_REVIEW_PROMPT_MAX_CHARS > 3
        else prompt[:WOS_REVIEW_PROMPT_MAX_CHARS]
    )


def _render_wos_review_command(prompt: str, context: dict[str, object]) -> list[str]:
    rendered = WOS_REVIEW_COMMAND.format(
        claude=_quoted_env(_claude_executable()),
        prompt=_quoted_env(prompt),
        prompt_json=_quoted_env(json.dumps(context, ensure_ascii=False, sort_keys=True)),
        repo=_quoted_env(str(context.get("repo") or "")),
        kind=_quoted_env(str(context.get("kind") or "")),
        event=_quoted_env(str(context.get("event") or "")),
        action=_quoted_env(str(context.get("action") or "")),
        number=_quoted_env(str(context.get("number") or "")),
        title=_quoted_env(str(context.get("title") or "")),
        url=_quoted_env(str(context.get("html_url") or "")),
        label=_quoted_env(WOS_REVIEW_LABEL),
    )
    return shlex.split(rendered)


def _start_detached_process(command: list[str], env: dict[str, str], *, label: str) -> None:
    try:
        with open(LOGFILE, "a", encoding="utf-8") as log_fh:
            subprocess.Popen(
                command,
                stdout=log_fh,
                stderr=log_fh,
                stdin=subprocess.DEVNULL,
                env=env,
                close_fds=True,
                start_new_session=True,
                text=True,
            )
        log_line(f"started detached {label} command={shlex.join(command)}")
    except Exception as exc:  # noqa: BLE001
        log_line(f"failed to start detached {label} command={shlex.join(command)} error={exc}")


def run_wos_review_hook(event_name: str, payload: dict[str, object]) -> None:
    object_kind, context = _github_object_context(event_name, payload) or (None, None)
    if not context:
        return

    action = str(context.get("action") or "")
    observed_labels = _payload_labels(payload, "issue" if object_kind == "issue" else "pull_request")
    has_review_label = any(label.casefold() == WOS_REVIEW_LABEL.casefold() for label in observed_labels)
    log_line(
        "wos review eval "
        f"kind={context.get('kind')} action={action} repo={context.get('repo')} "
        f"number={context.get('number') or 'n/a'} labels={observed_labels} "
        f"wanted={WOS_REVIEW_LABEL}"
    )
    log_line(
        "wos review decision "
        f"kind={context.get('kind')} action={action!r} has_review_label={has_review_label} "
        f"action_is_labeled={action == 'labeled'}"
    )
    triggered = False
    trigger_reason = ""
    if action == "labeled" and has_review_label:
        triggered = True
        trigger_reason = "label-added"
    elif action in {"opened", "reopened"} and has_review_label:
        triggered = True
        trigger_reason = f"label-present-on-{action}"

    if not triggered:
        return

    prompt = _build_wos_review_prompt(context)
    command = _render_wos_review_command(prompt, context)
    log_line(
        "wos review trigger "
        f"kind={context.get('kind')} action={action} repo={context.get('repo')} "
        f"number={context.get('number') or 'n/a'} reason={trigger_reason} label={WOS_REVIEW_LABEL}"
    )
    env = os.environ.copy()
    env["OPENCLAW_WOS_REVIEW_EVENT_JSON"] = json.dumps(context, ensure_ascii=False)
    env["OPENCLAW_WOS_REVIEW_PROMPT"] = prompt
    env["OPENCLAW_WOS_REVIEW_TRIGGER_REASON"] = trigger_reason
    env["OPENCLAW_WOS_REVIEW_LABEL"] = WOS_REVIEW_LABEL
    env["OPENCLAW_WOS_REVIEW_KIND"] = str(context.get("kind") or "")
    env["OPENCLAW_WOS_REVIEW_REPO"] = str(context.get("repo") or "")
    env["OPENCLAW_WOS_REVIEW_NUMBER"] = str(context.get("number") or "")
    env["OPENCLAW_WOS_REVIEW_TITLE"] = str(context.get("title") or "")
    env["OPENCLAW_WOS_REVIEW_URL"] = str(context.get("html_url") or "")

    synthetic_event = GitHubEvent(
        event="wos_review",
        action=action or "labeled",
        repo=str(context.get("repo") or "unknown/unknown"),
        number=_int(context.get("number")),
        title=str(context.get("title") or ""),
        url=str(context.get("html_url") or ""),
        summary=(
            f"WOS review trigger for {context.get('kind')} #{context.get('number') or '?'} "
            f"in {context.get('repo')}: {context.get('title') or '(no title)'}"
        ),
        details=f"label={WOS_REVIEW_LABEL} reason={trigger_reason} url={context.get('html_url') or 'n/a'}",
        severity="INFO",
    )

    run_alert(WOS_REVIEW_DISCORD_TARGET, synthetic_event)
    _start_detached_process(command, env, label="wos-review")


def classify_event(event_name: str, payload: dict[str, object]) -> GitHubEvent | None:
    action = str(payload.get("action") or "").strip().casefold()

    if event_name == "issues":
        issue = payload.get("issue") if isinstance(payload.get("issue"), dict) else {}
        repository = payload.get("repository") if isinstance(payload.get("repository"), dict) else {}
        repo = _repo_full_name(repository)
        if not repo_is_allowed(repo):
            return None
        number = _int(issue.get("number"))
        title = str(issue.get("title") or "").strip()
        url = str(issue.get("html_url") or "").strip()
        if action == "opened":
            summary = f"Issue created #{number or '?'} in {repo}: {title or '(no title)'}"
            details = f"action=opened url={url or 'n/a'}"
            return GitHubEvent(event_name, action, repo, number, title, url, summary, details, "INFO")
        if action == "closed":
            summary = f"Issue closed #{number or '?'} in {repo}: {title or '(no title)'}"
            details = f"action=closed url={url or 'n/a'}"
            return GitHubEvent(event_name, action, repo, number, title, url, summary, details, "RECOVERY")
        if action == "reopened":
            summary = f"Issue reopened #{number or '?'} in {repo}: {title or '(no title)'}"
            details = f"action=reopened url={url or 'n/a'}"
            return GitHubEvent(event_name, action, repo, number, title, url, summary, details, "WARN")
        return None

    if event_name == "pull_request":
        pr = payload.get("pull_request") if isinstance(payload.get("pull_request"), dict) else {}
        repository = payload.get("repository") if isinstance(payload.get("repository"), dict) else {}
        repo = _repo_full_name(repository)
        if not repo_is_allowed(repo):
            return None
        number = _int(pr.get("number"))
        title = str(pr.get("title") or "").strip()
        url = str(pr.get("html_url") or "").strip()
        merged = bool(pr.get("merged"))
        base = pr.get("base") if isinstance(pr.get("base"), dict) else {}
        base_ref = str(base.get("ref") or "").strip().casefold()
        if action == "opened":
            summary = f"PR created #{number or '?'} in {repo}: {title or '(no title)'}"
            details = f"action=opened url={url or 'n/a'}"
            return GitHubEvent(event_name, action, repo, number, title, url, summary, details, "INFO")
        if action == "reopened":
            summary = f"PR reopened #{number or '?'} in {repo}: {title or '(no title)'}"
            details = f"action=reopened url={url or 'n/a'}"
            return GitHubEvent(event_name, action, repo, number, title, url, summary, details, "WARN")
        if action == "closed" and merged and (not TARGET_BRANCH or base_ref == TARGET_BRANCH):
            summary = f"PR merged into {TARGET_BRANCH} #{number or '?'} in {repo}: {title or '(no title)'}"
            details = f"action=closed merged=true base={base_ref or 'n/a'} url={url or 'n/a'}"
            return GitHubEvent(event_name, action, repo, number, title, url, summary, details, "RECOVERY")
        if action == "closed" and not merged:
            summary = f"PR closed #{number or '?'} in {repo}: {title or '(no title)'}"
            details = f"action=closed merged=false base={base_ref or 'n/a'} url={url or 'n/a'}"
            return GitHubEvent(event_name, action, repo, number, title, url, summary, details, "WARN")
        return None

    return None


def _repo_full_name(repository: dict[str, object]) -> str:
    full_name = str(repository.get("full_name") or "").strip()
    if full_name:
        return full_name
    owner = repository.get("owner") if isinstance(repository.get("owner"), dict) else {}
    owner_login = str(owner.get("login") or "").strip()
    name = str(repository.get("name") or "").strip()
    if owner_login and name:
        return f"{owner_login}/{name}"
    return name or "unknown/unknown"


def _int(value: object) -> int | None:
    try:
        return int(value) if value is not None else None
    except (TypeError, ValueError):
        return None


def _quoted_env(value: str) -> str:
    return shlex.quote(value)


def run_alert(target: str, event: GitHubEvent) -> None:
    if not ALERT_SCRIPT:
        return
    try:
        subprocess.run(
            [
                ALERT_SCRIPT,
                target,
                event.severity,
                f"GitHub {event.event}",
                event.summary,
                event.details,
            ],
            check=False,
            capture_output=True,
            text=True,
            timeout=ALERT_TIMEOUT_SECONDS,
        )
    except subprocess.TimeoutExpired:
        log_line(
            f"alert script timeout target={target} event={event.event} "
            f"repo={event.repo} timeout={ALERT_TIMEOUT_SECONDS}s"
        )
    except Exception as exc:  # noqa: BLE001
        log_line(f"alert script failed target={target} event={event.event} repo={event.repo} error={exc}")


def run_openclaw_hook(event: GitHubEvent, payload: dict[str, object]) -> None:
    if not OPENCLAW_MONITOR_COMMAND:
        return

    env = os.environ.copy()
    env["OPENCLAW_GITHUB_EVENT_JSON"] = json.dumps(payload, ensure_ascii=False)
    env["OPENCLAW_GITHUB_EVENT"] = event.event
    env["OPENCLAW_GITHUB_ACTION"] = event.action
    env["OPENCLAW_GITHUB_REPO"] = event.repo
    env["OPENCLAW_GITHUB_NUMBER"] = str(event.number or "")
    env["OPENCLAW_GITHUB_TITLE"] = event.title
    env["OPENCLAW_GITHUB_URL"] = event.url
    env["OPENCLAW_GITHUB_SUMMARY"] = event.summary
    env["OPENCLAW_GITHUB_DETAILS"] = event.details
    env["OPENCLAW_GITHUB_SEVERITY"] = event.severity

    rendered = OPENCLAW_MONITOR_COMMAND.format(
        event=_quoted_env(event.event),
        action=_quoted_env(event.action),
        repo=_quoted_env(event.repo),
        number=_quoted_env(str(event.number or "")),
        title=_quoted_env(event.title),
        url=_quoted_env(event.url),
        summary=_quoted_env(event.summary),
        details=_quoted_env(event.details),
    )
    command = shlex.split(rendered)
    try:
        completed = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            env=env,
            timeout=OPENCLAW_HOOK_TIMEOUT_SECONDS,
        )
        stdout = (completed.stdout or "").strip()
        stderr = (completed.stderr or "").strip()
        log_line(
            f"openclaw hook event={event.event} action={event.action} repo={event.repo} "
            f"exit={completed.returncode} stdout={stdout} stderr={stderr}"
        )
    except subprocess.TimeoutExpired:
        log_line(
            f"openclaw hook timeout event={event.event} repo={event.repo} "
            f"timeout={OPENCLAW_HOOK_TIMEOUT_SECONDS}s"
        )
    except Exception as exc:  # noqa: BLE001
        log_line(f"openclaw hook failed event={event.event} repo={event.repo} error={exc}")


def _gh_command() -> str:
    return shutil.which("gh") or str(Path.home() / ".local" / "bin" / "gh")


def _run_gh_issue_edit(repo: str, issue: int, *args: str) -> tuple[int, str, str]:
    command = [_gh_command(), "issue", "edit", str(issue), "--repo", repo, *args]
    try:
        completed = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            timeout=SDLC_HOOK_TIMEOUT_SECONDS,
            env=os.environ.copy(),
        )
    except subprocess.TimeoutExpired:
        return 124, "", f"gh issue edit timed out after {SDLC_HOOK_TIMEOUT_SECONDS}s"
    except OSError as exc:
        return 127, "", str(exc)
    return completed.returncode, (completed.stdout or "").strip(), (completed.stderr or "").strip()


def _gh_add_label(repo: str, issue: int, label: str) -> bool:
    rc, _, stderr = _run_gh_issue_edit(repo, issue, "--add-label", label)
    if rc != 0:
        log_line(f"sdlc label add failed issue={issue} label={label} exit={rc} stderr={stderr}")
        return False
    return True


def _gh_remove_label(repo: str, issue: int, label: str) -> None:
    rc, _, stderr = _run_gh_issue_edit(repo, issue, "--remove-label", label)
    if rc != 0:
        log_line(f"sdlc label remove skipped issue={issue} label={label} exit={rc} stderr={stderr}")


def _run_gh_issue_comment(repo: str, issue: int, body: str) -> tuple[int, str, str]:
    command = [_gh_command(), "issue", "comment", str(issue), "--repo", repo, "--body", body]
    try:
        completed = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            timeout=SDLC_HOOK_TIMEOUT_SECONDS,
            env=os.environ.copy(),
        )
    except subprocess.TimeoutExpired:
        return 124, "", f"gh issue comment timed out after {SDLC_HOOK_TIMEOUT_SECONDS}s"
    except OSError as exc:
        return 127, "", str(exc)
    return completed.returncode, (completed.stdout or "").strip(), (completed.stderr or "").strip()


def _repo_for_write(payload: dict[str, object]) -> str:
    repository = payload.get("repository") if isinstance(payload.get("repository"), dict) else {}
    repo = _repo_full_name(repository)
    return repo if repo_is_allowed(repo) else ""


def _admit_sdlc_issue(issue_number: int, labeler: str, payload: dict[str, object]) -> bool:
    repo = _repo_for_write(payload)
    if not repo:
        log_line(f"sdlc admission failed issue={issue_number} reason=repo-not-allowed")
        return False

    if not _gh_add_label(repo, issue_number, SDLC_QUEUE_LABEL):
        log_line(f"sdlc admission failed issue={issue_number} labeler={labeler}")
        return False
    _gh_remove_label(repo, issue_number, SDLC_REJECTED_LABEL)
    _gh_remove_label(repo, issue_number, SDLC_UNAUTHORIZED_LABEL)

    _run_gh_issue_comment(
        repo,
        issue_number,
        f"AI SDLC admission accepted by `{labeler}`. The issue is now queued with `{SDLC_QUEUE_LABEL}`.",
    )
    log_line(f"sdlc admission accepted issue={issue_number} labeler={labeler} queue_label={SDLC_QUEUE_LABEL}")
    return True


def _reject_sdlc_issue(issue_number: int, labeler: str, payload: dict[str, object]) -> None:
    repo = _repo_for_write(payload)
    if not repo:
        log_line(f"sdlc rejection skipped issue={issue_number} reason=repo-not-allowed labeler={labeler or 'unknown'}")
        return

    _gh_remove_label(repo, issue_number, SDLC_HOOK_LABEL)
    _gh_remove_label(repo, issue_number, SDLC_QUEUE_LABEL)
    if not _gh_add_label(repo, issue_number, SDLC_REJECTED_LABEL):
        return
    if not _gh_add_label(repo, issue_number, SDLC_UNAUTHORIZED_LABEL):
        return

    _run_gh_issue_comment(
        repo,
        issue_number,
        (
            f"AI SDLC admission rejected: `{labeler or 'unknown'}` is not authorized to add "
            f"`{SDLC_HOOK_LABEL}`. Authorized labelers: {', '.join(sorted(SDLC_AUTHORIZED_LABELERS)) or 'none'}."
        ),
    )
    log_line(f"sdlc admission rejected issue={issue_number} labeler={labeler or 'unknown'}")


def _sdlc_event_command_name(event_name: str, payload: dict[str, object]) -> str:
    action = str(payload.get("action") or "").strip().casefold()

    if event_name == "issues":
        if action == "opened":
            return "issue-opened"
        if action in {"edited", "reopened"}:
            return "issue-opened"
        if action == "labeled":
            return "label-admission"

    if event_name == "issue_comment" and action == "created":
        return "issue-commented"

    if event_name == "pull_request":
        mapping = {
            "opened": "pr-opened",
            "reopened": "pr-reopened",
            "ready_for_review": "pr-ready-for-review",
            "synchronize": "pr-synchronized",
            "closed": "pr-closed",
        }
        return mapping.get(action, "")

    if event_name == "pull_request_review" and action == "submitted":
        return "pr-review-submitted"

    if event_name == "pull_request_review_comment" and action == "created":
        return "pr-commented"

    if event_name in {"check_suite", "check_run", "status"}:
        return "checks-completed"

    return ""


def _should_trigger_sdlc_hook(event_name: str, payload: dict[str, object]) -> tuple[bool, str, str]:
    if not SDLC_HOOK_ENABLED:
        return False, "disabled", ""
    if not SDLC_HOOK_COMMAND and not SDLC_EVENT_COMMAND:
        return False, "missing-command", ""

    event_command = _sdlc_event_command_name(event_name, payload)
    if event_command and event_command != "label-admission":
        if not SDLC_EVENT_COMMAND:
            return False, f"missing-event-command:{event_command}", ""
        return True, f"event-command:{event_command}", event_command
    if event_name != "issues":
        return False, "not-sdlc-event", ""

    action = str(payload.get("action") or "").strip().casefold()
    if action != "labeled":
        return False, f"ignored-action:{action or 'unknown'}", ""

    repository = payload.get("repository") if isinstance(payload.get("repository"), dict) else {}
    repo = _repo_full_name(repository)
    if not repo_is_allowed(repo):
        return False, "repo-not-allowed", ""

    issue = payload.get("issue") if isinstance(payload.get("issue"), dict) else {}
    number = _int(issue.get("number"))
    state = str(issue.get("state") or "").strip().casefold()
    if state != "open":
        return False, f"issue-not-open:{number or 'unknown'}", ""
    if not number:
        return False, "missing-issue-number", ""

    labels = _payload_labels(payload, "issue")
    has_trigger = any(label.casefold() == SDLC_HOOK_LABEL.casefold() for label in labels)
    event_label = ""
    label = payload.get("label") if isinstance(payload.get("label"), dict) else {}
    if isinstance(label, dict):
        event_label = str(label.get("name") or "").strip()
    if action == "labeled" and event_label.casefold() != SDLC_HOOK_LABEL.casefold():
        return False, f"other-label:{event_label or 'unknown'}", ""
    if not has_trigger:
        return False, "missing-trigger-label", ""

    has_accepted = any(label.casefold() == SDLC_ACCEPTED_LABEL.casefold() for label in labels)
    if not has_accepted:
        _gh_add_label(repo, number, SDLC_ADMISSION_REVIEW_LABEL)
        _run_gh_issue_comment(
            repo,
            number,
            (
                f"AI SDLC admission is waiting for initial acceptance review. "
                f"`{SDLC_HOOK_LABEL}` requires `{SDLC_ACCEPTED_LABEL}` before the issue can enter `{SDLC_QUEUE_LABEL}`."
            ),
        )
        return False, f"missing-accepted-label:{SDLC_ACCEPTED_LABEL}", ""

    labeler = _sender_login(payload)
    if not _is_authorized_sdlc_labeler(labeler):
        _reject_sdlc_issue(number, labeler, payload)
        return False, f"unauthorized-labeler:{labeler or 'unknown'}", ""

    if not _admit_sdlc_issue(number, labeler, payload):
        return False, "admission-failed", ""

    return True, f"issue:{number}:admitted-by:{labeler}", "reconcile"


def _render_sdlc_hook_command(payload_file: str, *, delivery: str, event_name: str, event_command: str) -> list[str]:
    template = SDLC_EVENT_COMMAND or SDLC_HOOK_COMMAND
    rendered = template.format(
        payload=_quoted_env(payload_file),
        delivery=_quoted_env(delivery),
        event=_quoted_env(event_name),
        command=_quoted_env(event_command),
        label=_quoted_env(SDLC_HOOK_LABEL),
        queue_label=_quoted_env(SDLC_QUEUE_LABEL),
        authorized_labelers=_quoted_env(",".join(sorted(SDLC_AUTHORIZED_LABELERS))),
    )
    return shlex.split(rendered)


def run_sdlc_hook(event_name: str, payload: dict[str, object], *, delivery: str) -> None:
    should_trigger, reason, event_command = _should_trigger_sdlc_hook(event_name, payload)
    if not should_trigger:
        log_line(f"sdlc hook skipped event={event_name} reason={reason}")
        return

    payload_file = ""
    try:
        with tempfile.NamedTemporaryFile(
            "w",
            encoding="utf-8",
            prefix="homedir-sdlc-github-",
            suffix=".json",
            delete=False,
        ) as fh:
            payload_file = fh.name
            json.dump(payload, fh, ensure_ascii=False)
            fh.write("\n")
        os.chmod(payload_file, 0o600)

        command = _render_sdlc_hook_command(
            payload_file,
            delivery=delivery,
            event_name=event_name,
            event_command=event_command,
        )
        completed = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            timeout=SDLC_HOOK_TIMEOUT_SECONDS,
            env=os.environ.copy(),
        )
        stdout = (completed.stdout or "").strip()
        stderr = (completed.stderr or "").strip()
        log_line(
            f"sdlc hook triggered event={event_name} reason={reason} "
            f"exit={completed.returncode} stdout={stdout} stderr={stderr}"
        )
    except subprocess.TimeoutExpired:
        log_line(f"sdlc hook timeout event={event_name} reason={reason} timeout={SDLC_HOOK_TIMEOUT_SECONDS}s")
    except Exception as exc:  # noqa: BLE001
        log_line(f"sdlc hook failed event={event_name} reason={reason} error={exc}")
    finally:
        if payload_file:
            try:
                os.unlink(payload_file)
            except OSError:
                pass


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        return

    def do_GET(self):
        if not is_status_authorized(self):
            self.send_response(403)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.end_headers()
            self.wfile.write(b"forbidden\n")
            log_line(f"status denied remote={safe_remote(self)}")
            return

        payload = {
            "service": "homedir-github-webhook",
            "timestamp_utc": utc_timestamp(),
            "discord_target": DISCORD_TARGET,
            "openclaw_hook_enabled": bool(OPENCLAW_MONITOR_COMMAND),
            "sdlc_hook_enabled": SDLC_HOOK_ENABLED and bool(SDLC_HOOK_COMMAND),
            "sdlc_hook_label": SDLC_HOOK_LABEL,
            "sdlc_queue_label": SDLC_QUEUE_LABEL,
            "sdlc_rejected_label": SDLC_REJECTED_LABEL,
            "sdlc_admission_review_label": SDLC_ADMISSION_REVIEW_LABEL,
            "sdlc_accepted_label": SDLC_ACCEPTED_LABEL,
            "sdlc_authorized_labelers": sorted(SDLC_AUTHORIZED_LABELERS),
            "wos_review_hook_enabled": True,
            "wos_review_label": WOS_REVIEW_LABEL,
            "wos_review_discord_target": WOS_REVIEW_DISCORD_TARGET,
            "signature_required": WEBHOOK_REQUIRE_SIGNATURE,
            "allowed_repositories": sorted(ALLOWED_REPOSITORIES),
            "target_branch": TARGET_BRANCH,
            "max_body_bytes": MAX_BODY_BYTES,
        }

        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.end_headers()
        self.wfile.write((json.dumps(payload) + "\n").encode("utf-8"))
        log_line(f"status served remote={safe_remote(self)}")

    def do_POST(self):
        raw_length = (self.headers.get("content-length") or "").strip()
        try:
            length = int(raw_length)
        except ValueError:
            self.send_response(400)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"invalid content-length\n")
            log_line(f"rejected invalid content-length remote={safe_remote(self)} value={raw_length!r}")
            return

        if length <= 0:
            self.send_response(400)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"empty payload\n")
            log_line(f"rejected empty payload remote={safe_remote(self)} bytes={length}")
            return

        if length > MAX_BODY_BYTES:
            self.send_response(413)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"payload too large\n")
            log_line(f"rejected payload too large remote={safe_remote(self)} bytes={length}")
            return

        body = self.rfile.read(length)
        signature_valid = verify_signature(
            body,
            self.headers.get("X-Hub-Signature-256"),
            self.headers.get("X-Hub-Signature"),
        )
        if WEBHOOK_REQUIRE_SIGNATURE and not signature_valid:
            self.send_response(401)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"invalid signature\n")
            log_line(f"rejected invalid signature remote={safe_remote(self)} bytes={len(body)}")
            return

        event_name = (self.headers.get("X-GitHub-Event") or "").strip().casefold()
        delivery = (self.headers.get("X-GitHub-Delivery") or "").strip()
        if not event_name:
            self.send_response(400)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"missing event header\n")
            log_line(f"rejected missing event header remote={safe_remote(self)}")
            return

        try:
            payload = json.loads(body)
        except Exception as exc:  # noqa: BLE001
            self.send_response(400)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"invalid json\n")
            log_line(f"rejected invalid json remote={safe_remote(self)} error={exc}")
            return

        event = classify_event(event_name, payload if isinstance(payload, dict) else {})
        log_line(
            f"received remote={safe_remote(self)} event={event_name} delivery={delivery} "
            f"signature_valid={signature_valid} handled={bool(event)}"
        )

        if isinstance(payload, dict):
            run_wos_review_hook(event_name, payload)
            run_sdlc_hook(event_name, payload, delivery=delivery)

        if event is not None:
            run_alert(DISCORD_TARGET, event)
            run_openclaw_hook(event, payload if isinstance(payload, dict) else {})

        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        self.end_headers()
        self.wfile.write(b"ok\n")


def main():
    if WEBHOOK_REQUIRE_SIGNATURE and not WEBHOOK_SHARED_SECRET:
        log_line("WARNING: signature required but GITHUB_WEBHOOK_SHARED_SECRET is not set")
    if not WEBHOOK_STATUS_TOKEN:
        log_line("WARNING: GITHUB_WEBHOOK_STATUS_TOKEN is not set; GET status endpoint is disabled")
    server = ThreadingHTTPServer((BIND_ADDRESS, PORT), Handler)
    log_line(f"github webhook server starting on {BIND_ADDRESS}:{PORT}")
    server.serve_forever()


if __name__ == "__main__":
    main()
