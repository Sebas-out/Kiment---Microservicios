# Build stage
FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Crear usuario sin privilegios para ejecución segura
RUN addgroup -S kiments && adduser -S kiments -G kiments \
    && mkdir -p /app/storage/catalog-images \
    && chown -R kiments:kiments /app
USER kiments:kiments

# Copiar artefacto empaquetado
COPY --from=build --chown=kiments:kiments /app/target/kiments-catalog-inventory-service-*.jar app.jar

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
