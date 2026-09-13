package com.fashionstore.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayUserContextFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLES_HEADER = "X-User-Roles";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .map(authentication -> mutateRequest(exchange, authentication))
                .defaultIfEmpty(mutateRequest(exchange, null))
                .flatMap(request -> chain.filter(exchange.mutate().request(request).build()));
    }

    private ServerHttpRequest mutateRequest(
            ServerWebExchange exchange,
            JwtAuthenticationToken authentication
    ) {
        return exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(HttpHeaders.AUTHORIZATION);
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLES_HEADER);
                    if (authentication != null) {
                        headers.set(USER_ID_HEADER, authentication.getToken().getSubject());
                        String scope = authentication.getToken().getClaimAsString("scope");
                        if (scope != null && !scope.isBlank()) {
                            headers.set(USER_ROLES_HEADER, scope);
                        }
                    }
                })
                .build();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}