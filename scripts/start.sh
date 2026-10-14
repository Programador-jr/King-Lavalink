#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ -f "$ROOT/Lavalink.jar" ]] || { echo "Execute primeiro ./scripts/setup.sh" >&2; exit 1; }
[[ -f "$ROOT/application.yml" ]] || { echo "Execute primeiro ./scripts/setup.sh" >&2; exit 1; }
command -v java >/dev/null 2>&1 || { echo "Java 17 ou superior é necessário." >&2; exit 1; }

cd "$ROOT"
exec java -jar "$ROOT/Lavalink.jar"
