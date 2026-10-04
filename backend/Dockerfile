# syntax=docker/dockerfile:1.7
# RepairTrack backend: production image.
#   build:   Maven wrapper + JDK 21, tests are NOT run here (CI runs them before an image is built)
#   runtime: JRE 21 only, non-root, configuration exclusively through environment variables
# Build from the backend/ directory:  docker build -t repairtrack-backend .

FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace
# Dependencies first, so source changes do not invalidate the dependency layer.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw --batch-mode --no-transfer-progress dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress -DskipTests package \
    && cp target/repairtrack-backend-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-noble AS runtime
# curl only for the container health check.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 repairtrack \
    && useradd --system --uid 10001 --gid repairtrack --home-dir /app --shell /usr/sbin/nologin repairtrack
WORKDIR /app
COPY --from=build --chown=repairtrack:repairtrack /workspace/app.jar /app/app.jar
USER repairtrack:repairtrack

# Container-aware heap (75% of the container memory limit); crash fast on OOM so Docker restarts the container.
# Override or extend with JAVA_TOOL_OPTIONS in the environment (the JVM reads it itself).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom" \
    SERVER_PORT=8080 \
    TZ=UTC

EXPOSE 8080

# Ready = application started and database reachable (see management.endpoint.health.group.readiness).
HEALTHCHECK --interval=10s --timeout=3s --start-period=90s --retries=6 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

# Exec form: java is PID 1 and receives SIGTERM directly (graceful shutdown).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Traceability (overwritten with exact values by the CI build).
ARG VERSION=dev
ARG REVISION=unknown
LABEL org.opencontainers.image.title="repairtrack-backend" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}"
