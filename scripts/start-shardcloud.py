#!/usr/bin/env python3
"""Start Lavalink privately and expose its API plus the dashboard on port 80."""

from __future__ import annotations

import os
import re
import signal
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = os.environ.get("JAVA", "java")
shutdown_requested = False


def request_shutdown(_signum, _frame) -> None:
    global shutdown_requested
    shutdown_requested = True


def main() -> int:
    signal.signal(signal.SIGTERM, request_shutdown)
    signal.signal(signal.SIGINT, request_shutdown)
    jar = ROOT / "Lavalink.jar"
    config = ROOT / "application.yml"
    if not jar.is_file() or not config.is_file():
        print("Lavalink.jar e application.yml precisam estar na raiz do pacote.", file=sys.stderr)
        return 1

    os.chdir(ROOT)
    (ROOT / "logs").mkdir(exist_ok=True)
    text = config.read_text(encoding="utf-8-sig")
    panel_config = re.search(r"(?ms)^panel:\s*\n((?:[ \t]+.*\n?)*)", text)
    panel_values = panel_config.group(1) if panel_config else ""
    panel_host = re.search(r"(?m)^\s+address:\s*([^\s#]+)", panel_values)
    panel_port = re.search(r"(?m)^\s+port:\s*(\d+)", panel_values)
    lavalink = subprocess.Popen([JAVA, "-jar", str(jar)], cwd=ROOT)
    os.environ["PANEL_HOST"] = panel_host.group(1) if panel_host else "0.0.0.0"
    os.environ["PANEL_PORT"] = panel_port.group(1) if panel_port else "80"
    os.environ["LAVALINK_HOST"] = "127.0.0.1"
    print("Lavalink iniciado na rede interna; painel e proxy ouvindo em 0.0.0.0:80", flush=True)

    panel = None
    try:
        panel = subprocess.Popen([sys.executable, str(ROOT / "scripts" / "panel.py")], cwd=ROOT)
        while panel.poll() is None:
            if shutdown_requested:
                return 0
            if lavalink.poll() is not None:
                print(f"Lavalink encerrou com código {lavalink.returncode}.", file=sys.stderr)
                panel.terminate()
                return lavalink.returncode or 1
            time.sleep(0.5)
        return panel.returncode
    finally:
        if panel is not None and panel.poll() is None:
            panel.terminate()
            try:
                panel.wait(timeout=5)
            except subprocess.TimeoutExpired:
                panel.kill()
        if lavalink.poll() is None:
            lavalink.terminate()
            try:
                lavalink.wait(timeout=10)
            except subprocess.TimeoutExpired:
                lavalink.kill()


if __name__ == "__main__":
    raise SystemExit(main())
