package com.fashionstore.catalog.config;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class MinioConfigTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void publicClientSignsUploadAndDownloadWithoutContactingPublicEndpoint(boolean sameEndpoint) throws Exception {
        // No server listens here: signing must be local even when both endpoints are the same.
        String publicEndpoint = "http://127.0.0.1:1";
        var properties = new MinioProperties(sameEndpoint ? publicEndpoint : "http://minio:9000",
                publicEndpoint, "test-access", "test-secret", "fashion-media", 900, 20971520);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(MinioProperties.class, () -> properties);
            context.register(MinioConfig.class);
            context.refresh();
            MinioClient client = context.getBean("presignMinioClient", MinioClient.class);
            for (Method method : new Method[]{Method.PUT, Method.GET}) {
                String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                        .method(method).bucket(properties.bucket()).object("avatars/test.png").expiry(900).build());
                assertThat(url).startsWith(publicEndpoint + "/fashion-media/avatars/test.png?")
                        .contains("%2Fus-east-1%2Fs3%2Faws4_request", "X-Amz-Signature=");
            }
        }
    }
}
