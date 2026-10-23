#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
set -euo pipefail
for name in ANDROID_KEYSTORE_FILE ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
  if [[ -z "${!name:-}" ]]; then printf 'Missing signing configuration: %s\n' "$name" >&2; exit 1; fi
done
[[ -f "$ANDROID_KEYSTORE_FILE" ]] || { printf 'Signing file not found\n' >&2; exit 1; }
input_dir="${1:?APK directory required}"
output_dir="${2:?Output directory required}"
build_tools="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}/build-tools/35.0.0"
version="$(node build-support/release-tools.mjs version)"
# Validate everything before making the first signed artifact.
outputs="$(node build-support/release-tools.mjs check-apks "$input_dir")"
mkdir -p "$output_dir"
work_dir="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/boostyvp-sign.XXXXXX")"
cleanup() { rm -f -- "$work_dir/aligned.apk"; rmdir -- "$work_dir"; }
trap cleanup EXIT
while IFS=$'\t' read -r abi file; do
  "$build_tools/zipalign" -P 16 -f 4 "$input_dir/$file" "$work_dir/aligned.apk"
  "$build_tools/apksigner" sign --ks "$ANDROID_KEYSTORE_FILE" --ks-type PKCS12 \
    --ks-key-alias "$ANDROID_KEY_ALIAS" --ks-pass env:ANDROID_KEYSTORE_PASSWORD \
    --key-pass env:ANDROID_KEY_PASSWORD --v4-signing-enabled false \
    --out "$output_dir/boostyvp-v$version-$abi.apk" "$work_dir/aligned.apk"
done <<< "$outputs"
node build-support/release-tools.mjs manifest "$output_dir"
node build-support/release-tools.mjs verify-assets "$output_dir"
