#!/usr/bin/env python3
"""Local monitoring panel for a Lavalink instance (Python standard library only)."""

from __future__ import annotations

import json
import os
import re
import select
import socket
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent.parent
LOG_PATH = Path(os.environ.get("LAVALINK_LOG_FILE", ROOT / "logs" / "lavalink.log"))
HISTORY_PATH = ROOT / "logs" / "connections.jsonl"
HOST = os.environ.get("PANEL_HOST", "127.0.0.1")
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
    protocol_version = "HTTP/1.1"

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
        elif self.path.startswith(("/v3/", "/v4/")) or self.path == "/version":
            self.proxy_lavalink()
            return
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.close_connection = True
        self.wfile.write(body)

    def do_POST(self):
        self.proxy_lavalink()

    def do_PATCH(self):
        self.proxy_lavalink()

    def do_DELETE(self):
        self.proxy_lavalink()

    def do_PUT(self):
        self.proxy_lavalink()

    def proxy_lavalink(self):
        if not (self.path.startswith(("/v3/", "/v4/")) or self.path == "/version"):
            self.send_error(404)
            return
        host, port, _password = lavalink_config()
        if self.headers.get("Upgrade", "").lower() == "websocket":
            self.proxy_websocket(host, port)
            return
        length = int(self.headers.get("Content-Length", "0"))
        payload = self.rfile.read(length) if length else None
        headers = {key: value for key, value in self.headers.items()
                   if key.lower() not in {"host", "connection", "content-length"}}
        request = Request(f"http://{host}:{port}{self.path}", data=payload,
                          headers=headers, method=self.command)
        try:
            with urlopen(request, timeout=60) as response:
                body = response.read()
                self.send_response(response.status)
                for key, value in response.headers.items():
                    if key.lower() not in {"connection", "transfer-encoding", "content-length"}:
                        self.send_header(key, value)
                self.send_header("Content-Length", str(len(body)))
                self.send_header("Connection", "close")
                self.end_headers()
                self.wfile.write(body)
        except HTTPError as error:
            body = error.read()
            self.send_response(error.code)
            for key, value in error.headers.items():
                if key.lower() not in {"connection", "transfer-encoding", "content-length"}:
                    self.send_header(key, value)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except (URLError, OSError, TimeoutError) as error:
            self.send_error(502, f"Lavalink indisponível: {error}")

    def proxy_websocket(self, host, port):
        try:
            upstream = socket.create_connection((host, port), timeout=10)
            upstream.settimeout(None)
            headers = [f"{self.command} {self.path} {self.request_version}\r\n"]
            headers.extend(f"{key}: {value}\r\n" for key, value in self.headers.items()
                           if key.lower() != "host")
            headers.append(f"Host: {host}:{port}\r\n\r\n")
            upstream.sendall("".join(headers).encode("iso-8859-1"))
            response = bytearray()
            while not response.endswith(b"\r\n\r\n"):
                chunk = upstream.recv(1)
                if not chunk:
                    raise OSError("Lavalink encerrou a conexão WebSocket")
                response.extend(chunk)
                if len(response) > 65536:
                    raise OSError("Cabeçalho WebSocket muito grande")
            self.connection.sendall(response)
            self.close_connection = True
            sockets = [self.connection, upstream]
            while True:
                readable, _, exceptional = select.select(sockets, [], sockets)
                if exceptional:
                    break
                for source in readable:
                    data = source.recv(65536)
                    if not data:
                        return
                    destination = upstream if source is self.connection else self.connection
                    destination.sendall(data)
        except (OSError, TimeoutError) as error:
            if not self.wfile.closed:
                self.send_error(502, f"Falha no WebSocket do Lavalink: {error}")
        finally:
            try:
                upstream.close()
            except (UnboundLocalError, OSError):
                pass

    def do_OPTIONS(self):
        self.proxy_lavalink()

    def log_message(self, *_args):
        pass


if __name__ == "__main__":
    print(f"Painel King-Lavalink em http://{HOST}:{PORT}")
    print(f"Logs: {LOG_PATH}")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
