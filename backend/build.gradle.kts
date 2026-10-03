// =====================================================================================
// backend – the Nexus online server (friends, DMs, parties, party chat, notifications,
// waypoints, profile cards, share codes, moderation; clans and cosmetics come later).
//
// It's a Spring Boot app: a ready-made Java web server framework. It stores data in a
// database (PostgreSQL on the real server, a small file database called H2 while developing)
// and pushes live updates (friend came online, new message…) to players over WebSockets.
//
// Run it while developing:  .\gradlew.bat :backend:bootRun
// The shared settings (Java 21 etc.) come from the root build.gradle.kts.
// =====================================================================================

import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("org.springframework.boot") version "4.1.1"
}

dependencies {
    // Spring Boot's list of library versions that are known to work together.
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")     // the web API (/api/…)
    implementation("org.springframework.boot:spring-boot-starter-websocket")  // live updates (/ws)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")   // saving to the database
    implementation("org.springframework.boot:spring-boot-starter-flyway")     // creates/updates the database tables
    implementation("org.springframework.boot:spring-boot-starter-validation") // checks incoming data
    implementation("org.springframework.boot:spring-boot-starter-restclient") // asks Mojang "is this really them?"
    implementation("com.google.code.gson:gson")                               // JSON for WebSocket messages

    runtimeOnly("org.postgresql:postgresql")                   // the real database
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("com.h2database:h2")                           // the development database (a file)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// `gradlew :backend:bootRun` = development mode (file database, test sign-in allowed).
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    systemProperty("spring.profiles.active", "dev")
}
