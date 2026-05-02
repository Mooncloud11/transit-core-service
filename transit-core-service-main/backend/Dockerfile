# Stage 1: Build phase
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build the application and skip tests for faster container build
RUN mvn clean package -DskipTests

# Stage 2: Runtime phase
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Copy the compiled JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the API port
EXPOSE 8080
# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]