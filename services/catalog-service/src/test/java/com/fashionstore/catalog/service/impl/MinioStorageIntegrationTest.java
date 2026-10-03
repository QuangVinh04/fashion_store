package com.fashionstore.catalog.service.impl;

import com.fashionstore.catalog.config.MinioProperties;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Optional real storage test; creates and removes its own isolated bucket. */
@EnabledIfEnvironmentVariable(named = "AVATAR_TEST_MINIO_ENDPOINT", matches = ".+")
class MinioStorageIntegrationTest {
    @Test void conditionalUploadCannotOverwriteVerifiedObjectAndAllowsBrowserCors() throws Exception {
        String endpoint = System.getenv("AVATAR_TEST_MINIO_ENDPOINT");
        String key = System.getenv("AVATAR_TEST_MINIO_ACCESS_KEY");
        String secret = System.getenv("AVATAR_TEST_MINIO_SECRET_KEY");
        String bucket = "avatar-verify-" + UUID.randomUUID();
        var client = MinioClient.builder().endpoint(endpoint).region("us-east-1").credentials(key, secret).build();
        var storage = new MinioStorageService(client, client,
                new MinioProperties(endpoint, endpoint, key, secret, bucket, 900, 5L * 1024 * 1024));
        String object = "avatars/image.png";
        try {
            URI upload = URI.create(storage.presignUpload(object, "image/png", Map.of("If-None-Match", "*")).url());
            var http = HttpClient.newHttpClient();
            byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j90kAAAAASUVORK5CYII=");
            var request = HttpRequest.newBuilder(upload).header("Content-Type", "image/png")
                    .header("If-None-Match", "*").PUT(HttpRequest.BodyPublishers.ofByteArray(png)).build();
            assertThat(http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            assertThat(storage.stat(object).sizeBytes()).isEqualTo(png.length);
            assertThat(storage.readPrefix(object, 8)).containsExactly(Arrays.copyOf(png, 8));
            // Reusing the same URL with different bytes must fail after verification.
            var overwrite = HttpRequest.newBuilder(upload).header("Content-Type", "image/png")
                    .header("If-None-Match", "*").PUT(HttpRequest.BodyPublishers.ofString("replacement")).build();
            assertThat(http.send(overwrite, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(412);
            assertThat(storage.readPrefix(object, 8)).containsExactly(Arrays.copyOf(png, 8));
            var missingHeader = HttpRequest.newBuilder(upload).header("Content-Type", "image/png")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(png)).build();
            assertThat(http.send(missingHeader, HttpResponse.BodyHandlers.ofString()).statusCode()).isIn(400, 403);
            var preflight = HttpRequest.newBuilder(upload).header("Origin", "http://localhost:5173")
                    .header("Access-Control-Request-Method", "PUT")
                    .header("Access-Control-Request-Headers", "content-type,if-none-match")
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
            var cors = http.send(preflight, HttpResponse.BodyHandlers.ofString());
            assertThat(cors.statusCode()).isEqualTo(204);
            assertThat(cors.headers().firstValue("Access-Control-Allow-Origin").orElse("")).isEqualTo("http://localhost:5173");
            assertThat(cors.headers().firstValue("Access-Control-Allow-Headers").orElse("").toLowerCase(Locale.ROOT)).contains("if-none-match");
        } finally {
            storage.delete(object);
            client.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
        }
    }
}
