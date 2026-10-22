#!/usr/bin/env python3
"""King Lavalink dashboard and reverse proxy for the Lavalink v4 API."""

from __future__ import annotations

import http.client
import json
import os
import re
import select
import socket
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent.parent
LOG_PATH = Path(os.environ.get("LAVALINK_LOG_FILE", ROOT / "logs" / "lavalink.log"))
HISTORY_PATH = ROOT / "logs" / "connections.jsonl"
HOST = os.environ.get("PANEL_HOST", "0.0.0.0")
PORT = int(os.environ.get("PANEL_PORT", "80"))
PROXY_HOST = os.environ.get("LAVALINK_HOST", "127.0.0.1")
PROXY_PORT = int(os.environ.get("LAVALINK_PORT", "2333"))
HOP_BY_HOP = {
    "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
    "te", "trailers", "transfer-encoding", "upgrade",
}


def lavalink_config():
    password = os.environ.get("LAVALINK_SERVER_PASSWORD") or os.environ.get("LAVALINK_PASSWORD")
    config = ROOT / "application.yml"
    if not password and config.exists():
        text = config.read_text(encoding="utf-8-sig")
        match = re.search(r"(?m)^\s+password:\s*[\"']?([^\"'\s#]+)", text)
        password = match.group(1) if match else None
    return PROXY_HOST, PROXY_PORT, password


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
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        route = urlsplit(self.path).path
        if route == "/":
            self._respond(200, PAGE.encode("utf-8"), "text/html; charset=utf-8")
        elif route == "/api/status":
            result = snapshot()
            record_connection(result)
            self._respond(200, json.dumps(result).encode("utf-8"), "application/json; charset=utf-8")
        elif route == "/api/logs":
            body = json.dumps([line.rstrip("\r\n") for line in tail(LOG_PATH)]).encode("utf-8")
            self._respond(200, body, "application/json; charset=utf-8")
        elif route == "/api/history":
            rows = []
            for line in tail(HISTORY_PATH, 500):
                try:
                    rows.append(json.loads(line))
                except ValueError:
                    continue
            self._respond(200, json.dumps(rows).encode("utf-8"), "application/json; charset=utf-8")
        elif self.headers.get("Upgrade", "").lower() == "websocket":
            self._proxy_websocket()
        else:
            self._proxy_http()

    def do_POST(self):
        self._proxy_http()

    def do_PUT(self):
        self._proxy_http()

    def do_PATCH(self):
        self._proxy_http()

    def do_DELETE(self):
        self._proxy_http()

    def do_OPTIONS(self):
        self._proxy_http()

    def do_HEAD(self):
        self._proxy_http()

    def _respond(self, status, body, content_type):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def _proxy_http(self):
        connection = None
        try:
            connection = http.client.HTTPConnection(PROXY_HOST, PROXY_PORT, timeout=30)
            length = int(self.headers.get("Content-Length", "0") or "0")
            body = self.rfile.read(length) if length else None
            headers = {
                key: value for key, value in self.headers.items()
                if key.lower() not in HOP_BY_HOP and key.lower() != "host"
            }
            headers["Host"] = f"{PROXY_HOST}:{PROXY_PORT}"
            connection.request(self.command, self.path, body=body, headers=headers)
            response = connection.getresponse()
            self.send_response(response.status, response.reason)
            for key, value in response.getheaders():
                if key.lower() not in HOP_BY_HOP and key.lower() != "content-length":
                    self.send_header(key, value)
            payload = response.read()
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(payload)
        except (OSError, http.client.HTTPException) as error:
            try:
                body = json.dumps({"error": "Lavalink proxy unavailable", "detail": str(error)}).encode()
                self._respond(502, body, "application/json; charset=utf-8")
            except (OSError, BrokenPipeError):
                pass
        finally:
            if connection:
                connection.close()

    def _proxy_websocket(self):
        upstream = None
        response_started = False
        try:
            upstream = socket.create_connection((PROXY_HOST, PROXY_PORT), timeout=10)
            request_lines = [f"{self.command} {self.path} HTTP/1.1"]
            for key, value in self.headers.items():
                if key.lower() == "host":
                    value = f"{PROXY_HOST}:{PROXY_PORT}"
                request_lines.append(f"{key}: {value}")
            upstream.sendall(("\r\n".join(request_lines) + "\r\n\r\n").encode("latin-1"))
            upstream.settimeout(10)
            response = b""
            while b"\r\n\r\n" not in response and len(response) < 65536:
                chunk = upstream.recv(4096)
                if not chunk:
                    break
                response += chunk
            self.connection.sendall(response)
            response_started = True
            if b" 101 " not in response.split(b"\r\n", 1)[0]:
                return
            upstream.settimeout(None)
            self.connection.setblocking(False)
            upstream.setblocking(False)
            sockets = [self.connection, upstream]
            while True:
                readable, _, exceptional = select.select(sockets, [], sockets, 60)
                if exceptional or not readable:
                    break
                for source in readable:
                    target = upstream if source is self.connection else self.connection
                    try:
                        data = source.recv(65536)
                    except OSError:
                        return
                    if not data:
                        return
                    target.sendall(data)
        except OSError:
            if not response_started:
                try:
                    self._respond(502, b'{"error":"Lavalink WebSocket proxy unavailable"}', "application/json; charset=utf-8")
                except OSError:
                    pass
        finally:
            if upstream:
                upstream.close()
            self.close_connection = True

    def log_message(self, *_args):
        pass


if __name__ == "__main__":
    print(f"King Lavalink panel and proxy listening on {HOST}:{PORT}", flush=True)
    print(f"Proxy target: {PROXY_HOST}:{PROXY_PORT}", flush=True)
    print(f"Logs: {LOG_PATH}", flush=True)
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
