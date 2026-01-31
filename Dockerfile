# ===========================================
# Stage 1: Build Frontend
# ===========================================
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend

# Copy frontend source
COPY src/main/frontend/package*.json ./
RUN npm ci

COPY src/main/frontend/ ./
RUN npm run build

# ===========================================
# Stage 2: Build Backend
# ===========================================
FROM eclipse-temurin:21-jdk-alpine AS backend-build
WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B -Dfrontend.skip=true

# Copy backend source (without frontend)
COPY src/main/java src/main/java
COPY src/main/resources src/main/resources

# Copy pre-built frontend
COPY --from=frontend-build /app/frontend/dist src/main/resources/static

# Build application (skip frontend plugin since we already built it)
RUN ./mvnw package -DskipTests -B -Dfrontend.skip=true

# ===========================================
# Stage 3: Runtime
# ===========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -g 1000 n3n && adduser -u 1000 -G n3n -D n3n

# Copy JAR from build stage
COPY --from=backend-build /app/target/*.jar app.jar

# Set ownership
RUN chown -R n3n:n3n /app

# Switch to non-root user
USER n3n

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
