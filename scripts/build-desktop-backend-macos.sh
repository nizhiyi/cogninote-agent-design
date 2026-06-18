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

# Source the verifier so a resolved JAVA_HOME remains available to jlink and
# jpackage in this same shell, including when callers only pass --jdk-home.
. "$SCRIPT_DIR/verify-desktop-toolchain-macos.sh"

cd "$PROJECT_ROOT"

JAR_NAME="cogninote-agent-design.jar"
JAR_PATH="$PROJECT_ROOT/target/$JAR_NAME"
COMPILED_STATIC_DIR="$PROJECT_ROOT/target/classes/static"
DESKTOP_BACKEND_DIR="$PROJECT_ROOT/target/desktop-macos/backend"
BACKEND_IMAGE_DIR="$DESKTOP_BACKEND_DIR/CogniNoteBackend.app"
CUSTOM_RUNTIME_DIR="$PROJECT_ROOT/target/desktop-macos/runtime"
JPACKAGE_INPUT_DIR="$PROJECT_ROOT/target/desktop-macos/jpackage-input"
DESKTOP_RUNTIME_MODULES="java.base,java.compiler,java.desktop,java.instrument,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.sql.rowset,java.xml.crypto,jdk.attach,jdk.incubator.vector,jdk.jdi,jdk.jfr,jdk.management,jdk.unsupported"

path_size() {
  if [[ ! -e "$1" ]]; then
    echo "0B"
    return
  fi
  du -sh "$1" | awk '{print $1}'
}

create_desktop_runtime() {
  rm -rf "$CUSTOM_RUNTIME_DIR"
  # jdeps is only the starting point for this module list. Keep the first
  # desktop runtime conservative because Spring reflection, SQLite native
  # loading, PDF/Office parsing, Lucene and model SDK paths are runtime-heavy.
  # Temurin 24+ can link from the current runtime image, so do not require
  # $JAVA_HOME/jmods in GitHub Actions.
  "$JAVA_HOME/bin/jlink" \
    --add-modules "$DESKTOP_RUNTIME_MODULES" \
    --strip-debug \
    --strip-java-debug-attributes \
    --strip-native-commands \
    --no-header-files \
    --no-man-pages \
    --compress zip-6 \
    --output "$CUSTOM_RUNTIME_DIR"
}

rm -rf "$COMPILED_STATIC_DIR"

if [[ "$SKIP_TESTS" == "true" ]]; then
  mvn -Pwith-frontend package -DskipTests
else
  mvn -Pwith-frontend package
fi

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Backend Jar was not generated: $JAR_PATH" >&2
  exit 1
fi

rm -rf "$BACKEND_IMAGE_DIR" "$CUSTOM_RUNTIME_DIR" "$JPACKAGE_INPUT_DIR"
mkdir -p "$DESKTOP_BACKEND_DIR" "$JPACKAGE_INPUT_DIR"
cp "$JAR_PATH" "$JPACKAGE_INPUT_DIR/$JAR_NAME"
create_desktop_runtime

"$JAVA_HOME/bin/jpackage" \
  --type app-image \
  --name CogniNoteBackend \
  --input "$JPACKAGE_INPUT_DIR" \
  --main-jar "$JAR_NAME" \
  --dest "$DESKTOP_BACKEND_DIR" \
  --runtime-image "$CUSTOM_RUNTIME_DIR" \
  --java-options "--enable-native-access=ALL-UNNAMED"

BACKEND_LAUNCHER="$BACKEND_IMAGE_DIR/Contents/MacOS/CogniNoteBackend"
if [[ ! -x "$BACKEND_LAUNCHER" ]]; then
  echo "jpackage did not generate backend launcher: $BACKEND_LAUNCHER" >&2
  exit 1
fi

echo "macOS custom desktop runtime generated: $CUSTOM_RUNTIME_DIR ($(path_size "$CUSTOM_RUNTIME_DIR"))"
echo "macOS backend app-image generated: $BACKEND_IMAGE_DIR ($(path_size "$BACKEND_IMAGE_DIR"))"
echo "Backend jar size: $JAR_PATH ($(path_size "$JAR_PATH"))"
