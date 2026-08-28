# syntax=docker/dockerfile:1.7
# ============================================================================
# Erezer backend - multi-stage build
# Stage 1 builds a Spring Boot fat jar; Stage 2 runs it on a slim JRE.
# ============================================================================

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Layer caching: copy the wrapper + build descriptors first so dependency
# resolution is cached across code-only changes.
COPY gradle gradle
COPY gradlew gradlew.bat settings.gradle* build.gradle ./
RUN chmod +x ./gradlew

# A persistent Gradle home makes rebuilds vastly faster - the cache survives
# between builds instead of re-downloading every dependency each time.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar -x test

# The jar name carries the version, so normalise it to a fixed path rather than
# relying on a glob at COPY time (a stale jar in build/libs would be ambiguous).
RUN cp build/libs/*-SNAPSHOT.jar /workspace/app.jar 2>/dev/null \
 || cp "$(ls -S build/libs/*.jar | head -n1)" /workspace/app.jar


FROM eclipse-temurin:21-jre-alpine AS runtime

# wget (busybox) drives the HEALTHCHECK below. curl is not in this image.
RUN addgroup -S erezer && adduser -S erezer -G erezer
WORKDIR /app

COPY --from=build /workspace/app.jar /app/app.jar
RUN chown erezer:erezer /app/app.jar

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"
# `docker` is the containerised-local profile. Compose sets this explicitly;
# the default here just makes a bare `docker run` behave sensibly.
ENV SPRING_PROFILES_ACTIVE=docker

USER erezer
EXPOSE 8080

# Readiness gate for the whole stack: compose uses `condition: service_healthy`
# against this so the frontends and proxy never race an unready API.
# start-period covers Flyway + Hibernate reconciling the schema on a cold boot.
# Probe 127.0.0.1, not localhost: localhost also resolves to ::1 inside
# the container, and a v6-first lookup can fail against a v4-only
# listener while the server is perfectly healthy.
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
