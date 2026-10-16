package com.fashionstore.test.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Realm export không được chứa secret/mật khẩu cứng: client secret, mật khẩu user dev phải là
 * placeholder {@code ${VAR:default}} và user dev có thể tắt bằng biến môi trường.
 */
class KeycloakRealmSecretsTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{[A-Z0-9_]+:.*}$");

    private static boolean placeholder(JsonNode n) {
        return n != null && PLACEHOLDER.matcher(n.asText()).matches();
    }

    @Test
    void secretsAndDevUsersAreEnvDriven() throws Exception {
        Path file = Path.of("").toAbsolutePath().getParent().getParent()
                .resolve("platform/keycloak/realm-fashion-store.json");
        JsonNode realm = new ObjectMapper().readTree(Files.readString(file));

        List<String> hardcoded = new ArrayList<>();
        for (JsonNode c : realm.path("clients")) {
            if (c.has("secret") && !placeholder(c.get("secret"))) {
                hardcoded.add("client " + c.path("clientId").asText());
            }
        }
        for (JsonNode u : realm.path("users")) {
            String name = u.path("username").asText();
            for (JsonNode cred : u.path("credentials")) {
                if (!placeholder(cred.get("value"))) {
                    hardcoded.add("password of " + name);
                }
            }
            if (!name.startsWith("service-account-") && !placeholder(u.get("enabled"))) {
                hardcoded.add("enabled of " + name);
            }
        }
        assertThat(hardcoded).as("hard-coded values in realm-fashion-store.json").isEmpty();
    }
}
