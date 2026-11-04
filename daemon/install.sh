#!/usr/bin/env bash
set -e

# AntiGravity Pets - Daemon Installer
# Installs and enables the background quota sync and heartbeat service for AntiGravity Pets

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="/storage/emulated/10/Documents/Projects/.gemini/antigravity"
SYSTEMD_USER_DIR="$HOME/.config/systemd/user"

echo "=== Installing AntiGravity Pets Sync Daemon ==="
mkdir -p "$TARGET_DIR"
mkdir -p "$SYSTEMD_USER_DIR"

cp "$SCRIPT_DIR/sync_usage.py" "$TARGET_DIR/sync_usage.py"
chmod +x "$TARGET_DIR/sync_usage.py"

cp "$SCRIPT_DIR/agy-sync-usage.service" "$SYSTEMD_USER_DIR/agy-sync-usage.service"

sudo loginctl enable-linger "$USER" 2>/dev/null || true
systemctl --user daemon-reload
systemctl --user enable --now agy-sync-usage.service

echo "=== Service Status ==="
systemctl --user status agy-sync-usage.service --no-pager || true
echo "Daemon successfully installed and running!"
