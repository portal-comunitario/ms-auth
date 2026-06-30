package com.portalcomunitario.msauth.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.portalcomunitario.msauth.user.User;
import com.portalcomunitario.msauth.user.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Date;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final GoogleIdTokenVerifier googleVerifier;
    private final SecretKey jwtKey;
    private final long jwtExpirationMillis;

    public AuthService(UserRepository userRepository,
                       @Value("${app.google.client-id}") String googleClientId,
                       @Value("${app.jwt.secret}") String jwtSecret,
                       @Value("${app.jwt.expiration}") long jwtExpirationMillis) {
        this.userRepository = userRepository;
        this.googleVerifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
        this.jwtKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpirationMillis = jwtExpirationMillis;
    }

    public AuthResult authenticate(String googleIdToken) {
        GoogleIdToken.Payload payload = verifyGoogleToken(googleIdToken);

        String email = payload.getEmail();
        String name = (String) payload.get("name");
        if (name == null || name.isBlank()) name = email;
        final String resolvedName = name;

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User nuevo = new User();
            nuevo.setEmail(email);
            nuevo.setName(resolvedName);
            nuevo.setRole(User.Role.VECINO);
            nuevo.setTenantId(null); // asignado por el admin al unirse con código
            return userRepository.save(nuevo);
        });

        return new AuthResult(generateJwt(user), user);
    }

    private GoogleIdToken.Payload verifyGoogleToken(String idTokenString) {
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new IllegalArgumentException("El idToken es requerido");
        }
        try {
            GoogleIdToken idToken = googleVerifier.verify(idTokenString);
            if (idToken == null) {
                throw new IllegalArgumentException("El idToken de Google no es válido");
            }
            return idToken.getPayload();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException("No se pudo validar el idToken de Google", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Formato de idToken inválido", e);
        }
    }

    private String generateJwt(User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtExpirationMillis);
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("name", user.getName())
                .claim("role", user.getRole().name())
                .claim("tenantId", user.getTenantId())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(jwtKey)
                .compact();
    }

    public record AuthResult(String token, User user) {}
}
