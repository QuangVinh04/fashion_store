FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY . .
RUN mvn -B -pl services/order-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/services/order-service/target/order-service-*.jar app.jar

EXPOSE 8089

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
