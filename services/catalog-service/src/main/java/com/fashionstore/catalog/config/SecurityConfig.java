package com.fashionstore.catalog.config;

import com.fashionstore.common.security.ApiAccessDeniedHandler;
import com.fashionstore.common.security.ApiAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Gộp ba {@code SecurityConfig} của product-service, inventory-service và file-service.
 *
 * <p>Một Spring context chỉ nhận một tập bean, mà ba service cũ có ba luật
 * {@code anyRequest()} khác nhau (product: ADMIN, inventory: ADMIN, file: chỉ cần
 * đăng nhập). Nên thay vì trộn tất cả vào một chain — việc bắt buộc phải chọn một
 * {@code anyRequest()} duy nhất và do đó làm đổi quyền của ai đó — mỗi domain giữ
 * một chain riêng, khoanh vùng bằng {@code securityMatcher}. Luật bên trong từng
 * chain được sao lại nguyên văn từ service cũ.
 *
 * <p>Thứ tự chain quan trọng: chain có {@code securityMatcher} phải đứng trước
 * chain bao trùm ({@link #catalogFilterChain}) vốn không khoanh vùng gì.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Nguyên văn từ file-service: nội dung file công khai, còn lại chỉ cần đăng nhập. */
    @Bean
    @Order(1)
    SecurityFilterChain mediaFilterChain(
            HttpSecurity http,
            CustomJwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtConverter,
            CorsConfigurationSource corsConfigurationSource,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        return http
                .securityMatcher("/api/v1/files/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/v1/files/*/content").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .build();
    }

    /** Nguyên văn từ inventory-service. {@code /internal/v1/**} chưa có controller nào. */
    @Bean
    @Order(2)
    SecurityFilterChain inventoryFilterChain(
            HttpSecurity http,
            CustomJwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtConverter,
            CorsConfigurationSource corsConfigurationSource,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        return http
                .securityMatcher("/api/v1/inventory/**", "/internal/v1/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/inventory/availability/**").permitAll()
                        .requestMatchers("/internal/v1/**").hasAuthority("internal")
                        .anyRequest().hasRole("ADMIN"))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .build();
    }

    /**
     * Nguyên văn từ product-service, cộng thêm hai đường swagger mà inventory và file
     * vốn mở (không có springdoc trên classpath nên hiện là luật vô hại). Chain này
     * không bật CORS vì product-service trước đây cũng không bật.
     */
    @Bean
    @Order(3)
    SecurityFilterChain catalogFilterChain(
            HttpSecurity http,
            CustomJwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtConverter,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/products/**",
                                "/api/v1/product/**",
                                "/api/v1/categories/**",
                                "/api/v1/category/**").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }

    /** Ba service cũ đều dùng bản giống nhau: bỏ tiền tố ROLE_ mặc định. */
    @Bean
    JwtAuthenticationConverter jwtConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    /**
     * Hai cấu hình CORS cũ khác nhau ở method và exposed header, nên đăng ký theo
     * đường dẫn thay vì chọn một cái: {@code /api/v1/files/**} lấy bản của
     * file-service, phần còn lại lấy bản của inventory-service. Pattern cụ thể phải
     * đăng ký trước vì {@link UrlBasedCorsConfigurationSource} trả về pattern khớp đầu tiên.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/files/**", corsConfig(
                List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"),
                List.of("Authorization", "Content-Disposition")));
        source.registerCorsConfiguration("/**", corsConfig(
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"),
                List.of("Authorization")));
        return source;
    }

    private CorsConfiguration corsConfig(List<String> methods, List<String> exposedHeaders) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173"));
        config.setAllowedMethods(methods);
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(exposedHeaders);
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        return config;
    }
}
