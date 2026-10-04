#!/usr/bin/env bash
set -e

echo "============================================="
echo "   ⚡ Installing RirDrop on Linux"
echo "============================================="

# Determine source directory (where this script is located)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_SRC_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Target installation paths
INSTALL_DIR="$HOME/.local/share/rirdrop"
BIN_DIR="$HOME/.local/bin"
DESKTOP_DIR="$HOME/.local/share/applications"
ICON_DIR="$HOME/.local/share/icons/hicolor/512x512/apps"
PIXMAPS_DIR="$HOME/.local/share/pixmaps"

echo "📦 Setting up directories..."
mkdir -p "$INSTALL_DIR"
mkdir -p "$BIN_DIR"
mkdir -p "$DESKTOP_DIR"
mkdir -p "$ICON_DIR"
mkdir -p "$PIXMAPS_DIR"

echo "📂 Copying RirDrop files to $INSTALL_DIR..."
# Copy source files (excluding git, tests, scratch)
cp -r "$APP_SRC_DIR/src" "$INSTALL_DIR/"
cp -r "$APP_SRC_DIR/assets" "$INSTALL_DIR/"
cp -r "$APP_SRC_DIR/scripts" "$INSTALL_DIR/"
cp -r "$APP_SRC_DIR/node_modules" "$INSTALL_DIR/"
cp "$APP_SRC_DIR/package.json" "$INSTALL_DIR/"

# Copy icon
echo "🎨 Registering app icons..."
cp "$APP_SRC_DIR/assets/icon.png" "$ICON_DIR/rirdrop.png"
cp "$APP_SRC_DIR/assets/icon.png" "$PIXMAPS_DIR/rirdrop.png"
cp "$APP_SRC_DIR/assets/icon.png" "$HOME/.local/share/icons/rirdrop.png"

# Create binary launcher wrapper in ~/.local/bin/rirdrop
echo "🚀 Creating executable launcher in $BIN_DIR/rirdrop..."
cat << 'EOF' > "$BIN_DIR/rirdrop"
#!/usr/bin/env bash
APP_DIR="$HOME/.local/share/rirdrop"

# Find electron binary (either local in node_modules or system)
if [ -f "$APP_DIR/node_modules/.bin/electron" ]; then
    ELECTRON_BIN="$APP_DIR/node_modules/.bin/electron"
elif command -v electron &>/dev/null; then
    ELECTRON_BIN="$(command -v electron)"
else
    echo "Error: Electron runtime not found in $APP_DIR or system PATH." >&2
    exit 1
fi

exec "$ELECTRON_BIN" "$APP_DIR" "$@"
EOF

chmod +x "$BIN_DIR/rirdrop"

# Create .desktop file for Linux Application Menu
echo "🖥️  Registering App Menu launcher ($DESKTOP_DIR/rirdrop.desktop)..."
cat << EOF > "$DESKTOP_DIR/rirdrop.desktop"
[Desktop Entry]
Name=RirDrop
GenericName=Offline Local File Sharing & Streaming
Comment=High-speed local network file sharing and media streaming
Exec=$BIN_DIR/rirdrop %U
Icon=rirdrop
Terminal=false
Type=Application
Categories=Network;FileTransfer;Utility;AudioVideo;
Keywords=share;drop;airdrop;localsend;stream;video;offline;transfer;
StartupNotify=true
StartupWMClass=rirdrop
EOF

chmod +x "$DESKTOP_DIR/rirdrop.desktop"

# Update desktop database
if command -v update-desktop-database &>/dev/null; then
    update-desktop-database "$DESKTOP_DIR" 2>/dev/null || true
fi

# Update icon cache
if command -v gtk-update-icon-cache &>/dev/null; then
    gtk-update-icon-cache -f -t "$HOME/.local/share/icons/hicolor" 2>/dev/null || true
fi

echo ""
echo "============================================="
echo "   ✨ RirDrop Installed Successfully!"
echo "============================================="
echo "👉 You can now launch RirDrop from your App Menu"
echo "   (Rofi / Wofi / Application Launcher)"
echo "   Or simply run 'rirdrop' in any terminal."
echo "============================================="
