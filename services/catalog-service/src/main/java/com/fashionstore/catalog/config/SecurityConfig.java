package com.fashionstore.catalog.config;

import com.fashionstore.common.security.ApiAccessDeniedHandler;
import com.fashionstore.common.security.ApiAuthenticationEntryPoint;
import com.fashionstore.common.security.GatewayHeaderAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // 1. CORS Preflight & Hệ thống (Công khai)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // 2. Domain Media / File: Xem ảnh công khai, các thao tác file khác chỉ cần đăng nhập
                        .requestMatchers(HttpMethod.GET, "/api/v1/files/*/content").permitAll()
                        .requestMatchers("/api/v1/files/**").authenticated()

                        // 3. Domain Inventory: Khách xem giỏ hàng checkStock cần đăng nhập, internal Saga cần quyền internal
                        .requestMatchers(HttpMethod.POST, "/api/v1/inventory/check").authenticated()
                        .requestMatchers("/internal/v1/**").hasAuthority("internal")
                        .requestMatchers(HttpMethod.GET, "/api/v1/inventory/**").hasRole("ADMIN")

                        // 4. Domain Product / Catalog: Khách xem sản phẩm, danh mục công khai
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/products/**",
                                "/api/v1/product/**",
                                "/api/v1/categories/**",
                                "/api/v1/category/**",
                                "/api/v1/brands/**",
                                "/api/v1/color-options/**",
                                "/api/v1/size-options/**",
                                "/api/v1/size-charts/**").permitAll()

                        // 5. Mọi thao tác còn lại (Thêm/Sửa/Xóa sản phẩm, cập nhật kho...): Bắt buộc quyền ADMIN
                        .anyRequest().hasRole("ADMIN")
                )
                // Filter nhận diện User từ Gateway (Header: X-User-Id, X-User-Roles)
                .addFilterBefore(new GatewayHeaderAuthenticationFilter(), AuthorizationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Cấu hình CORS cho media files
        source.registerCorsConfiguration("/api/v1/files/**", corsConfig(
                List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"),
                List.of("Authorization", "Content-Disposition")
        ));
        // Cấu hình CORS chung cho toàn bộ catalog
        source.registerCorsConfiguration("/**", corsConfig(
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"),
                List.of("Authorization")
        ));
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