# Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Copy build definition and source files
COPY pom.xml .
COPY src ./src

# Build the executable Spring Boot fat JAR without running tests
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-privileged application user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy the compiled JAR artifact from builder stage
COPY --from=builder /workspace/target/ticketrush-0.0.1-SNAPSHOT.jar app.jar

# Expose default port (Render will dynamically bind $PORT)
EXPOSE 8088

# JVM flags optimized for container memory constraints (Render 512MB Free Tier)
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
