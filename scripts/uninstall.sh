#!/usr/bin/env bash
set -e

echo "Removing RirDrop from your system..."

rm -rf "$HOME/.local/share/rirdrop"
rm -f "$HOME/.local/bin/rirdrop"
rm -f "$HOME/.local/share/applications/rirdrop.desktop"
rm -f "$HOME/.local/share/icons/hicolor/512x512/apps/rirdrop.png"
rm -f "$HOME/.local/share/pixmaps/rirdrop.png"

if command -v update-desktop-database &>/dev/null; then
    update-desktop-database "$HOME/.local/share/applications" 2>/dev/null || true
fi

echo "RirDrop has been cleanly uninstalled."
