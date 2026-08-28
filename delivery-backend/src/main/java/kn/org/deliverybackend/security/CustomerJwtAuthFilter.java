package kn.org.deliverybackend.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the Bearer token from the Authorization header, verifies it as a
 * Spring-issued customer JWT (see {@link JwtTokenProvider}), and populates
 * the SecurityContext with the customer's principal.
 *
 * If the header is missing or the token does not parse as a customer JWT,
 * this filter does nothing — the Keycloak resource-server chain will get a
 * shot at the request.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerJwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                Claims claims = tokenProvider.parse(token);
                if (JwtTokenProvider.TOKEN_TYPE_ACCESS.equals(
                        claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))) {

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    claims.getSubject(),
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ex) {
                // Not a customer JWT — let downstream filters (Keycloak) try.
                log.trace("Not a customer JWT: {}", ex.getMessage());
            }
        }

        chain.doFilter(request, response);
    }
}
