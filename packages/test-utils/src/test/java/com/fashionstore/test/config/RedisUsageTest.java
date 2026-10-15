package com.fashionstore.test.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Một khuôn dùng Redis cho cả repo: mã của service/platform chỉ gọi {@code RedisService} của
 * common-library (fail-open, TTL bắt buộc, key có namespace). Tự import template/operations của Spring
 * Data Redis ở đây là đi vòng khuôn đó. Ngoại lệ duy nhất là session, do Spring Session tự quản lý và
 * không đi qua mã của repo.
 */
class RedisUsageTest {

    private static final Pattern RAW_REDIS =
            Pattern.compile(
                    "^import\\s+org\\.springframework\\.data\\.redis\\.core\\."
                            + "(StringRedisTemplate|RedisTemplate|ReactiveRedisTemplate|ReactiveStringRedisTemplate"
                            + "|RedisOperations|ValueOperations|HashOperations|ListOperations|SetOperations|ZSetOperations)\\b");

    private static final List<String> SCANNED_ROOTS = List.of("services", "platform");

    private static Path root() {
        return Path.of("").toAbsolutePath().getParent().getParent();
    }

    @Test
    void servicesAndPlatformUseRedisOnlyThroughRedisService() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = SCANNED_ROOTS.stream().flatMap(this::mainJavaFiles)) {
            offenders =
                    files.filter(RedisUsageTest::importsRawRedis)
                            .map(path -> root().relativize(path).toString())
                            .sorted()
                            .toList();
        }

        assertThat(offenders)
                .as("use com.fashionstore.common.redis.RedisService instead of Spring Data Redis templates")
                .isEmpty();
    }

    private Stream<Path> mainJavaFiles(String top) {
        Path base = root().resolve(top);
        if (!Files.isDirectory(base)) {
            return Stream.empty();
        }
        try {
            return Files.walk(base)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/target/"));
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    private static boolean importsRawRedis(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.anyMatch(line -> RAW_REDIS.matcher(line.strip()).find());
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }
}
