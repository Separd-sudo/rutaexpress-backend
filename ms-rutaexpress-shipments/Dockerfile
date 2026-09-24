FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY ms-rutaexpress-shipments/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]