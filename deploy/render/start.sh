#!/bin/sh
# =====================================================================================
# start.sh – starts the Nexus server inside Render (it's the container's first command).
#
# Neon gives the database as ONE line, e.g.
#   postgresql://nexus_owner:secret@ep-cool-name-123-pooler.eu-central-1.aws.neon.tech/nexus?sslmode=require
# Render passes that in as DATABASE_URL. Java wants it split into three parts, so this turns it into
# NEXUS_DB_URL (jdbc:postgresql://host/database?sslmode=require), NEXUS_DB_USER and NEXUS_DB_PASSWORD.
# It also uses Neon's direct address instead of the "-pooler" one (the table-creating tool, Flyway,
# needs a direct connection).
# =====================================================================================
set -e

if [ -n "$DATABASE_URL" ] && [ -z "$NEXUS_DB_URL" ]; then
    rest="${DATABASE_URL#*://}"            # nexus_owner:secret@host/nexus?sslmode=require
    credentials="${rest%%@*}"              # nexus_owner:secret
    location="${rest#*@}"                  # host/nexus?sslmode=require
    host_and_db="${location%%\?*}"         # host/nexus
    host_and_db=$(echo "$host_and_db" | sed 's/-pooler\././')
    export NEXUS_DB_USER="${credentials%%:*}"
    export NEXUS_DB_PASSWORD="${credentials#*:}"
    export NEXUS_DB_URL="jdbc:postgresql://${host_and_db}?sslmode=require"
fi

if [ -z "$NEXUS_DB_URL" ]; then
    echo "No database! Set DATABASE_URL (the connection string from Neon) in Render → Environment." >&2
    exit 1
fi

# Render's free machines have 512 MB of memory: keep Java small, and start fast.
exec java -Xmx256m -XX:MaxMetaspaceSize=160m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 \
    -jar /app/nexus-server.jar \
    --spring.profiles.active=prod \
    --server.port="${PORT:-8080}" \
    --server.tomcat.threads.max=40 \
    --spring.datasource.hikari.maximum-pool-size=5
