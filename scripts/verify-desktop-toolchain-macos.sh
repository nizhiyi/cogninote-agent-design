#!/usr/bin/env bash
set -euo pipefail

JDK_HOME="${JDK_HOME:-${JAVA_HOME:-}}"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "macOS desktop packaging must run on Darwin." >&2
  exit 1
fi

if [[ "$(uname -m)" != "arm64" ]]; then
  echo "macOS desktop packaging currently supports Apple Silicon arm64 only." >&2
  exit 1
fi

if [[ -z "$JDK_HOME" ]]; then
  for candidate in \
    "/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home" \
    "$HOME/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home"; do
    if [[ -x "$candidate/bin/java" && -x "$candidate/bin/jpackage" ]]; then
      JDK_HOME="$candidate"
      break
    fi
  done
fi

if [[ -z "$JDK_HOME" || ! -x "$JDK_HOME/bin/java" ]]; then
  echo "JDK 25 java not found. Set JDK_HOME or JAVA_HOME to a full arm64 JDK 25." >&2
  exit 1
fi

if [[ ! -x "$JDK_HOME/bin/jpackage" ]]; then
  echo "jpackage not found under $JDK_HOME. Use a full JDK, not a JRE." >&2
  exit 1
fi

export JAVA_HOME="$JDK_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

require_command() {
  local name="$1"
  local hint="$2"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "$name not found. $hint" >&2
    exit 1
  fi
}

require_command mvn "Install Maven 3.9+ and add it to PATH."
require_command node "Install Node.js 20.19+ or a compatible version."
require_command npm "Install npm."
require_command cargo "Install Rust stable with rustup."
require_command rustc "Install Rust stable with rustup."
require_command xcodebuild "Install Xcode Command Line Tools: xcode-select --install."

echo "macOS desktop toolchain check passed."
echo "JAVA_HOME = $JAVA_HOME"
"$JAVA_HOME/bin/java" -version
echo "jpackage = $("$JAVA_HOME/bin/jpackage" --version)"
echo "node = $(node --version)"
echo "npm = $(npm --version)"
echo "rustc = $(rustc --version)"
echo "cargo = $(cargo --version)"
echo "xcodebuild = $(xcodebuild -version | head -n 1)"
