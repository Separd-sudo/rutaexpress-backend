FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY ms-rutaexpress-shipments/pom.xml ms-rutaexpress-shipments/
COPY ms-rutaexpress-shipments/src ms-rutaexpress-shipments/src
RUN mvn -pl ms-rutaexpress-shipments -am clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/ms-rutaexpress-shipments/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]