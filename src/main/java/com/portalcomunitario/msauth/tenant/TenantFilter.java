package com.portalcomunitario.msauth.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String tenant = extractTenant(request);
        TenantContext.setCurrentTenant(tenant);

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractTenant(HttpServletRequest request) {
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
