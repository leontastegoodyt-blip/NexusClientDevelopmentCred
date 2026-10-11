# =====================================================================================
# Dockerfile – how Render builds and runs the Nexus online server (see render.yaml in the project
# folder). A Dockerfile is a recipe: start from a ready-made system with Java, copy our files in,
# build, and say which command starts the server.
#
# Two stages, so the finished image is small:
#   1. "build": a full Java (JDK) builds backend/ into nexus-server.jar with the Gradle Wrapper
#   2. "run":   only the Java runtime (JRE) + that jar + start.sh
# =====================================================================================

# ---- 1. Build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# The Gradle Wrapper and the server's code – NOT the Minecraft mods (see settings.gradle.kts here).
COPY gradlew ./
COPY gradle/wrapper gradle/wrapper
COPY backend backend
COPY deploy/render/settings.gradle.kts settings.gradle.kts
COPY deploy/render/build.gradle.kts build.gradle.kts
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew \
    && ./gradlew :backend:bootJar --no-daemon -q -x test \
    && cp "$(ls backend/build/libs/backend-*.jar | grep -v plain)" /src/nexus-server.jar

# ---- 2. Run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/nexus-server.jar /app/nexus-server.jar
COPY deploy/render/start.sh /app/start.sh
RUN sed -i 's/\r$//' /app/start.sh && chmod +x /app/start.sh
EXPOSE 8080
CMD ["/app/start.sh"]
