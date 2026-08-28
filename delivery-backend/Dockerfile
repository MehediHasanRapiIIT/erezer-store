# syntax=docker/dockerfile:1.7
# ============================================================================
# Erezer backend — multi-stage build
# Stage 1 builds a Spring Boot fat jar; Stage 2 runs it on a slim JRE.
# ============================================================================

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Layer caching: copy gradle wrapper + descriptor first so dependency
# resolution is cached across code-only changes.
COPY gradle gradle
COPY gradlew gradlew.bat settings.gradle* build.gradle ./
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test


FROM eclipse-temurin:21-jre-alpine AS runtime
# Tiny non-root user so we don't run as PID 0.
RUN addgroup -S erezer && adduser -S erezer -G erezer
WORKDIR /app

# Single fat jar; expand on demand via Spring layers if you want finer caching.
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=local

USER erezer
EXPOSE 8080

# Liveness/readiness probes target Spring Boot's Actuator if you add it later.
# For now a no-arg start is fine.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
