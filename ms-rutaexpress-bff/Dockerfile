FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY ms-rutaexpress-bff/pom.xml ms-rutaexpress-bff/
COPY ms-rutaexpress-bff/src ms-rutaexpress-bff/src
RUN mvn -pl ms-rutaexpress-bff -am clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/ms-rutaexpress-bff/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]