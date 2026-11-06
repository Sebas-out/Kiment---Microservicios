FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
RUN mvn -B -ntp -DskipTests package
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S kiments && adduser -S -G kiments kiments
USER kiments
WORKDIR /app
COPY --from=build /build/target/kiments-auth-service-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
