package com.jarvis.security;

import com.jarvis.config.JarvisProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.WebFilter;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http, JarvisProperties p,
            @Value("${jarvis.auth-mode:local}") String mode, @Value("${server.address:127.0.0.1}") String address) {
        boolean local = mode.equals("local");
        if (!local && !mode.equals("token")) throw new IllegalArgumentException("JARVIS_AUTH_MODE must be local or token");
        if (local && !java.util.Set.of("127.0.0.1", "::1", "localhost").contains(address))
            throw new IllegalArgumentException("Local mode requires a loopback server.address");
        if (!local && p.apiToken().length()<32) throw new IllegalArgumentException("Token mode requires JARVIS_API_TOKEN (32-256 characters)");
        WebFilter localGuard = (exchange, chain) -> {
            if (!local || !exchange.getRequest().getPath().value().startsWith("/api/")) return chain.filter(exchange);
            var request=exchange.getRequest(); var remote=request.getRemoteAddress(); var uri=request.getURI();
            String origin=request.getHeaders().getOrigin();
            String expectedOrigin=uri.getScheme()+"://"+uri.getRawAuthority();
            boolean loopbackAllowed = remote!=null && remote.getAddress()!=null && remote.getAddress().isLoopbackAddress()
                && java.util.Set.of("127.0.0.1","localhost","[::1]").contains(uri.getHost())
                && (origin==null || origin.equals(expectedOrigin))
                && !"cross-site".equals(request.getHeaders().getFirst("Sec-Fetch-Site"));
            boolean allowed = loopbackAllowed && "1".equals(request.getHeaders().getFirst("X-Jarvis-Local"));
            String path = request.getPath().value();
            boolean isVisual = path.startsWith("/api/v1/realtime/chart") || path.startsWith("/api/v1/realtime/cinema-card");
            if(allowed || path.equals("/api/v1/health/live") || (loopbackAllowed && isVisual)) return chain.filter(exchange);
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN); return exchange.getResponse().setComplete();
        };
        byte[] expected = p.apiToken().getBytes(StandardCharsets.UTF_8);
        var auth = new AuthenticationWebFilter((org.springframework.security.authentication.ReactiveAuthenticationManager) authentication -> {
            var supplied = authentication.getCredentials().toString().getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected, supplied)) return Mono.error(new BadCredentialsException("Invalid token"));
            return Mono.just(new UsernamePasswordAuthenticationToken("local-user", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        });
        auth.setServerAuthenticationConverter(exchange -> {
            if (local) return Mono.empty();
            String header = exchange.getRequest().getHeaders().getFirst("Authorization");
            return header != null && header.startsWith("Bearer ")
                    ? Mono.just(new UsernamePasswordAuthenticationToken("local-user", header.substring(7))) : Mono.empty();
        });
        auth.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        auth.setAuthenticationFailureHandler((filter, error) -> {
            filter.getExchange().getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return filter.getExchange().getResponse().setComplete();
        });
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable).formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; connect-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: https: blob:; media-src 'self' data: blob:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'")))
                .authorizeExchange(a -> a.pathMatchers(org.springframework.http.HttpMethod.GET, "/", "/index.html", "/assets/**", "/api/v1/realtime/chart", "/api/v1/realtime/cinema-card").permitAll()
                        .pathMatchers("/api/v1/health/live", "/api/v1/session").permitAll()
                        .anyExchange().access((authentication, context) -> local
                            ? Mono.just(new org.springframework.security.authorization.AuthorizationDecision(true))
                            : authentication.map(a2 -> new org.springframework.security.authorization.AuthorizationDecision(a2.isAuthenticated()))
                                .defaultIfEmpty(new org.springframework.security.authorization.AuthorizationDecision(false))))
                .exceptionHandling(e -> e.authenticationEntryPoint((exchange, error) -> {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                })).addFilterBefore(localGuard, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAt(auth, SecurityWebFiltersOrder.AUTHENTICATION).build();
    }
}
