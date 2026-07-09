package com.portalcomunitario.msauth.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Aislamiento de tenant: si el token fue emitido para una comunidad (claim "schema"),
 * rechaza (403) cualquier petición cuyo X-Tenant-ID apunte a otra comunidad.
 * Tokens antiguos sin el claim se dejan pasar (el usuario re-inicia sesión).
 */
public class SchemaGuardFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authn = SecurityContextHolder.getContext().getAuthentication();
        if (authn instanceof JwtAuthenticationToken jwtAuth) {
            String tokenSchema = jwtAuth.getToken().getClaimAsString("schema");
            if (tokenSchema != null && !tokenSchema.isBlank()) {
                String reqTenant = requestTenant(request);
                if (!tokenSchema.equals(reqTenant)) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN,
                            "La sesión no pertenece a esta comunidad");
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String requestTenant(HttpServletRequest request) {
        String header = request.getHeader("X-Tenant-ID");
        if (header != null && !header.isBlank()) {
            return header;
        }
        String host = request.getServerName();
        String[] parts = host.split("\\.");
        if (parts.length >= 3) {
            return parts[0];
        }
        return "public";
    }
}
