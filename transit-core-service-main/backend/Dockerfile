# 1. Aşama: Derleme (Build) Aşaması
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 2. Aşama: Çalıştırma (Run) Aşaması
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar



EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]