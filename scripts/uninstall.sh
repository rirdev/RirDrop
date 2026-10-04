#!/usr/bin/env bash
set -e

echo "============================================="
echo "   🗑️  Uninstalling RirDrop Completely"
echo "============================================="

# 1. Stop any running RirDrop processes
echo "⏹️  Stopping any active RirDrop instances..."
pkill -f "electron .*rirdrop" 2>/dev/null || pkill -f rirdrop 2>/dev/null || true

# 2. Remove desktop menu shortcuts first (so it vanishes from app menu immediately)
echo "🖥️  Removing application menu entry..."
rm -f "$HOME/.local/share/applications/rirdrop.desktop" || true

# 3. Remove icons
echo "🎨 Removing icons..."
rm -f "$HOME/.local/share/icons/hicolor/512x512/apps/rirdrop.png" || true
rm -f "$HOME/.local/share/icons/rirdrop.png" || true
rm -f "$HOME/.local/share/pixmaps/rirdrop.png" || true

# 4. Remove binary launcher
echo "🚀 Removing binary launcher ($HOME/.local/bin/rirdrop)..."
rm -f "$HOME/.local/bin/rirdrop" || true

# 5. Remove application files
echo "📂 Removing application files..."
rm -rf "$HOME/.local/share/rirdrop" || true

# 6. Remove config and cached data (complete wipe)
echo "🧹 Purging configuration and cache..."
rm -rf "$HOME/.config/rirdrop" || true
rm -rf "$HOME/.config/RirDrop" || true
rm -rf "$HOME/.cache/rirdrop" || true

# 7. Update system desktop and icon databases
if command -v update-desktop-database &>/dev/null; then
    update-desktop-database "$HOME/.local/share/applications" 2>/dev/null || true
fi

if command -v gtk-update-icon-cache &>/dev/null; then
    gtk-update-icon-cache -f -t "$HOME/.local/share/icons/hicolor" 2>/dev/null || true
fi

# 8. Reload custom shell daemon if active (e.g. Ryoku/Hyprland)
if command -v ryoku-shell &>/dev/null; then
    ryoku-shell reload 2>/dev/null || true
fi

echo ""
echo "============================================="
echo "   ✨ RirDrop has been completely uninstalled!"
echo "============================================="
echo "All application files, launchers, icons, and"
echo "cached settings have been cleanly removed."
echo "============================================="
