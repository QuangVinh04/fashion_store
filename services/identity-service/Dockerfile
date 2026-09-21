FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/identity-service-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
