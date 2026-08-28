package kn.org.deliverybackend.config;

import jakarta.servlet.http.HttpServletResponse;
import kn.org.deliverybackend.security.CustomerJwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

/**
 * Two-chain security setup:
 *
 *   Chain 1  (order 1)  /admin/**         → Keycloak JWT (OAuth2 resource server)
 *   Chain 2  (order 2)  everything else   → Spring-issued customer JWT
 *
 * Anonymous (guest) traffic remains allowed on public endpoints and on the
 * subset of /app/consumer/** that supports guest checkout.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomerJwtAuthFilter customerJwtAuthFilter;

    // ── Chain 1: Admin (Keycloak) ──────────────────────────────────────────────
    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                // Admin chain (Keycloak) owns /admin/** plus all *mutations* of the
                // catalog endpoints — GETs stay public on the storefront chain.
                .securityMatcher(new OrRequestMatcher(
                        new AntPathRequestMatcher("/admin/**"),
                        new AntPathRequestMatcher("/api/products/**", "POST"),
                        new AntPathRequestMatcher("/api/products/**", "PUT"),
                        new AntPathRequestMatcher("/api/products/**", "DELETE"),
                        new AntPathRequestMatcher("/api/categories/**", "POST"),
                        new AntPathRequestMatcher("/api/categories/**", "PUT"),
                        new AntPathRequestMatcher("/api/categories/**", "DELETE"),
                        new AntPathRequestMatcher("/api/banners/**", "POST"),
                        new AntPathRequestMatcher("/api/banners/**", "PUT"),
                        new AntPathRequestMatcher("/api/banners/**", "DELETE")
                ))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/admin/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    // ── Chain 2: Storefront + public APIs (custom JWT) ─────────────────────────
    @Bean
    @Order(2)
    public SecurityFilterChain storefrontSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Always allow CORS preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public storefront endpoints
                        .requestMatchers("/app/auth/**").permitAll()
                        .requestMatchers("/app/home").permitAll()
                        .requestMatchers("/api/products/**").permitAll()
                        .requestMatchers("/api/categories/**").permitAll()
                        .requestMatchers("/api/banners/**").permitAll()
                        .requestMatchers("/api/search/**").permitAll()
                        .requestMatchers("/api/coupons/validate").permitAll()
                        .requestMatchers("/api/shipping/**").permitAll()
                        .requestMatchers("/api/checkout/quote").permitAll()
                        .requestMatchers("/api/payments/bkash/**").permitAll()
                        .requestMatchers("/api/support/contact").permitAll()
                        .requestMatchers("/api/newsletter/**").permitAll()
                        // Custom design studio: assets, artwork upload, quote
                        // requests and shared-design lookup are all guest-usable.
                        .requestMatchers("/api/custom-design/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/store-settings").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/discounts/active").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/flash-sale").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/flash-sales/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/bundle").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/bundles/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()

                        // Guest checkout: placing an order without an account is allowed.
                        // The endpoint itself must accept guest payloads (email + shipping
                        // address inline) and skip userId-based lookups in that case.
                        .requestMatchers(HttpMethod.POST, "/app/consumer/guest/orders").permitAll()

                        // Authenticated customer endpoints
                        .requestMatchers("/app/consumer/**").authenticated()

                        // Default-deny: anything not explicitly allowed above is
                        // rejected rather than silently public.
                        .anyRequest().denyAll()
                )
                // Unauthenticated requests (missing/expired/invalid customer JWT)
                // must return 401 — not the servlet default 403 — so the storefront
                // interceptor can refresh the access token and replay the request.
                // Genuine business "forbidden" cases still return 403 via the
                // controllers/GlobalExceptionHandler.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authEx) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                // Install our custom JWT filter before the standard auth filter so
                // tokens minted by JwtTokenProvider populate the SecurityContext.
                .addFilterBefore(customerJwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
