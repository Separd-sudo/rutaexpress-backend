FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY ms-rutaexpress-catalog/pom.xml ms-rutaexpress-catalog/
COPY ms-rutaexpress-catalog/src ms-rutaexpress-catalog/src
RUN mvn -pl ms-rutaexpress-catalog -am clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/ms-rutaexpress-catalog/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
