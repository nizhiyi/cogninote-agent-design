#!/usr/bin/env bash
set -euo pipefail

SKIP_TESTS=false
JDK_HOME_ARG=""
SIGN_BUILD=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-tests)
      SKIP_TESTS=true
      shift
      ;;
    --sign)
      SIGN_BUILD=true
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
TAURI_DIR="$PROJECT_ROOT/cogniNote-agent-front/src-tauri"
TAURI_CONFIG="$TAURI_DIR/tauri.conf.json"
TAURI_MACOS_CONFIG="$TAURI_DIR/tauri.macos.conf.json"
MACOS_ENTITLEMENTS="$TAURI_DIR/entitlements/macos.plist"
BACKEND_APP_IMAGE="$PROJECT_ROOT/target/desktop-macos/backend/CogniNoteBackend.app"

if [[ -n "$JDK_HOME_ARG" ]]; then
  export JDK_HOME="$JDK_HOME_ARG"
fi

bash "$SCRIPT_DIR/verify-desktop-toolchain-macos.sh"

cd "$PROJECT_ROOT"

if [[ "$SKIP_TESTS" != "true" ]]; then
  mvn test
fi

bash "$SCRIPT_DIR/build-desktop-backend-macos.sh" --skip-tests

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

require_macos_signing_tools() {
  if ! command -v codesign >/dev/null 2>&1; then
    echo "codesign not found. Install Xcode Command Line Tools." >&2
    exit 1
  fi
  if ! command -v xcrun >/dev/null 2>&1; then
    echo "xcrun not found. Install Xcode Command Line Tools." >&2
    exit 1
  fi
  if [[ ! -f "$MACOS_ENTITLEMENTS" ]]; then
    echo "macOS entitlements file not found: $MACOS_ENTITLEMENTS" >&2
    exit 1
  fi
}

sign_macos_app_bundle() {
  local app_path="$1"
  if [[ ! -d "$app_path" ]]; then
    echo "macOS app bundle not found for signing: $app_path" >&2
    exit 1
  fi

  echo "Signing macOS app bundle: $app_path"
  codesign \
    --force \
    --deep \
    --timestamp \
    --options runtime \
    --entitlements "$MACOS_ENTITLEMENTS" \
    --sign "$APPLE_SIGNING_IDENTITY" \
    "$app_path"
  codesign --verify --deep --strict --verbose=2 "$app_path"
}

verify_signed_macos_outputs() {
  local app_path
  app_path="$(find "$TAURI_DIR/target/release/bundle/macos" -maxdepth 1 -name '*.app' -type d | head -n 1)"

  if [[ -z "$app_path" ]]; then
    echo "Signed macOS build did not generate a .app output." >&2
    exit 1
  fi

  local bundled_backend_app="$app_path/Contents/Resources/backend/CogniNoteBackend.app"
  if [[ ! -d "$bundled_backend_app" ]]; then
    echo "Signed macOS build did not bundle the backend app: $bundled_backend_app" >&2
    exit 1
  fi

  codesign --verify --deep --strict --verbose=2 "$bundled_backend_app"
  codesign --verify --deep --strict --verbose=2 "$app_path"
}

# Tauri's --config option merges with tauri.conf.json. For a truly separate
# macOS bundle config, temporarily swap the active config during the build and
# restore the Windows config immediately afterwards.
cp "$TAURI_MACOS_CONFIG" "$TAURI_CONFIG"
if [[ "$SIGN_BUILD" == "true" ]]; then
  if [[ -z "${APPLE_SIGNING_IDENTITY:-}" ]]; then
    echo "APPLE_SIGNING_IDENTITY is required for signed macOS builds." >&2
    exit 1
  fi
  require_macos_signing_tools

  # The checked-in macOS config stays secret-free. During signed CI builds, write
  # the Developer ID identity only into the temporary active Tauri config that is
  # restored at process exit.
  node - "$TAURI_CONFIG" <<'NODE'
const fs = require('fs');
const configPath = process.argv[2];
const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
config.bundle ??= {};
config.bundle.macOS ??= {};
config.bundle.macOS.signingIdentity = process.env.APPLE_SIGNING_IDENTITY;
if (process.env.APPLE_PROVIDER_SHORT_NAME) {
  config.bundle.macOS.providerShortName = process.env.APPLE_PROVIDER_SHORT_NAME;
}
fs.writeFileSync(configPath, `${JSON.stringify(config, null, 2)}\n`);
NODE

  # The final Tauri app embeds the jpackage backend as a nested .app resource.
  # Sign it before Tauri copies it into the outer bundle; otherwise Gatekeeper can
  # reject the downloaded app as damaged even when the outer Tauri app is signed.
  sign_macos_app_bundle "$BACKEND_APP_IMAGE"

  rm -rf \
    "$TAURI_DIR/target/release/bundle/macos/CogniNote.app" \
    "$TAURI_DIR/target/release/bundle/dmg"
  npm --prefix cogniNote-agent-front run desktop:build:macos:signed
  verify_signed_macos_outputs
else
  rm -rf \
    "$TAURI_DIR/target/release/bundle/macos/CogniNote.app" \
    "$TAURI_DIR/target/release/bundle/dmg"
  npm --prefix cogniNote-agent-front run desktop:build:macos
fi

echo
echo "macOS desktop build finished. Check cogniNote-agent-front/src-tauri/target/release/bundle/."
