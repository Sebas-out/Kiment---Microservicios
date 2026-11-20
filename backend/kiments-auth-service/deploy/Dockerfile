# ==============================================================================
# KIMENTS POS — SVC-09 (kiments-auth-service)
# Dockerfile Multi-Etapa de Produccion para Linux x64 (Ubuntu / Azure)
# ==============================================================================

# ------------------------------------------------------------------------------
# ETAPA 1: Compilacion con Maven y OpenJDK 21
# ------------------------------------------------------------------------------
FROM maven:3.9.11-eclipse-temurin-21 AS builder

WORKDIR /build

# 1. Copiar descriptor de dependencias para aprovechar cache de Docker
COPY pom.xml .

# 2. Descargar dependencias en capa cacheada
RUN mvn dependency:go-offline -B --no-transfer-progress

# 3. Copiar codigo fuente y compilar paquete ejecutable
COPY src ./src
RUN mvn clean package -DskipTests -B --no-transfer-progress

# ------------------------------------------------------------------------------
# ETAPA 2: Imagen de Ejecucion Ligera y Segura (JRE 21 Jammy)
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

LABEL maintainer="Kiments Engineering Team"
LABEL service="kiments-auth-service"
LABEL version="0.1.0"

# Instalar curl para Healthcheck y certificados CA
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Crear usuario y grupo de sistema sin privilegios de root (UID 10001, GID 10001)
RUN groupadd -g 10001 kiments && \
    useradd -u 10001 -g kiments -m -d /home/kiments -s /bin/bash kiments

WORKDIR /app

# Crear directorios para persistencia de fotografias y montaje de claves externas
RUN mkdir -p /app/storage/profiles /app/keys && \
    chown -R kiments:kiments /app

# Copiar el artefacto JAR empaquetado desde la etapa de compilacion
COPY --from=builder --chown=kiments:kiments /build/target/kiments-auth-service-0.1.0.jar /app/app.jar

# Cambiar al usuario no root
USER 10001:10001

# Puerto interno del servicio
EXPOSE 8080

# Variables de entorno JVM adaptadas a contenedores y cgroups
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:InitialRAMPercentage=50.0 \
               -Djava.awt.headless=true \
               -Dfile.encoding=UTF-8 \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

# Healthcheck oficial contra el endpoint Actuator
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f -s http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# Ejecutar proceso Java como PID 1 para propagacion limpia de senales (SIGTERM graceful shutdown)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
