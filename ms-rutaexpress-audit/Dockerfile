FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY ms-rutaexpress-audit/pom.xml ms-rutaexpress-audit/
COPY ms-rutaexpress-audit/src ms-rutaexpress-audit/src
RUN mvn -pl ms-rutaexpress-audit -am clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/ms-rutaexpress-audit/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]