#!/usr/bin/env bash
set -euo pipefail

echo "============================================="
echo " Antigravity Android Session Pairing Tool"
echo "============================================="

# Check ADB device
if ! adb get-state >/dev/null 2>&1; then
    echo "Error: No Android device detected via ADB."
    echo "Connect your device via USB or Wi-Fi (adb connect <ip>:5555)."
    exit 1
fi

DEVICE_MODEL=$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo "Android Device")
echo "Connected Device: $DEVICE_MODEL"

# Accept cookie payload as argument or prompt
if [ $# -ge 1 ]; then
    COOKIE_PAYLOAD="$1"
else
    echo "Paste your active Google session cookies below:"
    read -r -p "Cookies: " COOKIE_PAYLOAD
fi

# Broadcast to AuthSyncReceiver
adb shell am broadcast \
    -a com.sapta.antigravity.SYNC_AUTH \
    -n com.sapta.antigravity.remote/.AuthSyncReceiver \
    --es cookies "$COOKIE_PAYLOAD"

echo "Session broadcast sent to $DEVICE_MODEL"
