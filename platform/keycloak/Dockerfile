FROM quay.io/keycloak/keycloak:26.7.4
# Event Listener SPI: REGISTER / UPDATE_EMAIL -> RabbitMQ. Build jar trước (giống các service):
#   ./mvnw -pl platform/keycloak/event-listener -am package -DskipTests
COPY event-listener/target/keycloak-rabbit-event-listener.jar /opt/keycloak/providers/
