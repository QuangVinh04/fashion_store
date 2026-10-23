package com.fashionstore.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature modules (product, inventory, media, review, wishlist) may only use each other's exposed API.
 * Existing coupling is frozen in modulith-known-violations.txt: new violations fail, fixed ones must be removed.
 */
class ModularityTest {

    private static final String BASE = CatalogServiceApplication.class.getPackageName() + ".";

    private final ApplicationModules modules = ApplicationModules.of(CatalogServiceApplication.class);

    @Test
    void moduleViolationsMatchKnownBaseline() throws IOException {
        List<String> current = modules.detectViolations().getMessages().stream()
                .map(ModularityTest::normalize)
                .distinct()
                .toList();

        assertThat(current).containsExactlyInAnyOrderElementsOf(knownViolations());
    }

    /** First line of a violation (cycles: the slice path only), without location details or base package. */
    private static String normalize(String message) {
        String head = message.split("\\R\\s*1\\. Dependencies", 2)[0];
        if (!head.startsWith("Cycle")) {
            head = head.lines().findFirst().orElse(head);
        }
        return head.replaceAll("\\s+", " ").replace(BASE, "").trim();
    }

    private static List<String> knownViolations() throws IOException {
        try (InputStream in = ModularityTest.class.getResourceAsStream("/modulith-known-violations.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        }
    }
}
