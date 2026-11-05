package com.fashionstore.keycloak.events;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * Bật cho realm ở Realm settings -> Events -> Event listeners (id {@value #ID}); realm-fashion-store.json đã bật sẵn.
 * Cấu hình RabbitMQ qua biến môi trường của Keycloak, ví dụ {@code KC_SPI_EVENTS_LISTENER__FASHION_RABBIT__HOST=rabbitmq}
 * (các khoá: host, port, username, password, vhost).
 */
public class RabbitEventListenerProviderFactory implements EventListenerProviderFactory {

    static final String ID = "fashion-rabbit";

    private static final Logger LOG = Logger.getLogger(RabbitEventListenerProviderFactory.class);

    private RabbitEventPublisher publisher;

    @Override
    public void init(Config.Scope config) {
        RabbitSettings settings = new RabbitSettings(
                config.get("host", "localhost"),
                config.getInt("port", 5672),
                config.get("username", "guest"),
                config.get("password", "guest"),
                config.get("vhost", "/"));
        publisher = new RabbitEventPublisher(settings);
        LOG.infof("Event listener '%s' publishes REGISTER / UPDATE_EMAIL to RabbitMQ at %s:%d (vhost %s)",
                ID, settings.host(), settings.port(), settings.virtualHost());
    }

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new RabbitEventListenerProvider(session, publisher);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Không cần gì sau khi mọi provider đã khởi tạo
    }

    @Override
    public void close() {
        if (publisher != null) {
            publisher.close();
        }
    }

    @Override
    public String getId() {
        return ID;
    }
}
