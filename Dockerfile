FROM maven:3.9-eclipse-temurin-17-alpine AS build

# Forzar invalidación de caché para los archivos de código
ARG CACHE_BUST=20260806

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk-alpine
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]