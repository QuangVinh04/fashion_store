FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/storefront-bff-*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
