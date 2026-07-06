# ---- Build stage ----
# Uses the project's own Gradle wrapper (9.4.1) for a reproducible build.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Cache dependencies first: copy build scripts + wrapper, then resolve.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Build the runnable distribution.
COPY src ./src
RUN ./gradlew --no-daemon installDist

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd -r -u 1001 appuser
COPY --from=build /app/build/install/shopping-agent-backend/ ./
USER appuser
EXPOSE 8080
ENTRYPOINT ["./bin/shopping-agent-backend"]
