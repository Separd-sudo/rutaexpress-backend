FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY ms-rutaexpress-audit/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]