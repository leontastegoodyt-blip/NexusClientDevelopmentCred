#!/usr/bin/env bash
# backup.sh – saves a copy of the whole Nexus database (runs every night at 04:00, set up by setup.sh).
# Copies go to ~/nexus-backups/nexus-YYYY-MM-DD.sql.gz and are kept for 14 days.
# To restore one (careful – replaces everything):
#   gunzip -c ~/nexus-backups/nexus-2026-10-11.sql.gz | sudo docker compose -f ~/nexus/docker-compose.yml exec -T db psql -U nexus nexus
set -euo pipefail
mkdir -p "$HOME/nexus-backups"
cd "$HOME/nexus"
sudo docker compose exec -T db pg_dump -U nexus --clean --if-exists nexus | gzip > "$HOME/nexus-backups/nexus-$(date +%F).sql.gz"
find "$HOME/nexus-backups" -name 'nexus-*.sql.gz' -mtime +14 -delete
