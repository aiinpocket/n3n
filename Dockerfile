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

# Extract Spring Boot layered JAR (splits into dependencies / app)
# Spring Boot 3.3+/4 使用 -Djarmode=tools（layertools 已移除）；
# --layers --launcher 產生與舊 layertools 相同的四層目錄結構供 JarLauncher 啟動
RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination /app/extracted

# ===========================================
# Stage 3: Runtime
# ===========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -g 1000 n3n && adduser -u 1000 -G n3n -D n3n

# 資料目錄（named volume 首次建立時會繼承此擁有者，避免 root-owned volume 導致寫入失敗）
RUN mkdir -p /app/data/artifacts /app/data/keys && chown -R n3n:n3n /app/data

# Copy layered JAR (each layer is a separate Docker layer)
# Order: least-changing → most-changing (best cache utilization)
COPY --from=backend-build --chown=n3n:n3n /app/extracted/dependencies/ ./
COPY --from=backend-build --chown=n3n:n3n /app/extracted/spring-boot-loader/ ./
COPY --from=backend-build --chown=n3n:n3n /app/extracted/snapshot-dependencies/ ./
COPY --from=backend-build --chown=n3n:n3n /app/extracted/application/ ./

# Switch to non-root user
USER n3n

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

# Run with Spring Boot launcher (required for layered JAR)
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
