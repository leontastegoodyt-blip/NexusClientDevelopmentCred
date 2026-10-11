#!/usr/bin/env bash
# =====================================================================================
# setup.sh – sets up (or updates) the Nexus online server on the Oracle machine.
#
#   bash ~/nexus/setup.sh nexusclient.duckdns.org
#
# You normally don't run this yourself: upload.ps1 (on your Windows PC) copies the files over and
# runs it for you. It's safe to run again any time – it only does what's still missing:
#   1. installs Docker (the program that runs the server's three parts)
#   2. opens ports 80 and 443 in the machine's own firewall (Oracle's Ubuntu blocks them)
#   3. on small machines (1 GB RAM), adds 2 GB of "swap" so Java + the database fit
#   4. makes the .env settings file with a random database password (only the first time)
#   5. starts / restarts everything (docker compose up -d)
#   6. sets up a database backup every night at 04:00 (kept for 14 days, in ~/nexus-backups)
# =====================================================================================
set -euo pipefail
cd "$(dirname "$0")"
DOMAIN="${1:-}"

echo "== 1/6 Docker"
if ! command -v docker >/dev/null 2>&1; then
    curl -fsSL https://get.docker.com | sudo sh
    sudo usermod -aG docker "$USER"
fi

echo "== 2/6 Firewall: open ports 80 and 443"
for port in 80 443; do
    if ! sudo iptables -C INPUT -p tcp --dport "$port" -m state --state NEW -j ACCEPT 2>/dev/null; then
        sudo iptables -I INPUT 1 -p tcp --dport "$port" -m state --state NEW -j ACCEPT
    fi
done
if command -v netfilter-persistent >/dev/null 2>&1; then
    sudo netfilter-persistent save >/dev/null 2>&1 || true   # keep the rule after a reboot
fi

echo "== 3/6 Memory"
MEM_MB=$(awk '/MemTotal/ {print int($2 / 1024)}' /proc/meminfo)
if [ "$MEM_MB" -lt 2000 ]; then
    JAVA_OPTIONS="-Xmx384m"
    if ! swapon --show | grep -q '/swapfile'; then
        sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
        sudo mkswap /swapfile >/dev/null && sudo swapon /swapfile
        grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
    fi
else
    JAVA_OPTIONS="-Xmx1g"
fi
echo "   ${MEM_MB} MB RAM → Java uses ${JAVA_OPTIONS}"

echo "== 4/6 Settings (.env)"
if [ ! -f .env ]; then
    if [ -z "$DOMAIN" ]; then
        echo "First time: give your address, e.g.  bash setup.sh nexusclient.duckdns.org" >&2
        exit 1
    fi
    printf 'NEXUS_DOMAIN=%s\nPOSTGRES_PASSWORD=%s\nJAVA_OPTIONS=%s\n' \
        "$DOMAIN" "$(openssl rand -hex 24)" "$JAVA_OPTIONS" > .env
    chmod 600 .env    # only you can read the database password
    echo "   made .env for $DOMAIN"
elif [ -n "$DOMAIN" ]; then
    sed -i "s|^NEXUS_DOMAIN=.*|NEXUS_DOMAIN=$DOMAIN|" .env
fi
if [ ! -f nexus-server.jar ]; then
    echo "nexus-server.jar is missing – upload.ps1 copies it here." >&2
    exit 1
fi

echo "== 5/6 Start"
sudo docker compose pull --quiet
sudo docker compose up -d --remove-orphans
sudo docker compose restart nexus     # picks up a newly uploaded nexus-server.jar

echo "== 6/6 Nightly backups"
chmod +x backup.sh
( crontab -l 2>/dev/null | grep -v 'nexus/backup.sh'; echo "0 4 * * * $HOME/nexus/backup.sh" ) | crontab -

DOMAIN_NOW=$(grep '^NEXUS_DOMAIN=' .env | cut -d= -f2)
echo
echo "Waiting for the server to start…"
for i in $(seq 1 60); do
    if curl -fsS "https://$DOMAIN_NOW/api/partners" >/dev/null 2>&1; then
        echo "✔ The Nexus server is online at https://$DOMAIN_NOW"
        exit 0
    fi
    sleep 3
done
echo "Not answering at https://$DOMAIN_NOW yet. Check:  sudo docker compose logs --tail 50 caddy nexus"
echo "(Common causes: the Oracle 'security list' doesn't allow ports 80/443 yet, or DuckDNS doesn't"
echo " point to this machine's public IP yet.)"
exit 1
