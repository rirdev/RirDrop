#!/usr/bin/env bash
set -e

SSD_DIR="/run/media/$USER/LINSSD/RirDrop"

if [ ! -d "/run/media/$USER/LINSSD" ]; then
    echo "❌ LINSSD is not mounted. Please click LINSSD in your file manager (Nautilus) first."
    exit 1
fi

echo "📦 Syncing Windows builds to LINSSD/RirDrop/windows..."
if [ -d "dist" ]; then
    mkdir -p "$SSD_DIR/windows"
    [ -f dist/"RirDrop Setup 1.0.0.exe" ] && cp dist/"RirDrop Setup 1.0.0.exe" "$SSD_DIR/windows/"
    [ -f dist/"RirDrop 1.0.0.exe" ] && cp dist/"RirDrop 1.0.0.exe" "$SSD_DIR/windows/"
    cd "$SSD_DIR/windows"
    [ -f "RirDrop Setup 1.0.0.exe" ] && sha256sum "RirDrop Setup 1.0.0.exe" > "RirDrop Setup 1.0.0.exe.sha256"
    [ -f "RirDrop 1.0.0.exe" ] && sha256sum "RirDrop 1.0.0.exe" > "RirDrop 1.0.0.exe.sha256"
    sha256sum *.exe > SHA256SUMS.txt 2>/dev/null || true
    echo "✅ Windows builds synced:"
    ls -lh "$SSD_DIR/windows"
fi

echo "📦 Syncing Android builds to LINSSD/RirDrop/android..."
mkdir -p "$SSD_DIR/android"
if [ -f "/home/rir790/.gemini/antigravity/scratch/rirdrop/android/app/build/outputs/apk/release/app-release.apk" ]; then
    cp "/home/rir790/.gemini/antigravity/scratch/rirdrop/android/app/build/outputs/apk/release/app-release.apk" "$SSD_DIR/android/RirDrop-v1.0.0.apk"
fi
if [ -f "/home/rir790/.gemini/antigravity/scratch/rirdrop/android/app/build/outputs/apk/debug/app-debug.apk" ]; then
    cp "/home/rir790/.gemini/antigravity/scratch/rirdrop/android/app/build/outputs/apk/debug/app-debug.apk" "$SSD_DIR/android/RirDrop-v1.0.0-debug.apk"
fi

cd "$SSD_DIR/android"
[ -f "RirDrop-v1.0.0.apk" ] && sha256sum "RirDrop-v1.0.0.apk" > "RirDrop-v1.0.0.apk.sha256"
[ -f "RirDrop-v1.0.0-debug.apk" ] && sha256sum "RirDrop-v1.0.0-debug.apk" > "RirDrop-v1.0.0-debug.apk.sha256"
sha256sum *.apk > SHA256SUMS.txt 2>/dev/null || true

echo "✅ Android builds synced:"
ls -lh "$SSD_DIR/android"
