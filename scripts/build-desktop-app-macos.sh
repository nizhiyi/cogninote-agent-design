#!/usr/bin/env bash
set -euo pipefail

SKIP_TESTS=false
JDK_HOME_ARG=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-tests)
      SKIP_TESTS=true
      shift
      ;;
    --jdk-home)
      JDK_HOME_ARG="${2:-}"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

if [[ -n "$JDK_HOME_ARG" ]]; then
  export JDK_HOME="$JDK_HOME_ARG"
fi

bash "$SCRIPT_DIR/verify-desktop-toolchain-macos.sh"

cd "$PROJECT_ROOT"

if [[ "$SKIP_TESTS" != "true" ]]; then
  mvn test
fi

bash "$SCRIPT_DIR/build-desktop-backend-macos.sh" --skip-tests

TAURI_DIR="$PROJECT_ROOT/cogniNote-agent-front/src-tauri"
TAURI_CONFIG="$TAURI_DIR/tauri.conf.json"
TAURI_MACOS_CONFIG="$TAURI_DIR/tauri.macos.conf.json"
TAURI_WINDOWS_CONFIG_BACKUP="$(mktemp "${TMPDIR:-/tmp}/cogninote-tauri-conf.XXXXXX.json")"

if [[ ! -f "$TAURI_MACOS_CONFIG" ]]; then
  echo "macOS Tauri config not found: $TAURI_MACOS_CONFIG" >&2
  exit 1
fi

cp "$TAURI_CONFIG" "$TAURI_WINDOWS_CONFIG_BACKUP"
restore_tauri_config() {
  if [[ -f "$TAURI_WINDOWS_CONFIG_BACKUP" ]]; then
    cp "$TAURI_WINDOWS_CONFIG_BACKUP" "$TAURI_CONFIG"
    rm -f "$TAURI_WINDOWS_CONFIG_BACKUP"
  fi
}
trap restore_tauri_config EXIT

# Tauri's --config option merges with tauri.conf.json. For a truly separate
# macOS bundle config, temporarily swap the active config during the build and
# restore the Windows config immediately afterwards.
cp "$TAURI_MACOS_CONFIG" "$TAURI_CONFIG"
npm --prefix cogniNote-agent-front run desktop:build:macos

echo
echo "macOS desktop build finished. Check cogniNote-agent-front/src-tauri/target/release/bundle/."
