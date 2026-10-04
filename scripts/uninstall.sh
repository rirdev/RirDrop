#!/usr/bin/env bash
set -e

echo "============================================="
echo "   🗑️  Uninstalling RirDrop Completely"
echo "============================================="

# 1. Stop any running RirDrop processes
echo "⏹️  Stopping any active RirDrop instances..."
pkill -f "electron .*rirdrop" 2>/dev/null || pkill -f rirdrop 2>/dev/null || true

# 2. Remove application files
echo "📂 Removing application files..."
rm -rf "$HOME/.local/share/rirdrop"

# 3. Remove binary launcher
echo "🚀 Removing binary launcher ($HOME/.local/bin/rirdrop)..."
rm -f "$HOME/.local/bin/rirdrop"

# 4. Remove desktop menu shortcuts
echo "🖥️  Removing application menu entry..."
rm -f "$HOME/.local/share/applications/rirdrop.desktop"

# 5. Remove icons
echo "🎨 Removing icons..."
rm -f "$HOME/.local/share/icons/hicolor/512x512/apps/rirdrop.png"
rm -f "$HOME/.local/share/icons/rirdrop.png"
rm -f "$HOME/.local/share/pixmaps/rirdrop.png"

# 6. Remove config and cached data (complete wipe)
echo "🧹 Purging configuration and cache..."
rm -rf "$HOME/.config/rirdrop"
rm -rf "$HOME/.config/RirDrop"
rm -rf "$HOME/.cache/rirdrop"

# 7. Update system desktop and icon databases
if command -v update-desktop-database &>/dev/null; then
    update-desktop-database "$HOME/.local/share/applications" 2>/dev/null || true
fi

if command -v gtk-update-icon-cache &>/dev/null; then
    gtk-update-icon-cache -f -t "$HOME/.local/share/icons/hicolor" 2>/dev/null || true
fi

echo ""
echo "============================================="
echo "   ✨ RirDrop has been completely uninstalled!"
echo "============================================="
echo "All application files, launchers, icons, and"
echo "cached settings have been cleanly removed."
echo "============================================="
