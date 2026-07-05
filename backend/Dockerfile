# Build stage - use maven image to build (multi-arch support)
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn -f pom.xml clean package -DskipTests -q

# Runtime stage - use slim JRE (multi-arch: amd64 + arm64)
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Create non-root user for security
RUN groupadd -r lms && useradd -r -g lms lms
USER lms

# Copy jar from build stage
COPY --from=build /app/target/adaptive-lms-backend-*.jar app.jar

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=5 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=70.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
