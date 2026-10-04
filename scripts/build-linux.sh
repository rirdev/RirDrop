#!/usr/bin/env bash
set -e

echo "🔨 Building RirDrop Linux Standalone Release Package..."

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST_DIR="$PROJECT_ROOT/dist"
STAGE_DIR="$DIST_DIR/rirdrop-linux"

rm -rf "$DIST_DIR"
mkdir -p "$STAGE_DIR"

echo "📂 Staging application files..."
mkdir -p "$STAGE_DIR/src"
mkdir -p "$STAGE_DIR/assets"
mkdir -p "$STAGE_DIR/scripts"

cp -r "$PROJECT_ROOT/src" "$STAGE_DIR/"
cp -r "$PROJECT_ROOT/assets" "$STAGE_DIR/"
cp -r "$PROJECT_ROOT/node_modules" "$STAGE_DIR/"
cp "$PROJECT_ROOT/package.json" "$STAGE_DIR/"
cp "$PROJECT_ROOT/scripts/install.sh" "$STAGE_DIR/"
cp "$PROJECT_ROOT/scripts/uninstall.sh" "$STAGE_DIR/"

chmod +x "$STAGE_DIR/install.sh"
chmod +x "$STAGE_DIR/uninstall.sh"

# Create a clean README inside the package
cat << 'EOF' > "$STAGE_DIR/README.md"
# RirDrop - Offline Local File Sharing & Streaming Suite

### Installation:
Run the install command in your terminal:
```bash
./install.sh
```

Once installed, you can launch **RirDrop** directly from your application launcher (Rofi / Wofi / GNOME / KDE) or by typing `rirdrop` in your terminal.

### Uninstallation:
```bash
./uninstall.sh
```
EOF

echo "📦 Creating tar.gz archive..."
cd "$DIST_DIR"
tar -czf "rirdrop-linux.tar.gz" "rirdrop-linux"

if command -v zip &>/dev/null; then
    echo "📦 Creating zip archive..."
    zip -rq "rirdrop-linux.zip" "rirdrop-linux"
fi

echo ""
echo "=========================================================="
echo "   ✅ RirDrop Linux Package Created Successfully!"
echo "   File: $DIST_DIR/rirdrop-linux.tar.gz"
if [ -f "$DIST_DIR/rirdrop-linux.zip" ]; then
    echo "   File: $DIST_DIR/rirdrop-linux.zip"
fi
echo "=========================================================="
