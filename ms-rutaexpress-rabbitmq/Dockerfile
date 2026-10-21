FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY ms-rutaexpress-rabbitmq/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
