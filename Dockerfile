# ==============================================================================
# Multi-stage Dockerfile for EventEase Backend
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build stage with Maven 3.9 and Eclipse Temurin Java 21
# ------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy project descriptor first to leverage Docker layer caching for dependencies
COPY pom.xml .

# Download dependencies in an isolated layer
RUN mvn dependency:go-offline -B || true

# Copy application source code and compile executable Spring Boot JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 2: Runtime stage with Debian/Ubuntu-based Eclipse Temurin Java 21 JRE
# Uses Ubuntu Jammy (glibc) to guarantee native ONNX Runtime binary compatibility
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create unprivileged system group and user for secure non-root execution
RUN groupadd -r eventease && \
    useradd -r -g eventease -s /bin/false -d /app -M eventease && \
    chown -R eventease:eventease /app

# Copy the packaged Spring Boot executable fat JAR from the builder stage
COPY --from=builder --chown=eventease:eventease /build/target/backend-*.jar app.jar

# Switch to non-root user
USER eventease

# Expose backend port
EXPOSE 8080

# Launch Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
