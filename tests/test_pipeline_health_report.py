import datetime as dt
import importlib.util
import io
import json
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request


def _load_module():
    script_path = Path(__file__).resolve().parents[1] / "scripts" / "ci" / "pipeline_health_report.py"
    spec = importlib.util.spec_from_file_location("pipeline_health_report", script_path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


pipeline_health_report = _load_module()


class _FakeResponse:
    def __init__(self, payload):
        self._payload = payload

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def read(self):
        return json.dumps(self._payload).encode("utf-8")


def test_api_get_json_retries_transient_http_error(monkeypatch):
    """Transient 5xx should be retried and eventually succeed."""

    calls = {"count": 0}
    sleeps = []

    def fake_urlopen(req, timeout=30):
        calls["count"] += 1
        if calls["count"] == 1:
            raise urllib.error.HTTPError(
                req.full_url,
                502,
                "Bad Gateway",
                {"Retry-After": "0"},
                io.BytesIO(b'{"message":"Server Error"}'),
            )
        return _FakeResponse({"ok": True})

    monkeypatch.setattr(pipeline_health_report.urllib.request, "urlopen", fake_urlopen)
    monkeypatch.setattr(pipeline_health_report.time, "sleep", lambda seconds: sleeps.append(seconds))

    payload = pipeline_health_report.api_get_json("https://example.test/api", "token")

    assert payload == {"ok": True}
    assert calls["count"] == 2
    assert len(sleeps) == 1


def test_fetch_recent_runs_degrades_on_later_page_failure(capsys, monkeypatch):
    """If a later page fails, the function should warn and keep collected runs."""

    created_at = (pipeline_health_report.utc_now() - dt.timedelta(days=1)).isoformat().replace("+00:00", "Z")
    calls = []

    def fake_api_get_json(url, token):
        calls.append(url)
        page = urllib.parse.parse_qs(urllib.parse.urlparse(url).query).get("page", [""])[0]
        if page == "1":
            return {"workflow_runs": [{"created_at": created_at, "id": 1}]}
        raise RuntimeError("GitHub API error 502")

    monkeypatch.setattr(pipeline_health_report, "api_get_json", fake_api_get_json)

    runs = pipeline_health_report.fetch_recent_runs("os-santiago/homedir", "pr-check.yml", "token", 14)
    captured = capsys.readouterr()

    assert len(runs) == 1
    assert runs[0]["id"] == 1
    assert any("page=2" in url for url in calls)
    assert "warning: unable to fetch workflow runs" in captured.err


def test_fetch_recent_runs_degrades_when_first_page_fails(capsys, monkeypatch):
    """If the first page fails after retries, return empty runs instead of crashing."""

    def fake_api_get_json(url, token):
        raise RuntimeError("GitHub API error 502")

    monkeypatch.setattr(pipeline_health_report, "api_get_json", fake_api_get_json)

    runs = pipeline_health_report.fetch_recent_runs("os-santiago/homedir", "release.yml", "token", 14)
    captured = capsys.readouterr()

    assert runs == []
    assert "warning: unable to fetch workflow runs" in captured.err
