package com.fashionstore.test.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Không để secret dev chạy thầm lặng trên production: mọi khóa kiểu bí mật có giá trị mặc định trong
 * {@code application.yaml} (vd {@code ${X_PASSWORD:root}}) bắt buộc phải được {@code
 * application-prod.yaml} ghi đè bằng {@code ${X_PASSWORD}} không mặc định, để thiếu biến môi trường
 * thì service từ chối khởi động thay vì dùng giá trị dev.
 */
class ProdProfileSecretsTest {

    private static final List<String> MODULES =
            List.of(
                    "services/catalog-service",
                    "services/identity-service",
                    "services/notification-service",
                    "services/order-service",
                    "services/payment-service",
                    "platform/gateway",
                    "platform/storefront-bff",
                    "platform/backoffice-bff");

    private static final Pattern SECRET_LIKE =
            Pattern.compile("(?i).*(secret|password|passwd|token|key|username|tmn-code|client-id)$");
    /** ${NAME:default} (kể cả default rỗng). */
    private static final Pattern WITH_DEFAULT = Pattern.compile("^\\$\\{[A-Za-z0-9_]+:.*}$");
    /** ${NAME} không mặc định. */
    private static final Pattern REQUIRED = Pattern.compile("^\\$\\{[A-Za-z0-9_]+}$");

    private static Path root() {
        // Maven chạy test với thư mục làm việc là module (packages/test-utils)
        return Path.of("").toAbsolutePath().getParent().getParent();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> flatten(Path file) throws IOException {
        Map<String, Object> flat = new TreeMap<>();
        try (var in = Files.newBufferedReader(file)) {
            for (Object document : new Yaml().loadAll(in)) {
                if (document instanceof Map<?, ?> map) {
                    flatten("", (Map<String, Object>) map, flat);
                }
            }
        }
        return flat;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> node, Map<String, Object> out) {
        node.forEach(
                (key, value) -> {
                    String path = prefix.isEmpty() ? key : prefix + "." + key;
                    if (value instanceof Map<?, ?> child) {
                        flatten(path, (Map<String, Object>) child, out);
                    } else {
                        out.put(path, value);
                    }
                });
    }

    private static String lastSegment(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }

    @Test
    void everySecretWithADevDefaultIsOverriddenWithoutADefaultInTheProdProfile() throws IOException {
        for (String module : MODULES) {
            Path resources = root().resolve(module).resolve("src/main/resources");
            Map<String, Object> base = flatten(resources.resolve("application.yaml"));
            List<String> secretsWithDefault =
                    base.entrySet().stream()
                            .filter(e -> SECRET_LIKE.matcher(lastSegment(e.getKey())).matches())
                            .filter(e -> e.getValue() instanceof String value && WITH_DEFAULT.matcher(value).matches())
                            .map(Map.Entry::getKey)
                            .toList();
            if (secretsWithDefault.isEmpty()) {
                continue;
            }

            Path prodFile = resources.resolve("application-prod.yaml");
            assertThat(prodFile).as("%s must have application-prod.yaml", module).exists();
            Map<String, Object> prod = flatten(prodFile);
            for (String key : secretsWithDefault) {
                assertThat(prod.get(key))
                        .as("%s: %s must be required (no default) in the prod profile", module, key)
                        .isInstanceOfSatisfying(
                                String.class, value -> assertThat(REQUIRED.matcher(value).matches()).isTrue());
            }
        }
    }

    @Test
    void theProdProfileNeverIntroducesADefaultForAnything() throws IOException {
        for (String module : MODULES) {
            Path prodFile = root().resolve(module).resolve("src/main/resources/application-prod.yaml");
            if (!Files.exists(prodFile)) {
                continue;
            }
            flatten(prodFile)
                    .forEach(
                            (key, value) ->
                                    assertThat(value instanceof String text && WITH_DEFAULT.matcher(text).matches())
                                            .as("%s: %s must not carry a default in the prod profile", module, key)
                                            .isFalse());
        }
    }

    /**
     * Dùng đúng bộ phân giải placeholder của Spring (không phải chỉ đọc chữ trong YAML): thiếu biến
     * môi trường thì phải lỗi, đủ biến thì ra đúng giá trị. MockEnvironment không có biến môi trường
     * của máy chạy test nên kết quả không phụ thuộc vào nơi chạy.
     */
    @Test
    void withoutTheEnvironmentVariablesTheProdProfileFailsToResolveAndWithThemItResolves() throws IOException {
        var loader = new org.springframework.boot.env.YamlPropertySourceLoader();
        for (String module : MODULES) {
            Path resources = root().resolve(module).resolve("src/main/resources");
            Path prodFile = resources.resolve("application-prod.yaml");
            if (!Files.exists(prodFile)) {
                continue;
            }
            Map<String, Object> prod = flatten(prodFile);

            var missing = new org.springframework.mock.env.MockEnvironment();
            addYaml(loader, missing, prodFile);
            addYaml(loader, missing, resources.resolve("application.yaml"));
            for (String key : prod.keySet()) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> missing.getProperty(key))
                        .as("%s: %s must not resolve without its environment variable", module, key)
                        .isInstanceOf(IllegalArgumentException.class);
            }

            var supplied = new org.springframework.mock.env.MockEnvironment();
            prod.forEach(
                    (key, placeholder) ->
                            supplied.setProperty(
                                    placeholder.toString().replaceAll("^\\$\\{|}$", ""), "value-of-" + key));
            addYaml(loader, supplied, prodFile);
            addYaml(loader, supplied, resources.resolve("application.yaml"));
            prod.keySet().forEach(key -> assertThat(supplied.getProperty(key)).isEqualTo("value-of-" + key));
        }
    }

    private static void addYaml(
            org.springframework.boot.env.YamlPropertySourceLoader loader,
            org.springframework.mock.env.MockEnvironment environment,
            Path file)
            throws IOException {
        for (var source : loader.load(file.toString(), new org.springframework.core.io.FileSystemResource(file))) {
            environment.getPropertySources().addLast(source);
        }
    }
}
