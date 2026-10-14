#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT/Lavalink.jar"
CONFIG="$ROOT/application.yml"
TEMPLATE="$ROOT/application.example.yml"
VERSION="4.2.2"
URL="https://github.com/lavalink-devs/Lavalink/releases/download/$VERSION/Lavalink.jar"

if ! command -v java >/dev/null 2>&1; then
  echo "Java 17 ou superior não foi encontrado." >&2
  exit 1
fi

JAVA_VERSION="$(java -version 2>&1 | head -n 1)"
JAVA_MAJOR="$(printf '%s' "$JAVA_VERSION" | sed -E 's/.*version "([0-9]+).*/\1/')"
if ! [[ "$JAVA_MAJOR" =~ ^[0-9]+$ ]] || (( JAVA_MAJOR < 17 )); then
  echo "Java 17 ou superior é necessário. Detectado: $JAVA_VERSION" >&2
  exit 1
fi

if [[ ! -f "$JAR" ]]; then
  echo "Baixando Lavalink $VERSION..."
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --retry 3 "$URL" --output "$JAR"
  else
    wget --https-only "$URL" -O "$JAR"
  fi
  if [[ ! -s "$JAR" ]]; then
    rm -f "$JAR"
    echo "Download falhou ou o arquivo está vazio." >&2
    exit 1
  fi
else
  echo "Lavalink.jar já existe; mantendo o arquivo atual."
fi

if [[ ! -f "$CONFIG" ]]; then
  if [[ ! -f "$TEMPLATE" ]]; then
    echo "application.example.yml não encontrado." >&2
    exit 1
  fi
  PASSWORD="$(openssl rand -base64 32 | tr -d '=+/\n' | cut -c1-40)"
  awk -v password="$PASSWORD" '{ gsub(/\$\{LAVALINK_SERVER_PASSWORD:change-me-before-running\}/, password); print }' "$TEMPLATE" > "$CONFIG"
  chmod 600 "$CONFIG"
  echo "application.yml criado com senha local aleatória (arquivo ignorado pelo Git)."
else
  echo "application.yml já existe; mantendo sua configuração."
fi

echo "Preparação concluída. Inicie com: ./scripts/start.sh"
