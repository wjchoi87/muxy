#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SOURCE_SVG="$PROJECT_ROOT/assets/logo.svg"
# assets/logo.svg keeps the macOS icon body in an 824x824 area centered
# on the 1024x1024 canvas, leaving 100px transparent padding per side.
APPICON_DIR="$PROJECT_ROOT/Muxy/Resources/Assets.xcassets/AppIcon.appiconset"

if ! command -v sips >/dev/null 2>&1; then
  echo "error: sips is required to render app icons" >&2
  exit 1
fi

render_icon() {
  local pixels="$1"
  local filename="$2"
  sips -s format png -z "$pixels" "$pixels" "$SOURCE_SVG" --out "$APPICON_DIR/$filename" >/dev/null
}

render_icon 16   icon_16.png
render_icon 32   icon_16@2x.png
render_icon 32   icon_32.png
render_icon 64   icon_32@2x.png
render_icon 128  icon_128.png
render_icon 256  icon_128@2x.png
render_icon 256  icon_256.png
render_icon 512  icon_256@2x.png
render_icon 512  icon_512.png
render_icon 1024 icon_512@2x.png

echo "Generated app icons in $APPICON_DIR"
