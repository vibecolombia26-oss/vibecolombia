FROM maven:3.9-eclipse-temurin-17-alpine AS build

ARG CACHE_BUST=20260806

WORKDIR /app

# Copiar solo el pom.xml primero para cachear dependencias
COPY pom.xml .
RUN mvn dependency:go-offline

# Copiar todo el código fuente (esto se invalida con CACHE_BUST)
COPY src ./src

# Limpiar caché de Maven y compilar desde cero
RUN mvn clean package -DskipTests

# Verificar que el archivo HTML está en el JAR (opcional, para diagnóstico)
RUN jar tf target/*.jar | grep producto-detalle.html || echo "⚠️ El archivo no está en el JAR"

FROM eclipse-temurin:17-jdk-alpine
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]