package kn.org.deliverybackend.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import kn.org.deliverybackend.security.CustomerJwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Two-chain security setup:
 *
 *   Chain 1  (order 1)  /admin/** and catalogue changes  → Keycloak JWT (OAuth2 resource server)
 *   Chain 2  (order 2)  everything else                  → Spring-issued customer JWT
 *
 * Anonymous (guest) traffic remains allowed on public endpoints and on the
 * subset of /app/consumer/** that supports guest checkout.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Customer reviews live under /api/products/{id}/reviews but are written by
     * customers, not staff. Without this exclusion the admin chain claimed them
     * and rejected every customer's review as an invalid admin token.
     */
    private static final RequestMatcher CUSTOMER_REVIEWS =
            new AntPathRequestMatcher("/api/products/*/reviews/**");

    private final CustomerJwtAuthFilter customerJwtAuthFilter;

    // ── Chain 1: Admin (Keycloak) ──────────────────────────────────────────────
    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                // Admin chain (Keycloak) owns /admin/** plus all *mutations* of the
                // catalog endpoints — GETs stay public on the storefront chain —
                // and the live stock feed, which only the admin panel may use.
                .securityMatcher(new OrRequestMatcher(
                        new AntPathRequestMatcher("/admin/**"),
                        new AntPathRequestMatcher("/ws/**"),
                        new AndRequestMatcher(
                                new OrRequestMatcher(
                                        new AntPathRequestMatcher("/api/products/**", "POST"),
                                        new AntPathRequestMatcher("/api/products/**", "PUT"),
                                        new AntPathRequestMatcher("/api/products/**", "DELETE")),
                                new NegatedRequestMatcher(CUSTOMER_REVIEWS)),
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
                        // When a request is refused or fails, the servlet container
                        // forwards internally to /error to write the response. That
                        // step carries no login, so without this line it would be
                        // judged again as anonymous and every 403 would come back
                        // as 401, which the admin panel treats as "logged out".
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                        // Always allow CORS preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public storefront endpoints
                        .requestMatchers("/app/auth/**").permitAll()
                        .requestMatchers("/app/home").permitAll()

                        // Reviews: anyone may read them, only a logged-in customer
                        // may write, and the controller takes the author from the
                        // login, never from the request. Must precede the public
                        // /api/products/** rule below.
                        .requestMatchers(HttpMethod.POST, "/api/products/*/reviews").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/products/*/reviews/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/products/*/reviews/*").authenticated()

                        .requestMatchers("/api/products/**").permitAll()
                        .requestMatchers("/api/categories/**").permitAll()
                        .requestMatchers("/api/banners/**").permitAll()
                        .requestMatchers("/api/search/**").permitAll()
                        .requestMatchers("/api/coupons/validate").permitAll()
                        .requestMatchers("/api/shipping/**").permitAll()
                        .requestMatchers("/api/checkout/quote").permitAll()
                        .requestMatchers("/api/meta/**").permitAll()
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
                        // API docs: public here, switched off entirely in the prod
                        // profile (springdoc.*.enabled=false), so they 404 there.
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // Container/orchestrator health probe. Only /health is
                        // exposed (see management.endpoints.web.exposure.include)
                        // and it reports status without details, so this leaks
                        // nothing. Needed because anyRequest() below is denyAll.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()

                        // Guest checkout: placing an order without an account is allowed.
                        // The endpoint itself must accept guest payloads (email + shipping
                        // address inline) and skip userId-based lookups in that case.
                        .requestMatchers(HttpMethod.POST, "/app/consumer/guest/orders").permitAll()

                        // A customer may only reach their own data. Every customer
                        // address carries the customer id, so checking it here once
                        // covers orders, cart, addresses, profile, returns and
                        // design drafts, including endpoints added later.
                        .requestMatchers("/app/consumer/{userId}/**").access(SecurityConfig::isOwnCustomerPath)

                        // Authenticated customer endpoints
                        .requestMatchers("/app/consumer/**").authenticated()

                        // Default-deny: anything not explicitly allowed above is
                        // rejected rather than silently public.
                        .anyRequest().denyAll()
                )
                // Unauthenticated requests (missing/expired/invalid customer JWT)
                // must return 401 — not the servlet default 403 — so the storefront
                // interceptor can refresh the access token and replay the request.
                // A logged-in customer reaching someone else's data gets 403.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authEx) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                // Install our custom JWT filter before the standard auth filter so
                // tokens minted by JwtTokenProvider populate the SecurityContext.
                .addFilterBefore(customerJwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Granted only when the {userId} in a customer address is the logged-in
     * customer. The customer token's subject is their id (see JwtTokenProvider).
     * An anonymous caller never matches, so it is sent to the entry point (401)
     * rather than told the resource exists.
     */
    static AuthorizationDecision isOwnCustomerPath(Supplier<Authentication> authentication,
                                                   RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        String pathUserId = context.getVariables().get("userId");
        if (auth == null || pathUserId == null) {
            return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(sameId(pathUserId, auth.getName()));
    }

    /** UUIDs compare by value, so upper-case and lower-case spellings agree. */
    private static boolean sameId(String a, String b) {
        if (a == null || b == null) return false;
        try {
            return UUID.fromString(a).equals(UUID.fromString(b));
        } catch (IllegalArgumentException notUuids) {
            return a.equalsIgnoreCase(b);
        }
    }
}
