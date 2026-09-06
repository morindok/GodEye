#!/usr/bin/env bash
# Requires internet, a full JDK 17, and Android SDK platform 35.
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v javac >/dev/null; then echo "Install a full JDK 17 and set JAVA_HOME first." >&2; exit 1; fi
if [[ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" && ! -f local.properties ]]; then
  echo "Set ANDROID_HOME to your Android SDK (or create local.properties with sdk.dir)." >&2; exit 1
fi
version=8.9
mkdir -p .tooling
if [[ ! -x ".tooling/gradle-$version/bin/gradle" ]]; then
  archive=".tooling/gradle-$version-bin.zip"
  curl --fail --location --retry 2 "https://services.gradle.org/distributions/gradle-$version-bin.zip" -o "$archive"
  expected=$(curl --fail --location "https://services.gradle.org/distributions/gradle-$version-bin.zip.sha256")
  if command -v sha256sum >/dev/null; then actual=$(sha256sum "$archive" | cut -d' ' -f1)
  else actual=$(shasum -a 256 "$archive" | cut -d' ' -f1); fi
  [[ "$actual" == "$expected" ]] || { echo "Gradle checksum mismatch" >&2; rm -f "$archive"; exit 1; }
  unzip -q -o "$archive" -d .tooling
  rm "$archive"
fi
if [[ $# -eq 0 ]]; then set -- testDebugUnitTest lintDebug assembleDebug; fi
exec ".tooling/gradle-$version/bin/gradle" --no-daemon "$@"
