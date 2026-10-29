# 1. Fase de construcción (Build) con JDK 21
FROM gradle:8.5-jdk21-alpine AS builder
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY src src

# Dar permisos de ejecución a gradlew
RUN chmod +x gradlew

# Compilar el JAR ignorando tests
RUN ./gradlew bootJar --no-daemon -x test

# 2. Fase de ejecución (Runtime) con JRE 21
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]