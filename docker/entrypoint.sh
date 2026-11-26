#!/usr/bin/env bash
set -euo pipefail

CMD="${1:-help}"

case "$CMD" in
  test)
    echo "[Short Planet Stream Docker] A executar testes unitários Android..."
    gradle :app:testDebugUnitTest --no-daemon
    ;;
  apk)
    echo "[Short Planet Stream Docker] A compilar APK Debug..."
    gradle :app:assembleDebug --no-daemon
    mkdir -p /workspace/build-artifacts
    cp -f app/build/outputs/apk/debug/*.apk /workspace/build-artifacts/ || true
    echo "APK copiado para ./build-artifacts/"
    ;;
  ios-prep)
    echo "[Short Planet Stream Docker] A validar estrutura iOS / Fastlane..."
    mkdir -p /workspace/build-artifacts/ios
    cp -f /workspace/ios/ShortPlanetStreamApp.swift /workspace/build-artifacts/ios/
    cp -f /workspace/ios/ExportOptions.plist /workspace/build-artifacts/ios/
    echo "Pre-flight iOS concluído. Para gerar o .ipa final use Xcode em macOS ou o workflow .github/workflows/build-ios-ipa.yml."
    fastlane --version || true
    ;;
  shell)
    exec /bin/bash
    ;;
  *)
    echo "Uso: studio-entrypoint.sh [test|apk|ios-prep|shell]"
    ;;
esac
