#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

command -v java >/dev/null 2>&1 || { echo "Java 17 or newer is required." >&2; exit 1; }
JAVA_VERSION="$(java -version 2>&1 | head -n 1)"
JAVA_MAJOR="$(printf '%s' "$JAVA_VERSION" | sed -E 's/.*version "([0-9]+).*/\1/')"
if ! [[ "$JAVA_MAJOR" =~ ^[0-9]+$ ]] || (( JAVA_MAJOR < 17 )); then
  echo "Java 17 or newer is required. Detected: $JAVA_VERSION" >&2
  exit 1
fi

[[ -f "$ROOT/Lavalink.jar" ]] || { echo "Lavalink.jar was not found." >&2; exit 1; }
[[ -f "$ROOT/KingLauncher.java" ]] || { echo "KingLauncher.java was not found." >&2; exit 1; }
[[ -f "$ROOT/scripts/panel.html" ]] || { echo "scripts/panel.html was not found." >&2; exit 1; }
[[ -f "$ROOT/application.shardcloud.yml" || -f "$ROOT/application.yml" ]] || {
  echo "application.shardcloud.yml or application.yml was not found." >&2
  exit 1
}

exec java "$ROOT/KingLauncher.java"
