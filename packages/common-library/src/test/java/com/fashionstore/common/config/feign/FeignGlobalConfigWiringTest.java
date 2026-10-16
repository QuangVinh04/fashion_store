package com.fashionstore.common.config.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fashionstore.common.web.CorrelationIdFilter;
import com.sun.net.httpserver.HttpServer;
import feign.FeignException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * Chạy Spring Cloud OpenFeign thật với một máy chủ HTTP giả để chứng minh cấu hình chung được áp lên
 * client (không chỉ đúng trên mock): header correlation và timeout đọc 5 giây.
 */
class FeignGlobalConfigWiringTest {

    static HttpServer server;
    static ConfigurableApplicationContext context;
    static final List<String> correlationHeaders = new CopyOnWriteArrayList<>();

    @FeignClient(name = "echo-service", url = "${stub.url}", configuration = FeignGlobalConfig.class)
    interface EchoClient {
        @GetMapping("/echo")
        String echo();
    }

    @FeignClient(name = "slow-service", url = "${stub.url}", configuration = FeignGlobalConfig.class)
    interface SlowClient {
        @GetMapping("/slow")
        String slow();
    }

    @Configuration
    @EnableFeignClients(clients = {EchoClient.class, SlowClient.class})
    @Import({FeignAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class, JacksonAutoConfiguration.class})
    static class App {}

    @BeforeAll
    static void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            correlationHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst(CorrelationIdFilter.HEADER)));
            reply(exchange, 200, "ok");
        });
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(7_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            try {
                reply(exchange, 200, "late");
            } catch (IOException ignored) {
                // client đã bỏ cuộc vì timeout
            }
        });
        server.start();
        context = new SpringApplicationBuilder(App.class)
                .web(WebApplicationType.NONE)
                .properties("stub.url=http://127.0.0.1:" + server.getAddress().getPort())
                .run();
    }

    @AfterAll
    static void stop() {
        context.close();
        server.stop(0);
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void theCorrelationIdOfTheCurrentRequestReachesTheDownstreamService() {
        correlationHeaders.clear();
        EchoClient client = context.getBean(EchoClient.class);

        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-abc");
        try {
            client.echo();
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
        client.echo(); // thread không có ID: không gắn header

        assertThat(correlationHeaders).containsExactly("corr-abc", "null");
    }

    @Test
    void aSlowDownstreamIsCutOffByTheSharedReadTimeoutInsteadOfHangingForAMinute() {
        SlowClient client = context.getBean(SlowClient.class);
        long start = System.nanoTime();

        assertThatThrownBy(client::slow).isInstanceOf(FeignException.class);

        long seconds = (System.nanoTime() - start) / 1_000_000_000L;
        assertThat(seconds).isBetween(4L, 6L);
    }
}
