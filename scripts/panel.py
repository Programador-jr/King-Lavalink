#!/usr/bin/env python3
"""Local monitoring panel for a Lavalink instance (Python standard library only)."""

from __future__ import annotations

import json
import os
import re
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent.parent
LOG_PATH = Path(os.environ.get("LAVALINK_LOG_FILE", ROOT / "logs" / "lavalink.log"))
HISTORY_PATH = ROOT / "logs" / "connections.jsonl"
HOST = "127.0.0.1"
PORT = int(os.environ.get("PANEL_PORT", "8080"))


def lavalink_config():
    host = os.environ.get("LAVALINK_HOST", "127.0.0.1")
    port = int(os.environ.get("LAVALINK_PORT", "2333"))
    password = os.environ.get("LAVALINK_SERVER_PASSWORD") or os.environ.get("LAVALINK_PASSWORD")
    config = ROOT / "application.yml"
    if not password and config.exists():
        text = config.read_text(encoding="utf-8-sig")
        match = re.search(r"(?m)^\s+password:\s*[\"']?([^\"'\s#]+)", text)
        password = match.group(1) if match else None
    return host, port, password


def snapshot():
    host, port, password = lavalink_config()
    result = {"checkedAt": datetime.now(timezone.utc).isoformat(), "connected": False}
    try:
        request = Request(
            f"http://{host}:{port}/v4/stats",
            headers={"Authorization": password or ""},
        )
        with urlopen(request, timeout=3) as response:
            stats = json.loads(response.read().decode("utf-8"))
        result.update({"connected": True, "stats": stats})
    except (HTTPError, URLError, TimeoutError, OSError, ValueError) as error:
        result["error"] = str(error)
    return result


def record_connection(result):
    if not result.get("connected"):
        return
    HISTORY_PATH.parent.mkdir(parents=True, exist_ok=True)
    stats = result.get("stats", {})
    entry = {
        "at": result["checkedAt"],
        "players": stats.get("players", 0),
        "playingPlayers": stats.get("playingPlayers", 0),
        "uptime": stats.get("uptime"),
    }
    with HISTORY_PATH.open("a", encoding="utf-8") as history:
        history.write(json.dumps(entry, ensure_ascii=False) + "\n")


def tail(path, limit=200):
    try:
        with path.open("r", encoding="utf-8", errors="replace") as handle:
            return handle.readlines()[-limit:]
    except OSError:
        return []


PAGE = (ROOT / "scripts" / "panel.html").read_text(encoding="utf-8")


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/":
            body, content_type = PAGE.encode("utf-8"), "text/html; charset=utf-8"
        elif self.path == "/api/status":
            result = snapshot()
            record_connection(result)
            body, content_type = json.dumps(result).encode("utf-8"), "application/json; charset=utf-8"
        elif self.path == "/api/logs":
            body = json.dumps([line.rstrip("\r\n") for line in tail(LOG_PATH)]).encode("utf-8")
            content_type = "application/json; charset=utf-8"
        elif self.path == "/api/history":
            rows = []
            for line in tail(HISTORY_PATH, 500):
                try:
                    rows.append(json.loads(line))
                except ValueError:
                    continue
            body, content_type = json.dumps(rows).encode("utf-8"), "application/json; charset=utf-8"
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_args):
        pass


if __name__ == "__main__":
    print(f"Painel de monitoramento local do King-Lavalink em http://{HOST}:{PORT}")
    print(f"Logs: {LOG_PATH}")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
