#!/usr/bin/env bash
set -euo pipefail

ARCHITECTURE="aarch64"
JAVA_FEATURE_VERSION="25"
JDK_HOME_ARG="${JDK_HOME:-${JAVA_HOME:-}}"
DESTINATION_ROOT="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/cogninote-temurin-jmods"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --architecture)
      ARCHITECTURE="${2:-}"
      shift 2
      ;;
    --java-feature-version)
      JAVA_FEATURE_VERSION="${2:-}"
      shift 2
      ;;
    --jdk-home)
      JDK_HOME_ARG="${2:-}"
      shift 2
      ;;
    --destination-root)
      DESTINATION_ROOT="${2:-}"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ -z "$JDK_HOME_ARG" ]]; then
  echo "JAVA_HOME is empty. Run actions/setup-java first or pass --jdk-home." >&2
  exit 1
fi

if [[ -f "$JDK_HOME_ARG/jmods/java.base.jmod" ]]; then
  if [[ -n "${GITHUB_ENV:-}" ]]; then
    echo "JDK_JMODS_DIR=$JDK_HOME_ARG/jmods" >> "$GITHUB_ENV"
  fi
  echo "JDK JMODs already available: $JDK_HOME_ARG/jmods"
  exit 0
fi

API_URL="https://api.adoptium.net/v3/assets/latest/${JAVA_FEATURE_VERSION}/hotspot?architecture=${ARCHITECTURE}&image_type=jmods&os=mac&vendor=eclipse"
read -r DOWNLOAD_URL EXPECTED_CHECKSUM ARCHIVE_NAME < <(python3 - "$API_URL" <<'PY'
import json
import sys
import urllib.request

with urllib.request.urlopen(sys.argv[1]) as response:
    assets = json.load(response)
if not assets:
    raise SystemExit(f"No Temurin JMODs asset found from Adoptium API: {sys.argv[1]}")
package = assets[0]["binary"]["package"]
print(package["link"], package["checksum"], package["name"])
PY
)

ARCHIVE_PATH="${TMPDIR:-/tmp}/${ARCHIVE_NAME}"
EXTRACT_DIR="${TMPDIR:-/tmp}/cogninote-temurin-jmods-$$"
JMODS_DIR="$DESTINATION_ROOT/jmods"

echo "Downloading Temurin JMODs: $DOWNLOAD_URL"
curl --fail --location --retry 3 --output "$ARCHIVE_PATH" "$DOWNLOAD_URL"

ACTUAL_CHECKSUM="$(shasum -a 256 "$ARCHIVE_PATH" | awk '{print $1}')"
if [[ "$ACTUAL_CHECKSUM" != "$EXPECTED_CHECKSUM" ]]; then
  echo "Temurin JMODs checksum mismatch. Expected $EXPECTED_CHECKSUM but got $ACTUAL_CHECKSUM." >&2
  exit 1
fi

rm -rf "$EXTRACT_DIR"
mkdir -p "$EXTRACT_DIR" "$JMODS_DIR"
tar -xzf "$ARCHIVE_PATH" -C "$EXTRACT_DIR"

JAVA_BASE_JMOD="$(find "$EXTRACT_DIR" -name 'java.base.jmod' -type f -print -quit)"
if [[ -z "$JAVA_BASE_JMOD" ]]; then
  echo "Downloaded Temurin JMODs archive did not contain java.base.jmod." >&2
  exit 1
fi

SOURCE_JMODS_DIR="$(dirname "$JAVA_BASE_JMOD")"
cp "$SOURCE_JMODS_DIR"/*.jmod "$JMODS_DIR/"

if [[ ! -f "$JMODS_DIR/java.base.jmod" ]]; then
  echo "Temurin JMODs installation failed: $JMODS_DIR/java.base.jmod was not created." >&2
  exit 1
fi

if [[ -n "${GITHUB_ENV:-}" ]]; then
  echo "JDK_JMODS_DIR=$JMODS_DIR" >> "$GITHUB_ENV"
fi

echo "Installed Temurin JMODs: $JMODS_DIR"
