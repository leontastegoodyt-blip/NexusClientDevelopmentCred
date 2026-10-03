# backend

The Nexus online server: sign-in (checked with Mojang), friends, DMs, parties, party chat,
waypoints, profile cards, share codes and moderation. Clans, Premium and cosmetics come later.

- Run for development: `.\gradlew.bat :backend:bootRun` (http://localhost:8080, file database in `data/`)
- Test: `.\gradlew.bat :backend:test`
- Real server: build with `.\gradlew.bat :backend:bootJar`, then
  `java -jar backend/build/libs/backend-0.1.0.jar --spring.profiles.active=prod`
  with `NEXUS_DB_URL`, `NEXUS_DB_USER` and `NEXUS_DB_PASSWORD` set (PostgreSQL).

See the "Decisions made in step 13" section of the project brief for how it all works.
